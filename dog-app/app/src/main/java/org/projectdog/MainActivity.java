package org.projectdog;


import android.app.Fragment;
import android.content.Context;

import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.gigamole.infinitecycleviewpager.HorizontalInfiniteCycleViewPager;

import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.examples.detection.env.Logger;

import org.R;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import org.R;

import org.google.ar.core.examples.java.CollisionFragment;
import org.tensorflow.lite.examples.detection.DetectionFragment;

import org.google.ar.core.examples.java.CollisionFragment;
import org.tensorflow.lite.examples.detection.DetectionFragment;
import org.tensorflow.lite.examples.detection.env.Logger;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Toast;


import static java.time.temporal.ChronoUnit.SECONDS;

public class MainActivity extends AppCompatActivity {

    // How long should an object be suppressed from enqueuing? (seconds)
    private static short COOLDOWN = 10;

    // List for carousel items
    List<Button> lstImages = new ArrayList<>();

    // Priority queue for text-to-speech
    public PriorityQueue<QueueableResult> resultQueue = new PriorityQueue<QueueableResult>();

    // Service to constantly dequeue
    private ScheduledExecutorService dequeueTask;
    private static final int REQUEST_CODE_SPEECH_INPUT = 1000;

    // Hashmap to track the wait times for items to be allowed to enter the queue
    public Map<String, LocalTime> onCooldown = new HashMap();

    // Hashmap for sign labels to human-understandable labels
    public Map<String, String> signLabelsComprehender;
    private Set<String> comprehensibleSigns;

    // Hashmap (more of a set) for the general object detection labels
    private Map<String, String> genObjLabels;
    LayoutInflater layoutInflater;
    Context context;
    public TextToSpeech textToSpeech;
    private String welcome_message = "Welcome back! Say 'Help' to hear all voice commands again";
    private String tutorial = "Welcome to the Digital Orientation Guide. This app has four screens: home; camera; collision; and help.\n"+
            "Change the screen by scrolling through the button wheel at the bottom of the screen, or by using a voice command.\n" +
            "To enter a voice command, press the red voice command button on the button wheel.\n " +
            "On the camera screen, the app detects awbjects seen by your phone camera and speaks them aloud. Please face your back phone camera outward while using this mode.\n" +
            "Say: “What is in front of me”, while using this mode to hear all the awbjects in your environment. Say, “Is there a car”, or any other awbject to check for a specific awbject. \n" +
            "The collision screen prevents you from colliding with awbjects by sounding an alert if you are too close to an obstruction. \n" +
            "Please face your back phone camera outward while using this mode. This mode may not work well in low-light environments. \n" +
            "The Help screen lists all voice commands that are available in the application. Say, “Help” to hear all voice commands. \n" +
            "To replay this tutorial at any time, say, “replay tutorial”. Thank you.";
    private String help = "The voice commands are: Go to, Camera, Collision, or Home. Is there. awbject. in front of me. What is in front of me. Replay tutorial. Exit app. Say help to hear this list again";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        HomeFragment homeFragment = new HomeFragment();
        getFragmentManager().beginTransaction().replace(R.id.fragment_container, homeFragment).addToBackStack(null).commit();

        initData();
        HorizontalInfiniteCycleViewPager pager = (HorizontalInfiniteCycleViewPager)findViewById(R.id.horizontal_cycle);
        MyAdapter adapter = new MyAdapter(lstImages,this);
        pager.setAdapter(adapter);

        dequeueTask = Executors.newScheduledThreadPool(5);
        dequeueTask.scheduleAtFixedRate(new Runnable() {
            public void run() {
                // Dequeue latest result
                QueueableResult latestResult = resultQueue.poll();
                if(latestResult != null) {
                    textToSpeech(latestResult.cueText);
                }

                // Check all cooldowns
                LocalTime currTime = LocalTime.now();
                List<String> toRemove = new LinkedList<>();
                for (Map.Entry<String, LocalTime> item : onCooldown.entrySet()) {
                    LocalTime itemTime = item.getValue();
                    long secondsBetween = SECONDS.between(itemTime, currTime);

                    // Remove item if it has been suppressed long enough
                    if(secondsBetween >= COOLDOWN) {
                        toRemove.add(item.getKey());
                    }
                }
                for (String item : toRemove) {
                    onCooldown.remove(item);
                }
            }
        }, 0, 1, TimeUnit.SECONDS);

        // Read JSON files into Hashmaps
        try {
            // Sign labels
            InputStream fileInput = getApplicationContext().getAssets().open("label_translate.json");
            byte[] buffer = new byte[fileInput.available()];
            fileInput.read(buffer);
            fileInput.close();
            String rawInput = new String(buffer, "UTF-8");
            signLabelsComprehender = parseJson(new JSONObject(rawInput));
            comprehensibleSigns = new HashSet<String>(signLabelsComprehender.keySet());

            // General object labels
            fileInput = getApplicationContext().getAssets().open("general_labels.json");
            buffer = new byte[fileInput.available()];
            fileInput.read(buffer);
            fileInput.close();
            rawInput = new String(buffer, "UTF-8");
            genObjLabels = parseJson(new JSONObject(rawInput));
        } catch (IOException | JSONException e) {
            Logger LOGGER = new Logger();
            LOGGER.w("Error parsing JSON. Hashmap uninitialized");
            LOGGER.e(e.toString());
            signLabelsComprehender = null;
            genObjLabels = null;
        }

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        boolean firstStart = prefs.getBoolean("firstStart", true);

        if (firstStart) {
            textToSpeech(tutorial);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("firstStart", false);
            editor.apply();
        }
        else{
            textToSpeech(welcome_message);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        textToSpeech.stop();

    }

