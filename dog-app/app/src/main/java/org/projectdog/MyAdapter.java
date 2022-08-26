package org.projectdog;

import android.app.Fragment;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import org.tensorflow.lite.examples.detection.DetectionFragment;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import org.R;
import org.google.ar.core.examples.java.CollisionFragment;
import org.tensorflow.lite.examples.detection.env.Logger;
import java.util.List;


public class MyAdapter extends PagerAdapter {
    List<Button> lstImages;
    Context context;
    LayoutInflater layoutInflater;

     public MyAdapter(List<Button> lstImages, Context context){
         this.lstImages =lstImages;
         this.context = context;
         layoutInflater = LayoutInflater.from(context);

     }

     @Override
    public int getCount(){

         return lstImages.size();
     }

     @Override
    public boolean isViewFromObject(View view, Object object){
         return view.equals(object);
     }

     @Override
    public void destroyItem(ViewGroup container, int position, Object object){
         container.removeView((View)object);
     }
    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position){
         View view = layoutInflater.from(context).inflate(R.layout.card_item,container,false);

         ImageView imageView = ((ImageView)view.findViewById(R.id.imageView));

         //set data
         imageView.setImageResource(lstImages.get(position).getImage());

         view.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                 boolean clearObjects = true;

                 if(lstImages.get(position).getName().equals("Camera View")){

                     ((MainActivity)context).textToSpeech("Camera View");
                     AppCompatActivity activity= (AppCompatActivity) context;
                    DetectionFragment detectionFragment = new DetectionFragment();
                    activity.getFragmentManager().beginTransaction().replace(R.id.fragment_container, detectionFragment).addToBackStack(null).commit();

                 }
                 else if(lstImages.get(position).getName().equals("Voice Commands")){

                     clearObjects = false;


                     // Stop current TTS
                     ((MainActivity) context).textToSpeech.stop();

                     // Stop Collision and Detection from enqueuing
                     AppCompatActivity activity= (AppCompatActivity) context;
                     Fragment activeFragment = activity.getFragmentManager().findFragmentById(R.id.fragment_container);
                     if(activeFragment instanceof DetectionFragment) {
                         ((DetectionFragment)activeFragment).allowedToEnqueue = false;
                     } else if (activeFragment instanceof CollisionFragment) {
                         ((CollisionFragment)activeFragment).allowedToEnqueue = false;
                     }

                     MainActivity a = (MainActivity) context;
                     a.askSpeechInput();

                 }
                 else if(lstImages.get(position).getName().equals("Help")){
                     ((MainActivity)context).textToSpeech("Help");
                     AppCompatActivity activity= (AppCompatActivity) context;
                     SettingsFragment settingsFragment = new SettingsFragment();
                     activity.getFragmentManager().beginTransaction().replace(R.id.fragment_container, settingsFragment).addToBackStack(null).commit();

                 }
                 else if(lstImages.get(position).getName().equals("Collision Screen")){
                     ((MainActivity)context).textToSpeech("Collision Screen");
                     AppCompatActivity activity= (AppCompatActivity) context;
                     CollisionFragment collisionFragment = new CollisionFragment();
                     activity.getFragmentManager().beginTransaction().replace(R.id.fragment_container, collisionFragment).addToBackStack(null).commit();
                 }
                 else if(lstImages.get(position).getName().equals("Home")){
                     ((MainActivity)context).textToSpeech("Home");
                     AppCompatActivity activity= (AppCompatActivity) context;
                     HomeFragment homeFragment = new HomeFragment();
                     activity.getFragmentManager().beginTransaction().replace(R.id.fragment_container, homeFragment).addToBackStack(null).commit();

                 }
                 else {
                     Toast.makeText(context, lstImages.get(position).getName(), Toast.LENGTH_SHORT).show();
                 }

                 if(clearObjects) {
                     // Obtain MainActivity from current context and call clear on queue
                     ((MainActivity)context).resultQueue.clear();
                     ((MainActivity)context).onCooldown.clear();
                 }

             }
         });

         container.addView(view);
         return view;
     }
}
