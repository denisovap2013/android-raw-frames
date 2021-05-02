package com.rawframesrecorder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaActionSound;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;


@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {
    private Camera mCamera;
    final private MediaActionSound sound = new MediaActionSound();

    // Parameters of the camera
    // (I'm not sure how the camera works, but I store the camera settings of
    // the current app session in case the camera needs reinitializing.)
    private Camera.Parameters cameraParameters;

    // Number of consecutive frames to  be recorded in one go.
    private int framesNumber = 30;
    @SuppressWarnings("FieldCanBeLocal")
    final private int MAX_FRAMES_NUMBER = 150;

    // Flag indicating if frames are currently recorded.
    private boolean recording = false;

    // Flag indicating if preview is active.
    private boolean previewActive = false;

    // Frames directory located in the app-related external storage.
    File framesSaveDir;
    private boolean storageAvailable;

    // Formatter for naming the directories for each record.
    @SuppressLint("SimpleDateFormat")
    final private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    // Gui elements
    private SurfaceView videoSurface;
    private ViewFlipper viewFlipper;
    private Spinner previewSizeSelector;
    private Spinner fpsRangeSelector;
    private Spinner formatSelector;
    private Spinner focusModeSelector;

    private TextView zoomIndicator;
    private SeekBar zoomController;

    private ProgressBar recordingProgressBar;

    private Toast lastToast = null;
    private String lastToastText = null;

    // This is truly magnificent and embarrassing.
    // (Layout designer gives me only Switch controller,
    // but I "should" use SwitchCompat for my API 23 +. Ha-ha.)
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch recordHintSwitch;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch videoStabilizationSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start your day with requesting permissions.
        if (!CheckAndRequestPermissions()) {
            Inform("Congratulations for your resolve. Without " +
                    "the necessary permissions this app is practically " +
                    "useless. Add necessary permissions!");
        }

        // Create the storage directory
        framesSaveDir = new File(getFilesDir(), "raw-frames");
        storageAvailable = maybeCreateDir(framesSaveDir);

        if (!storageAvailable)
            Inform("Storage is not available. Try to restart the app and add corresponding permissions.");

        // Bind with GUI elements
        initializeInterface();

        // Get camera instance
        mCamera = getCameraInstance();

        // Perform the initial camera setup
        // (If your app crushes try to change it or just comment.
        // You still will be able to change the camera settings in the app.)
        SetupCamera();

        // Save current camera parameters into the object field.
        cameraParameters = requestCameraParameters();
    }

    private void initializeInterface() {
        // I wish I could do this when I create the corresponding object fields,
        // but the app crashes that way.
        // (I don;t know why, so there you have this masterpiece.)
        videoSurface = findViewById(R.id.videoView);
        viewFlipper = findViewById(R.id.viewFlipperId);

        previewSizeSelector = findViewById(R.id.previewSizeSelectorId);
        fpsRangeSelector = findViewById(R.id.fpsRangeSelectorId);
        formatSelector = findViewById(R.id.formatSelectorId);
        focusModeSelector = findViewById(R.id.focusModeSelectorId);


        recordHintSwitch = findViewById(R.id.recordHintSwitchId);
        videoStabilizationSwitch = findViewById(R.id.videoStabilizationSwitchId);

        zoomController = findViewById(R.id.zoomControllerId);
        zoomIndicator = findViewById(R.id.zoomIndicatorId);

        // Setup callbacks for seek bars
        zoomController.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (cameraParameters == null) return;

                // Not really optimal to parse a string each time, but whatever.
                int zoomRatio = cameraParameters.getZoomRatios().get(progress);

                zoomIndicator.setText(String.valueOf(zoomRatio / 100f));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Set the video recording progress bar
        recordingProgressBar = findViewById(R.id.recordingProgressBarId);
        recordingProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // Number of frames to record
        SeekBar framesNumberControl = findViewById(R.id.framesNumberControlId);
        TextView framesNumberIndicator = findViewById(R.id.framesNumberIndicatorId);

        framesNumberControl.setMax(MAX_FRAMES_NUMBER - 1);

        framesNumberControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                framesNumber = progress + 1;
                framesNumberIndicator.setText(String.valueOf(framesNumber));
                recordingProgressBar.setMax(framesNumber);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Inform("Updated the number of frames to record.");
            }
        });

        framesNumberControl.setProgress(framesNumber - 1);
    }

    // Permissions

    private boolean isPermissionGranted(String permissionName) {
        return checkSelfPermission(permissionName) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean requestSinglePermission(String permissionName) {
        if (isPermissionGranted(permissionName)) return true;
        requestPermissions(new String[]{permissionName}, 1);
        return isPermissionGranted(permissionName);
    }

    private boolean CheckAndRequestPermissions() {
        return (requestSinglePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                && requestSinglePermission(Manifest.permission.CAMERA));
    }

    // Camera related methods

    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(0); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    public void UpdateFocus(View view) {
        if (mCamera != null)
            try {
                mCamera.autoFocus((success, camera) -> {
                    if (success) {
                        sound.play(MediaActionSound.FOCUS_COMPLETE);
                    } else {
                        Inform("Unable to focus.");
                    }
                });
            } catch (Exception e) {
                // Probably preview is turned off, so just ignoring that
                Inform("Unable to focus. Preview is turned off or an error occurred.");
            }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mCamera != null) return;  // Camera instance is acquired from the onCreate method.

        mCamera = getCameraInstance();

        if (mCamera == null) {
            Inform("Unable to restore the Camera");
            return;
        }

        // Restore camera parameters, in case they are reset.
        if (cameraParameters != null) writeCameraParameters(cameraParameters);
    }

    private Camera.Parameters requestCameraParameters() {
        if (mCamera == null) return null;

        Camera.Parameters parameters = null;

        try {
            parameters = mCamera.getParameters();
        } catch (Exception e) {
            e.printStackTrace();
            Inform("Unable to get camera parameters");
        }

        return parameters;
    }

    private boolean writeCameraParameters(Camera.Parameters parameters) {
        if (mCamera == null) return false;

        try {
            mCamera.setParameters(parameters);
        } catch (Exception e) {
            e.printStackTrace();
            Inform("Unable to set camera parameters");
            return false;
        }

        return true;
    }

    private void SetupCamera() {
        Camera.Parameters cameraParameters = requestCameraParameters();
        if (cameraParameters == null) return;

        List<int[]> supportedFpsRanges = cameraParameters.getSupportedPreviewFpsRange();
        int[] fpsRange = supportedFpsRanges.get(supportedFpsRanges.size() - 1);

        cameraParameters.setPreviewFpsRange(fpsRange[0], fpsRange[1]);
        if (cameraParameters.isVideoStabilizationSupported())
            cameraParameters.setVideoStabilization(true);
        cameraParameters.setRecordingHint(true);

        writeCameraParameters(cameraParameters);
    }

    private void stopPreview() {
        if (mCamera == null) return;
        try {mCamera.stopPreview();} catch (Exception e) {/*Not so important.*/}
        previewActive = false;
    }

    private boolean startPreview() {
        try {
            stopPreview();
            mCamera.setPreviewDisplay(videoSurface.getHolder());
            mCamera.startPreview();
            previewActive = true;
        } catch (Exception e) {
            e.printStackTrace();
            previewActive = false;
            return false;
        }
        return true;
    }

    public void StartPreview(View view) {
        if (mCamera == null) {
            Inform("Camera pointer is null, unable to start a preview. Try to restart the app and update its permissions.");
            return;
        }
        if (!startPreview()) {
            Inform("Unable to start a preview.");
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            stopRecording();
            stopPreview();
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    private void stopRecording() {
        recording = false;
        recordingProgressBar.setVisibility(ProgressBar.INVISIBLE);
        mCamera.setPreviewCallback(null); // Remove callback after some number of frames
    }

    public void StartRecordingFrames(View view) {
        // Check that we can actually start recording.
        if (!previewActive) {
            Inform("Preview is not active. Try to click 'Turn on' button.");
            return;
        }

        if (recording) return;  // We do not start another recording session, while there is already active one.

        if (!storageAvailable) {
            Inform("Unable to start recording. Storage is not available. Try restarting the app and granting corresponding permissions.");
            return;
        }

        if (framesNumber < 1) return;  // Why anyone would want to try that?

        recording = true;

        // Create a directory name based on the calendar date and time
        File calendarBasedSaveDir = new File(
                framesSaveDir,
                dateFormat.format(Calendar.getInstance().getTime())
        );

        if (!maybeCreateDir(calendarBasedSaveDir)) {
            Inform("Unable to create the save directory: \n" + calendarBasedSaveDir);
            return;
        }

        if (!writeSettingsLogFile(calendarBasedSaveDir, cameraParameters)) {
            Inform("Unable to write setting log to \n" + calendarBasedSaveDir);
            return;
        }

        // Start the video recording
        // (If something goes wrong, we stop recording.)
        sound.play(MediaActionSound.START_VIDEO_RECORDING);

        // Prepare the progress bar.
        recordingProgressBar.setProgress(0);
        recordingProgressBar.setVisibility(ProgressBar.VISIBLE);

        mCamera.setPreviewCallback(new Camera.PreviewCallback() {
            // Have not studied well how closures work in Java, so there you have it.
            // I just copy values to the object fields.
            int counter = 0;
            final int MAX_FRAMES = framesNumber;
            final File saveDir = calendarBasedSaveDir;

            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                if (!WriteFrame(saveDir, data, ".yuv")) {
                    // If we cannot write the frame data
                    // (can happen because of numerous reasons)
                    // we abort the recording
                    Inform("Unable to write frame data to " + saveDir);
                    stopRecording();
                    return;
                }
                if (++counter >= MAX_FRAMES) {
                    stopRecording();
                    Inform("Finished recording the frames.");
                    sound.play(MediaActionSound.STOP_VIDEO_RECORDING);
                }

                recordingProgressBar.setProgress(counter);
            }
        });
    }

    // Settings
    public void OpenSettings(View view) {
        if (recording) {
            Inform("Access to settings denied. Currently recording.");
            return;  // No settings for you, while recording (just in case)
        }

        Camera.Parameters cameraParameters = requestCameraParameters();

        if (cameraParameters == null) {
            Inform("Camera parameters are not available");
            return;
        }

        // INITIALIZE FIELDS

        // Set the zoom controller (indicator updates automatically)
        zoomController.setMax(cameraParameters.getMaxZoom());
        zoomController.setProgress(cameraParameters.getZoom());

        // Video stabilization option is not always supported, so I just disable the
        // switch controller so the user can see it.
        if (cameraParameters.isVideoStabilizationSupported()) {
            videoStabilizationSwitch.setChecked(cameraParameters.getVideoStabilization());
        } else {
            videoStabilizationSwitch.setChecked(false);
            videoStabilizationSwitch.setEnabled(false);
        }

        // That option is not really special, but someone
        // decided not to put the corresponding "get" method (so I do it manually).
        // (It affects the field of view of the camera, because of the "stabilization"
        // feature which removes bad edges of frames.)
        recordHintSwitch.setChecked(cameraParameters.get("recording-hint").equals("true"));

        // Setup spinners
        // (A lot of code for parsing the camera parameters)
        prepareSettingsForFpsRange(fpsRangeSelector, cameraParameters);
        prepareSettingsForPreviewFormat(formatSelector, cameraParameters);
        prepareSettingsForPreviewSize(previewSizeSelector, cameraParameters);
        prepareSettingsForFocusMode(focusModeSelector, cameraParameters);

        // Go to the settings page.
        viewFlipper.showNext();
    }

    private void prepareSettingsForFpsRange(Spinner selector, Camera.Parameters parameters) {
        int[] currentFpsRange = {0, 0};
        int currentFpsRangeIndex = -1;

        parameters.getPreviewFpsRange(currentFpsRange);
        List<int[]> supportedFpsRangeValues = parameters.getSupportedPreviewFpsRange();

        for (int i = 0; i< supportedFpsRangeValues.size(); i++) {
            if (Arrays.equals(supportedFpsRangeValues.get(i), currentFpsRange)) {
                currentFpsRangeIndex = i;
                break;
            }
        }

        if (currentFpsRangeIndex == -1) {
            Inform("Unable to match the supported fps range values with the current range.");
            currentFpsRangeIndex = 0;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        for (int[] x: supportedFpsRangeValues) {
            adapter.add(x[0] + ".." + x[1]);
        }

        selector.setAdapter(adapter);
        selector.setSelection(currentFpsRangeIndex);
    }


    private void prepareSettingsForPreviewFormat(Spinner selector, Camera.Parameters parameters) {
        List<String> supportedFormats = Arrays.asList(parameters.get("preview-format-values").split(","));
        String currentFormat = parameters.get("preview-format");
        int currentFormatIndex = supportedFormats.indexOf(currentFormat);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, supportedFormats);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        selector.setAdapter(adapter);
        selector.setSelection(currentFormatIndex);
    }

    private void prepareSettingsForFocusMode(Spinner selector, Camera.Parameters parameters) {
        List<String> supportedFocusModes = parameters.getSupportedFocusModes();
        String currentMode = parameters.getFocusMode();
        int currentModeIndex = supportedFocusModes.indexOf(currentMode);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, supportedFocusModes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        selector.setAdapter(adapter);
        selector.setSelection(currentModeIndex);
    }

    private void prepareSettingsForPreviewSize(Spinner selector, Camera.Parameters parameters) {
        List<Camera.Size> supportedSizes = parameters.getSupportedPreviewSizes();
        Camera.Size currentSize = parameters.getPreviewSize();
        int currrentSizeIndex = -1;

        for (int i = 0; i< supportedSizes.size(); i++) {
            if (supportedSizes.get(i).equals(currentSize)) {
                currrentSizeIndex = i;
                break;
            }
        }

        if (currrentSizeIndex == -1) {
            Inform("Unable to match the supported preview size values with the current preview size.");
            currrentSizeIndex = 0;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        for (Camera.Size size: supportedSizes) {
            adapter.add(size.width + " x " + size.height);
        }

        selector.setAdapter(adapter);
        selector.setSelection(currrentSizeIndex);
    }

    public void ApplySettings(View view) {
        Camera.Parameters parameters = requestCameraParameters();
        if (parameters == null) {
            Inform("Unable to request camera parameters. Ignore changes");
            // Go back to the main page.
            viewFlipper.showNext();
            return;
        }

        // APPLYING CHANGES

        // Zoom
        parameters.setZoom(zoomController.getProgress());

        // Preview size
        Camera.Size prevSize = cameraParameters.getPreviewSize();
        Camera.Size newSize = parameters.getSupportedPreviewSizes().get((int)previewSizeSelector.getSelectedItemId());
        parameters.setPreviewSize(newSize.width, newSize.height);

        // Fps range
        int[] newFpsRange = parameters.getSupportedPreviewFpsRange().get((int)fpsRangeSelector.getSelectedItemId());
        parameters.setPreviewFpsRange(newFpsRange[0], newFpsRange[1]);

        // Focus mode
        parameters.setFocusMode((String)focusModeSelector.getSelectedItem());

        // Recording hint
        parameters.setRecordingHint(recordHintSwitch.isChecked());

        // Video stabilization
        if (parameters.isVideoStabilizationSupported())
            parameters.setVideoStabilization(videoStabilizationSwitch.isChecked());

        // Preview format
        String prevFormat = cameraParameters.get("preview-format");
        String newFormat = (String)formatSelector.getSelectedItem();
        parameters.set("preview-format", newFormat);

        // Some parameters require restarting the preview
        boolean needToRestartPreview = previewActive && (!prevSize.equals(newSize) || !prevFormat.equals(newFormat));

        if (needToRestartPreview) {
            stopPreview();
        }

        // Writing parameters to camera.
        if (writeCameraParameters(parameters)) {
            Inform("Camera parameters successfully updated.");
            cameraParameters = parameters;

            if (needToRestartPreview) {
                if (startPreview()) {
                    Inform("Restarted the preview.", true);
                } else {
                    Inform("Unable to restore the preview.", true);
                }
            }

        } else {
            Inform("Unable to update camera parameters.");
        }

        // Go back to the main page.
        viewFlipper.showNext();
    }

    public void CancelSettings(View view) {
        // Go back to the main page.
        viewFlipper.showPrevious();
    }

    // Writing methods
    private boolean maybeCreateDir(File dir) {
        try {
            if (dir.exists()) return true;

            return dir.mkdirs();
        } catch (SecurityException e) {
            // Something went wrong. Check permissions and
            // destination directory you are trying to create.
            e.printStackTrace();
            return false;
        }
    }

    private File getOutputFile(File directory, String extension) {
        // Creating a unique file path, based on the timestamp in milliseconds.
        String timestamp = Long.toString(System.currentTimeMillis());
        return new File(directory, timestamp + "." + extension);
    }

    private boolean writeSettingsLogFile(File directory, Camera.Parameters parameters) {
        long startTime = System.currentTimeMillis();
        File outputFile = new File(directory, "camera_settings.txt");

        // It is always great to have some additional info
        // about what settings you used for each recording session.
        // I log all camera settings I made controllable for this demo.

        try {
            FileWriter fw = new FileWriter(outputFile);

            fw.write("Preview size: " + parameters.get("preview-size") + "\n");
            fw.write("Preview fps range: " + parameters.get("preview-fps-range") + "\n");
            fw.write("Focus mode: " + parameters.getFocusMode() + "\n");
            fw.write("Zoom: " + parameters.getZoom() + "\n");
            fw.write("Recording hint: " + parameters.get("\"recording-hint\"") + "\n");
            fw.write("Video stabilization supported: " + parameters.isVideoStabilizationSupported() + "\n");
            fw.write("Video stabilization enabled: " + parameters.getVideoStabilization() + "\n");
            fw.write("Frames format: " + parameters.get("preview-format") + "\n");
            fw.write("Approximate recording start time (ms): " + startTime + "\n");

            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean WriteFrame(File directory, byte[] data, String extension) {
        // Create a unique file name based on the timestamp in milliseconds
        // (the name will be unique, because no one ever heard about
        // smartphone camera preview with 1000 fps)
        File outputFile = getOutputFile(directory, extension);

        // Don't wont to
        try {
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(data);
            fos.close();
        } catch (IOException e) {
            // Well, something went wrong.
            // It's high time to check what permissions are missing.
            e.printStackTrace();
            return false;
        }

        // Yay! You successfully put a lot of data to you storage.
        // It's up to you to remove all that when you don't need it anymore.
        return true;
    }

    // Other methods
    private void Inform(String text, boolean update) {
        // Users can be persistent sometimes, which can result in a bunch of pending toasts.
        // "Inform" wrapper limits the number of active toasts to 1.
        // Don't know if it is how it should be implemented
        // (I wish there were a method to cancel all toasts)
        if (lastToast != null) lastToast.cancel();

        if (update && lastToastText != null) {
            lastToastText = lastToastText + "\n" + text;
        } else {
            lastToastText = text;
        }

        lastToast = Toast.makeText(this, lastToastText, Toast.LENGTH_SHORT);
        lastToast.show();
    }

    private void Inform(String text) {
        Inform(text, false);
    }

    public void CloseApp(View view) {
        // Sometimes the camera cannot be recovered, so the best choice is to shutdown the app
        // (some devices keep the process alive even after native way of shutting the app down,
        // thus I implemented the easiest solution - manual shutdown)
        releaseCamera();
        finish();
        System.exit(0);
    }
}
