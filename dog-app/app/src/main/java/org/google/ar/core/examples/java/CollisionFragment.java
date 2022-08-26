package org.google.ar.core.examples.java;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.media.Image;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import org.R;
import org.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import org.google.ar.core.examples.java.common.helpers.DepthSettings;
import org.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import org.google.ar.core.examples.java.common.helpers.InstantPlacementSettings;
import org.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import org.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import org.google.ar.core.examples.java.common.samplerender.Framebuffer;
import org.google.ar.core.examples.java.common.samplerender.SampleRender;
import org.google.ar.core.examples.java.common.samplerender.arcore.BackgroundRenderer;
import org.projectdog.MainActivity;
import org.projectdog.QueueableResult;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class CollisionFragment extends Fragment implements SampleRender.Renderer {

    private static final String TAG = CollisionFragment.class.getSimpleName();

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100f;

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;

    private boolean installRequested;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private TrackingStateHelper trackingStateHelper;
    private SampleRender render;

    private BackgroundRenderer backgroundRenderer;
    private Framebuffer virtualSceneFramebuffer;
    private boolean hasSetTextureNames = false;

    private final DepthSettings depthSettings = new DepthSettings();

    private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();

    private TextView distanceText;
    public int distance;


    private Button swapToMap;


    // timers used to limit the frequency of toasts
    private boolean booting = true;
    private long startTime = 0;
    private long currentTime = 0;


    private Context activityContext;

    public boolean allowedToEnqueue = true;


    public CollisionFragment() {
        // Required empty public constructor
    }



    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        activityContext = context;

        trackingStateHelper = new TrackingStateHelper((Activity)context);
        //displayRotationHelper = new DisplayRotationHelper(context);
        depthSettings.onCreate(context);
        instantPlacementSettings.onCreate(context);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_collision, container, false);

    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        surfaceView = view.findViewById(R.id.surfaceview);
        distanceText = view.findViewById(R.id.distance_text);
        displayRotationHelper = new DisplayRotationHelper(activityContext);

        startTime = System.currentTimeMillis();

        // Set up renderer.
        render = new SampleRender(surfaceView, this, getActivity().getAssets());

        installRequested = false;

        depthSettings.setDepthColorVisualizationEnabled(true);

    }

    @Override
    public void onDestroy() {
        if (session != null) {
            // Explicitly close ARCore Session to release native resources.
            // Review the API reference for important considerations before calling close() in apps with
            // more complicated lifecycle requirements:
            // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
            session.close();
            session = null;
        }

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall((Activity) activityContext, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission((Activity)activityContext)) {
                    CameraPermissionHelper.requestCameraPermission((Activity)activityContext);
                    return;
                }

                // Create the session.
                session = new Session(/* context= */ activityContext);
            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            }
            catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError((Activity)activityContext, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            configureSession();
            // To record a live camera session for later playback, call
            // `session.startRecording(recorderConfig)` at anytime. To playback a previously recorded AR
            // session instead of using the live camera feed, call
            // `session.setPlaybackDataset(playbackDatasetPath)` before calling `session.resume()`. To
            // learn more about recording and playback, see:
            // https://developers.google.com/ar/develop/java/recording-and-playback
            session.resume();
        } catch (CameraNotAvailableException e) {
            session = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission((Activity)activityContext)) {
            // Use toast instead of snackbar here since the activity will exit.
            Toast.makeText((Activity)activityContext, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale((Activity)activityContext)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings((Activity)activityContext);
            }
            getActivity().finish();
        }
    }


    /**
     * Configures the session with feature settings.
     */
    private void configureSession() {
        Config config = session.getConfig();

        //DEBUG
        String cameraID = session.getCameraConfig().getCameraId();
        Log.i("CAMERA_ID", cameraID);

        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }
        session.configure(config);
    }


    @Override
    public void onSurfaceCreated(SampleRender render) {
        // Prepare the rendering objects. This involves reading shaders and 3D model files, so may throw
        // an IOException.
        backgroundRenderer = new BackgroundRenderer(render);
        virtualSceneFramebuffer = new Framebuffer(render, /*width=*/ 1, /*height=*/ 1);
    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame(SampleRender render) {
        currentTime = System.currentTimeMillis();

        if (session == null) {
            return;
        }

        // Texture names should only be set once on a GL thread unless they change. This is done during
        // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
        // initialized during the execution of onSurfaceCreated.
        if (!hasSetTextureNames) {
            session.setCameraTextureNames(
                    new int[]{backgroundRenderer.getCameraColorTexture().getTextureId()});
            hasSetTextureNames = true;
        }

        // -- Update per-frame state

        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        // Obtain the current frame from ARSession. When the configuration is set to
        // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
        // camera framerate.
        Frame frame;
        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera not available during onDrawFrame", e);
            return;
        }
        Camera camera = frame.getCamera();

        // Update BackgroundRenderer state to match the depth settings.
        try {
            backgroundRenderer.setUseDepthVisualization(
                    render, depthSettings.depthColorVisualizationEnabled());
            backgroundRenderer.setUseOcclusion(render, depthSettings.useDepthForOcclusion());
        } catch (IOException e) {
            Log.e(TAG, "Failed to read a required asset file", e);
            return;
        }
        // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
        // used to draw the background camera image.
        backgroundRenderer.updateDisplayGeometry(frame);

        if (camera.getTrackingState() == TrackingState.TRACKING
                && (depthSettings.useDepthForOcclusion()
                || depthSettings.depthColorVisualizationEnabled())) {
            try (Image depthImage = frame.acquireDepthImage()) {
                backgroundRenderer.updateCameraDepthTexture(depthImage);

                // Extract distance from depth map
                int x_center = depthImage.getWidth() / 2;
                int y_center = depthImage.getHeight() / 2;
                distance = getMillimetersDepth(depthImage, x_center, y_center);
                // convert mm to feet
                double feet = distance / 304.8;
                String feetText = String.format("%.2f feet", feet);


                //update UI
                if (distance <= 6096){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            distanceText.setText(feetText);
                        }
                    });
                }
                else{
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            distanceText.setText("");
                        }
                    });

                }

                int collisionThreshold = 1320;  //4.3 feet
                int bootTime = 3000;            //3 seconds

                // wait 5 seconds to boot
                if (booting){
                    // distance threshold is 4 feet
                    if (currentTime - startTime > bootTime && distance < collisionThreshold && distance > 50 ){
                        startTime = System.currentTimeMillis();
                        booting = false;
                        if(allowedToEnqueue) {
                            ((MainActivity)getActivity()).resultQueue.add(
                                    new QueueableResult("Collision alert", 0, "Watch out! Ahbject ahead")
                            );
                        }
                    }
                }
                else{
                    if (currentTime - startTime > 3000 && distance < collisionThreshold && distance > 50){
                        startTime = System.currentTimeMillis();

                        if(allowedToEnqueue) {
                            ((MainActivity)getActivity()).resultQueue.add(
                                    new QueueableResult("Collision alert", 0, "Watch out! Ahbject ahead")
                            );
                        }
                    }
                }





            } catch (NotYetAvailableException e) {
                // This normally means that depth data is not available yet. This is normal so we will not
                // spam the logcat with this.
            }
        }

        // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());


        // -- Draw background
        if (frame.getTimestamp() != 0) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            backgroundRenderer.drawBackground(render);
        }

        // If not tracking, don't draw 3D objects.
        if (camera.getTrackingState() == TrackingState.PAUSED) {
            return;
        }

        // Compose the virtual scene with the background.
        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
    }

    public int getMillimetersDepth(Image depthImage, int x, int y) {
        // The depth image has a single plane, which stores depth for each
        // pixel as 16-bit unsigned integers.
        Image.Plane plane = depthImage.getPlanes()[0];
        int byteIndex = x * plane.getPixelStride() + y * plane.getRowStride();
        ByteBuffer buffer = plane.getBuffer().order(ByteOrder.nativeOrder());
        short depthSample = buffer.getShort(byteIndex);
        return depthSample;
    }
}