    private Map<String, String> parseJson(JSONObject obj) throws JSONException {
        /**
         * Helper function for converting a JSON object to a Hashmap (Python style)
         */
        Map<String, String> map = new HashMap<String, String>();
        Iterator<String> keysItr = obj.keys();
        while(keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = obj.get(key);
            if(value instanceof JSONObject) {
                value = parseJson((JSONObject) value);
            }
            map.put(key.toLowerCase(), ((String) value).toLowerCase());
        }
        return map;
    }

    public void doVoiceCommand(String voiceInput) {
        /**
         * Hub function for all the voice commands
         */
        Logger l = new Logger();
        String cleanInput = voiceInput.toLowerCase();
        cleanInput = cleanInput.trim();
        l.i("Got string: " + cleanInput);

        Fragment activeFragment = getFragmentManager().findFragmentById(R.id.fragment_container);
        if(activeFragment instanceof DetectionFragment) {
            ((DetectionFragment)activeFragment).allowedToEnqueue = true;
        } else if (activeFragment instanceof CollisionFragment) {
            ((CollisionFragment)activeFragment).allowedToEnqueue = true;
        }

        if(cleanInput.contains("go to camera") || cleanInput.equals("camera")) {
            DetectionFragment detectionFragment = new DetectionFragment();
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, detectionFragment).addToBackStack(null).commit();
            textToSpeech("opening camera screen");
            l.i("Switch to camera");
        } else if(cleanInput.contains("go to collision") || cleanInput.equals("collision")) {
            CollisionFragment collisionFragment = new CollisionFragment();
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, collisionFragment).addToBackStack(null).commit();
            textToSpeech("opening collision screen");
            l.i("Switch to collision");
        } else if(cleanInput.contains("go to home") || cleanInput.equals("home")) {
            HomeFragment homeFragment = new HomeFragment();
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, homeFragment).addToBackStack(null).commit();
            textToSpeech("opening home screen");
            l.i("Switch to home");
        } else if(cleanInput.contains("help")) {
            help();
            l.i("Launch help");
        } else if(cleanInput.contains("tutorial")) {
            tutorial();
            l.i("Launch tutorial");
        } else if(cleanInput.contains("exit app")) {
            quitApp();
            l.i("Exit app");
        } else if(cleanInput.contains("what is in front of me") || cleanInput.equals("scan")) {
            textToSpeech("opening camera");
            DetectionFragment detectionFragment = new DetectionFragment();
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, detectionFragment).addToBackStack(null).commit();
            resultQueue.clear();
            for(String item : detectionFragment.scanContainer) {
                textToSpeech(item);
            }
            l.i("Read all queue items");
        } else if(cleanInput.contains("is there") && cleanInput.contains("in front of me")) {
            int objStrEndsAt = cleanInput.lastIndexOf("i");
            String objToFind = cleanInput.substring(9, objStrEndsAt - 1);
            if(objToFind.charAt(0) == 'a') {
                // splice out an indefinite article if it's there
                objToFind = cleanInput.substring(11, objStrEndsAt - 1);
            }
            l.i("Parsed object: " + objToFind);

            boolean invalidObject = true;
            if(comprehensibleSigns.contains(objToFind) || genObjLabels.containsKey(objToFind)) {
                invalidObject = false;
            }

            if(!invalidObject && onCooldown.containsKey(objToFind)) {
                textToSpeech(objToFind + " ahead");
                l.i(objToFind + " ahead");
            } else if (!invalidObject && !onCooldown.containsKey(objToFind)) {
                textToSpeech("No " + objToFind + " found");
                l.i("No " + objToFind + " found");
            } else {
                textToSpeech("Awbject not in database.");
                l.i("Unrecognized object " + objToFind);
            }
        } else {
            textToSpeech("Unrecognized command. Please try again");
            l.i("Unrecognized command");
        }
    }

    public void textToSpeech(String text){

        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int ttsLang = textToSpeech.setLanguage(Locale.US);

                    if (ttsLang == TextToSpeech.LANG_MISSING_DATA
                            || ttsLang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "The Language is not supported!");
                    } else {
                        Log.i("TTS", "Language Supported.");
                    }
                    Log.i("TTS", "Initialization success.");


                    int speechStatus = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "");

                    if (speechStatus == TextToSpeech.ERROR) {
                        Log.e("TTS", "Error in converting Text to Speech!");
                    }

                } else {
                    Toast.makeText(getApplicationContext(), "TTS Initialization failed!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void initData(){
        lstImages.add(new Button("Voice Commands", R.drawable.voice_commands2));
        lstImages.add(new Button("Home", R.drawable.home2));
        lstImages.add(new Button("Camera View", R.drawable.camera2));
        lstImages.add(new Button("Help",R.drawable.help2));
        lstImages.add(new Button("Collision Screen", R.drawable.collision2));
    }

    private void help(){
        SettingsFragment settingsFragment = new SettingsFragment();
        getFragmentManager().beginTransaction().replace(R.id.fragment_container, settingsFragment).addToBackStack(null).commit();
        textToSpeech(help);
    }

    private void tutorial(){
        textToSpeech(tutorial);
    }

    private void quitApp(){
        System.exit(0);
    }

    public void askSpeechInput() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak something");

        try{
            startActivityForResult(i, REQUEST_CODE_SPEECH_INPUT);
        }catch (Exception e){
            Toast.makeText(this, ""+e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode){
            case REQUEST_CODE_SPEECH_INPUT:{
                if(resultCode == RESULT_OK && null !=data){
                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    String commandResult = result.get(0);
                    doVoiceCommand(commandResult);
                }
                break;
            }
        }
    }
}
