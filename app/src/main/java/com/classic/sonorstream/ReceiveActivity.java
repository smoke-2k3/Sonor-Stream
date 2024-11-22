package com.classic.sonorstream;

import static android.content.Context.ACTIVITY_SERVICE;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;

public class ReceiveActivity extends Fragment {
    static String server_ip;
    static HashMap<String,String> server_data_list;
    static boolean discovery_service_running = false;
    static ServerListAdapter sla;
    boolean server_list_removal_thread = false;
    static boolean connected = false;
    private ServerListManager serverListManager;
    View v;
    static {
        System.loadLibrary("native-lib");
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        v = inflater.inflate(R.layout.layout_receive, container, false);
        serverListManager = new ServerListManager();

        //Multicast lock
        try {
            WifiManager.MulticastLock lock;
            WifiManager wifi;
            wifi = (WifiManager) requireActivity().getSystemService(Context.WIFI_SERVICE);
            lock = wifi.createMulticastLock("WiFi_Lock");
            lock.setReferenceCounted(true);
            lock.acquire();
        }
        catch(Exception e)
        {
            Log.d("Wifi Exception",""+ e.getMessage());
        }

        //Start server discovery service
        server_discovery(12345);

        server_data_list = new HashMap<>();
        RecyclerView recyclerView = v.findViewById(R.id.serv_list_recycler);
        sla = new ServerListAdapter(server_data_list,this);
        recyclerView.setAdapter(sla);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireActivity()));
        EditText ipInput = v.findViewById(R.id.ip_input);
        Button manual_connect = v.findViewById(R.id.manual_connect_but);

        manual_connect.setOnClickListener(v -> {
            try {
                connect(InetAddress.getByName(ipInput.getText().toString()));
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        });
        v.findViewById(R.id.stop_but).setOnClickListener(v -> {
            if(!checkServiceRunning(ReceiveService.class)) {
                Toast.makeText(requireActivity(), "Service not running", Toast.LENGTH_SHORT).show();
            }
            else {
                stop();
                requireActivity().stopService(new Intent(getActivity(), ReceiveService.class));
            }
        });

        //server_data_list.put("255.255.255.255","hello");
        //sla.notifyItemChanged(0);
        return v;
    }
    private final BroadcastReceiver layoutChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Handle layout change message
            loadLayout(intent.getIntExtra("EXTRA_LAYOUT_NUMBER", 0));
        }
    };
    @Override
    public void onResume() {
        super.onResume();
        // Register the BroadcastReceiver to receive layout change messages
        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(layoutChangeReceiver, new IntentFilter("ACTION_LAYOUT_CHANGE"));
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister the BroadcastReceiver
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(layoutChangeReceiver);
    }

    // Connect to the server with the specified IP address
    public void connect(InetAddress address){
        if(!checkServiceRunning(ReceiveService.class)) {
            Intent receiveService = new Intent(getActivity(), ReceiveService.class);
            receiveService.setAction(Constants.ACTION_START);
            server_ip = address.getHostAddress();
            requireActivity().startForegroundService(receiveService);
        }
        else {
            Toast.makeText(requireActivity(), "Service already running", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        discovery_service_running = false;
        server_list_removal_thread = false;
        serverListManager.stopHeartbeatScheduler();
        super.onDestroy();
    }


    public boolean checkServiceRunning(Class<?> serviceClass){
        ActivityManager manager = (ActivityManager) requireActivity().getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
            if (serviceClass.getName().equals(service.service.getClassName()))
                return true;
        return false;
    }

    @SuppressLint("StaticFieldLeak")
    public void server_discovery(int port){
        discovery_service_running = true;
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            DatagramSocket skt = null;
            byte[] message = new byte[128];
            try{
                DatagramPacket pkt = new DatagramPacket(message, message.length);
                skt = new DatagramSocket(port);
                //skt.setSoTimeout(1500); //in server, thread sleeps for 1000ms
                String receivedIp;
                while(discovery_service_running) {
                    skt.receive(pkt);
                    receivedIp = pkt.getAddress().getHostAddress();
                    serverListManager.processServerHeartbeat(receivedIp);
                    if(!server_data_list.containsKey(receivedIp)) {
                        server_data_list.put(receivedIp, "servername1");
                        mainHandler.post(() -> sla.notifyItemRangeChanged(0,server_data_list.size()));
                    }
                }
                skt.close();
            }catch(Exception e){
                Log.d("udp_broadcast","error  " + e);
            } finally {
                if (skt != null && !skt.isClosed()) {
                    skt.close();
                }
            }
        }).start();

/*
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        executor.scheduleAtFixedRate(() -> mainHandler.post(() -> {
            // Update the UI
            int size;
            size = server_data_list.size();
            server_data_list.clear(); // clear list
            sla.notifyItemRangeRemoved(0, size);
        }), 5, 5, TimeUnit.SECONDS);


 */
    }
    public native void stop();

    @SuppressLint("InflateParams")
    public void loadLayout(int layoutNumber) {
        // Load your new layout resource file here
        View view = null;
        if(layoutNumber == 1) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.server_connected_layout, null);
        }
        else if(layoutNumber == 0) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.layout_receive, null);
        }
        // Replace the current layout with the new layout
        ViewGroup rootView = (ViewGroup) getView();
        assert rootView != null;
        rootView.removeAllViews();
        rootView.addView(view);
    }
}