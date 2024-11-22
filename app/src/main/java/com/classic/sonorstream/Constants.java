package com.classic.sonorstream;

public class Constants {
    public static final int NUM_SAMPLES_PER_READ = 360;
    public static final int BYTES_PER_SAMPLE = 2;
    public static final int BUFFER_SIZE_IN_BYTES = NUM_SAMPLES_PER_READ * BYTES_PER_SAMPLE;
    public static final String PREFS_NAME = "SharedPreferences";
    public static final String ACTION_START = "AudioCaptureService:Start";
    public static final String ACTION_STOP = "AudioCaptureService:Stop";
    public static final String EXTRA_RESULT_DATA = "AudioCaptureService:Extra:ResultData";
    public static final int CAPTURE_MEDIA_PROJECTION_REQUEST_CODE = 13;
    public static final int PERMISSION_REQUEST_CODE = 1;
    public static final int PICK_FILE_REQUEST_CODE = 2;
}
