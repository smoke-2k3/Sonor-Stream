package com.classic.sonorstream;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.Context.CONNECTIVITY_SERVICE;
import static com.classic.sonorstream.Constants.ACTION_START;
import static com.classic.sonorstream.Constants.CAPTURE_MEDIA_PROJECTION_REQUEST_CODE;
import static com.classic.sonorstream.Constants.EXTRA_RESULT_DATA;
import static com.classic.sonorstream.Constants.PREFS_NAME;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StreamActivity extends Fragment {
    Intent audioCaptureIntent;
    View v;
    boolean isWifiConnected = false;
    boolean isHotspotEnabled = false;
    static String serverDisplayName = "Android_Server";
    static HashMap<String,String> client_data_list;
    SharedPreferences settings;
    static ClientListAdapter cla;
    SharedPreferences.Editor editor;
    static int streamMode = -1;
    MaterialButton stream;
    Boolean streaming = false;
    static Boolean stereo = false;
    TextView serverIP;
    static String clientip;
    static boolean from_file = false;
    static String filePath = null;
    static {
        System.loadLibrary("native-lib");
    }
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private BroadcastReceiver hotspotStateReceiver;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = requireActivity().getSharedPreferences(PREFS_NAME, 0);
        streamMode = settings.getInt("StreamingMode", 0);
        editor = settings.edit();

        connectivityManager = (ConnectivityManager) requireActivity().getSystemService(CONNECTIVITY_SERVICE);

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                requireActivity().runOnUiThread(() -> {
                    isWifiConnected = true;
                    serverIP.setCompoundDrawables(null, null, null, null);
                    serverIP.setText(getLocalIpAddress());
                });
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                requireActivity().runOnUiThread(() -> {
                    isWifiConnected = false;
                    serverIP.setText("Not Connected");
                    serverIP.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_info, 0, 0, 0);
                });
            }
        };

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);

        hotspotStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action)) {
                    int state = intent.getIntExtra("wifi_state", 0);
                    if (state == 13) { // WIFI_AP_STATE_ENABLED
                        isHotspotEnabled = true;
                        serverIP.setCompoundDrawables(null,null,null,null);
                        serverIP.setText(getLocalIpAddress());
                        //Toast.makeText(requireActivity(), "Hotspot Enabled", Toast.LENGTH_SHORT).show();
                    } else if (state == 11) { // WIFI_AP_STATE_DISABLED
                        isHotspotEnabled = false;
                        serverIP.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_info, 0, 0, 0);
                        serverIP.setText("Not Connected");
                    }
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
        requireActivity().registerReceiver(hotspotStateReceiver, intentFilter);

        //WindowCompat.setDecorFitsSystemWindows(getWindow(), false); // render in display cutouts

        //Status bar height and set toolbar top margin
        //Rect rectangle = new Rect();
        //Window window = getWindow();
        //window.getDecorView().getWindowVisibleDisplayFrame(rectangle);
        //ConstraintLayout base = findViewById(R.id.base_layout);
        //ConstraintSet constraintSet = new ConstraintSet();
        //constraintSet.clone(base);
        //constraintSet.connect(R.id.appbar,ConstraintSet.TOP,R.id.base_layout,ConstraintSet.TOP,rectangle.top);

        //bottom nav bar height & constraints
        // Resources resources = this.getResources();
        // @SuppressLint({"DiscouragedApi", "InternalInsetResource"}) int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        //if (resourceId > 0) {
            //constraintSet.connect(R.id.conn_client_rect,ConstraintSet.BOTTOM,R.id.base_layout,ConstraintSet.BOTTOM,resources.getDimensionPixelSize(resourceId));
        //}
        //constraintSet.applyTo(base);


    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        v = inflater.inflate(R.layout.stream_activity, container, false);
        serverIP = v.findViewById(R.id.server_ip);
        if(isConnectedToWifi(requireActivity())) isWifiConnected = true;
        if(isHotspotEnabled(requireActivity())) isHotspotEnabled = true;
        if(isWifiConnected || isHotspotEnabled) {
            serverIP.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            serverIP.setText(getLocalIpAddress());
        }
        else {
            serverIP.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_info, 0, 0, 0);
            serverIP.setText("Not Connected");
        }
        /*
        //bottom Sheet
        //ConstraintLayout bottom_sheet  = v.findViewById(R.id.file_chooser);
        //BottomSheetBehavior<ConstraintLayout> sheet_Behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottom_sheet);

        //Bottom sheet callback
        sheet_Behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View view, int newState) {
                switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN:
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED:
                        break;
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        break;
                    case BottomSheetBehavior.STATE_DRAGGING:
                        break;
                    case BottomSheetBehavior.STATE_SETTLING:
                        break;
                    case BottomSheetBehavior.STATE_HALF_EXPANDED:
                        break;
                }
            }
            @Override
            public void onSlide(@NonNull View view, float v) {

            }
        });
         */

        @SuppressLint("UseSwitchCompatOrMaterialCode")

        //Stream button controls
        View move_N_select = v.findViewById(R.id.movable_selector);
        Button appAudio = v.findViewById(R.id.app_audio);
        Button micAudio = v.findViewById(R.id.mic_audio);
        Button fileAudio = v.findViewById(R.id.file_audio);
        MaterialButtonToggleGroup mtbg = v.findViewById(R.id.toggle_button_options);
        int lMargin = ((ConstraintLayout.LayoutParams) move_N_select.getLayoutParams()).leftMargin;
        AtomicInteger w = new AtomicInteger();
        mtbg.post(() -> {
            w.set(mtbg.getWidth());
            System.out.println(mtbg.getX());
            appAudio.setWidth(w.get() /3);
            micAudio.setWidth(w.get() /3);
            fileAudio.setWidth(w.get() /3);
            //set the selector at desired value
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) move_N_select.getLayoutParams();
            params.leftMargin = lMargin + streamMode*w.get() / 3;
            params.rightMargin = lMargin - streamMode*w.get() / 3;
            move_N_select.setLayoutParams(params);
        });

        ValueAnimator vAnim = new ValueAnimator();
        vAnim.setDuration(400);
        vAnim.addUpdateListener(animation -> {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) move_N_select.getLayoutParams();
            params.leftMargin = (int)animation.getAnimatedValue();
            params.rightMargin = 2*lMargin-(int)animation.getAnimatedValue();
            move_N_select.setLayoutParams(params);
        });


        switch (streamMode){
            case 0: mtbg.check(R.id.app_audio);
                    break;
            case 1: mtbg.check(R.id.mic_audio);
                    break;
            case 2: mtbg.check(R.id.file_audio);
        }

        //Toggle button listeners
        appAudio.setOnClickListener(view -> {
            if(streamMode != 0) {
                //if(checkServiceRunning(StreamService.class))

                vAnim.setIntValues(((ConstraintLayout.LayoutParams) move_N_select.getLayoutParams()).leftMargin, lMargin);
                vAnim.start();
                streamMode = 0;
                editor.putInt("StreamingMode", streamMode);
                editor.apply();
            }
        });
        micAudio.setOnClickListener(view -> {
            if(streamMode != 1) {
                streamMode = 1;
                vAnim.setIntValues(((ConstraintLayout.LayoutParams) move_N_select.getLayoutParams()).leftMargin, lMargin + w.get() / 3);
                vAnim.start();
                streamMode = 1;
                editor.putInt("StreamingMode", streamMode);
                editor.apply();
            }
        });
        fileAudio.setOnClickListener(view -> {
            if(streamMode != 2) {
                // Todo: implement
                // startPicking();
                vAnim.setIntValues(((ConstraintLayout.LayoutParams) move_N_select.getLayoutParams()).leftMargin, lMargin + 2 * w.get() / 3);
                vAnim.start();
                streamMode = 2;
                editor.putInt("StreamingMode", streamMode);
                editor.apply();
            }
        });

        //Client list recycler section
        client_data_list = new HashMap<>();
        RecyclerView recyclerView = v.findViewById(R.id.client_list_recy);
        cla = new ClientListAdapter(client_data_list);
        recyclerView.setAdapter(cla);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireActivity()));

        //Start/Stop button
        stream = v.findViewById(R.id.start_stop);

        if(checkServiceRunning(ReceiveService.class)) {
            stream.setIcon(ContextCompat.getDrawable(requireActivity(), R.drawable.stop_icon));
            stream.setPadding((int) convertDpToPixel(15,requireActivity()),0,0,0);
            streaming = true;
        }
        else {
            stream.setPadding((int) convertDpToPixel(17,requireActivity()),0,0,0);
            stream.setIcon(ContextCompat.getDrawable(requireActivity(), R.drawable.play_icon));
        }

        stream.setOnClickListener(v1 -> {
            if(!streaming)
            {
                if(isHotspotEnabled|| isWifiConnected)
                {
                    streaming = true;
                    ReceiveActivity.discovery_service_running = false;
                    startMediaProjectionRequest();
                }
                else {
                    Toast.makeText(requireActivity(),"Connect to network",Toast.LENGTH_SHORT).show();
                }
            }
            else
            {
                stream.setIcon(ContextCompat.getDrawable(requireActivity(), R.drawable.play_icon));
                stream.setPadding((int) convertDpToPixel(17,requireActivity()),0,0,0);
                ReceiveActivity.discovery_service_running = true;
                stopAudioTransmission();
                requireActivity().stopService(new Intent(getActivity(), StreamService.class));
                streaming = false;
            }
        });
        return v;
    }

    private void startMediaProjectionRequest() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) requireActivity().getApplication()
                .getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                CAPTURE_MEDIA_PROJECTION_REQUEST_CODE
        );
    }

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    filePath = FileUtils.getPath(requireActivity(),uri);
                    Toast.makeText(requireActivity(), "File path: " + filePath, Toast.LENGTH_LONG).show();

                    from_file = true;
                    startService(0);

                    /*
                    // flip layout to player
                    ConstraintLayout tmp = v.findViewById(R.id.file_chooser);
                    ViewGroup parent = (ViewGroup) tmp.getParent();
                    int index = parent.indexOfChild(tmp);
                    parent.removeView(tmp);
                    tmp = (ConstraintLayout) getLayoutInflater().inflate(R.layout.player,parent,false);
                    parent.addView(tmp, index);
                     */
                }
            }
    );

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/mpeg");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(intent);
    }

    public void startPicking() {
        if (ActivityCompat.checkSelfPermission(requireActivity(), android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            pickFile();
        } else {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, Constants.PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Constants.PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickFile();
            } else {
                Toast.makeText(requireActivity(), "Permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAPTURE_MEDIA_PROJECTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                stream.setIcon(ContextCompat.getDrawable(requireActivity(), R.drawable.stop_icon));
                stream.setPadding((int) convertDpToPixel(15,requireActivity()),0,0,0);
                Toast.makeText(requireActivity(),
                        "MediaProjection permission obtained. Foreground service will be started to capture audio.",
                        Toast.LENGTH_SHORT
                ).show();

                audioCaptureIntent = new Intent(getActivity(), StreamService.class);
                audioCaptureIntent.setAction(ACTION_START);
                audioCaptureIntent.putExtra(EXTRA_RESULT_DATA, data);
                requireActivity().startForegroundService(audioCaptureIntent);

            } else {
                stream.setIcon(ContextCompat.getDrawable(requireActivity(), R.drawable.play_icon));
                stream.setPadding((int) convertDpToPixel(17,requireActivity()),0,0,0);
                Toast.makeText(requireActivity(),
                        "Request to obtain MediaProjection denied.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    public static float convertDpToPixel(float dp, Context context){
        return dp * ((float) context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

    public boolean checkServiceRunning(Class<?> serviceClass){
        ActivityManager manager = (ActivityManager) requireActivity().getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
            if (serviceClass.getName().equals(service.service.getClassName()))
                return true;
        return false;
    }
    public static boolean isConnectedToWifi(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
        }
        return false;
    }
    public static boolean isHotspotEnabled(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            try {
                Method method = wifiManager.getClass().getMethod("getWifiApState");
                Object result = method.invoke(wifiManager);

                if (result != null) {
                    int wifiApState = (int) result;

                    Field field = wifiManager.getClass().getField("WIFI_AP_STATE_ENABLED");
                    Object fieldValue = field.get(wifiManager);

                    if (fieldValue != null) {
                        int wifiApEnabled = (int) fieldValue;
                        return wifiApState == wifiApEnabled;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
        if (hotspotStateReceiver != null) {
            requireActivity().unregisterReceiver(hotspotStateReceiver);
        }
    }


    void startService(int mode)
    {
        audioCaptureIntent = new Intent(getActivity(), StreamService.class);
        audioCaptureIntent.setAction(ACTION_START);
        requireActivity().startForegroundService(audioCaptureIntent);
    }

    public native void stopAudioTransmission();
}
