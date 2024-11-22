package com.classic.sonorstream;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class ReceiveService extends Service {
    static {
        System.loadLibrary("native-lib");
    }
    boolean pinger_running = false;
    private WeakReference<Fragment> fragmentReference;
    String clientDisplayName = "AndroidClient";
    private static final int PING_TIMEOUT = 2000; // Timeout for each ping in milliseconds
    private static final int MAX_PING_TRIES = 3; // Maximum consecutive failed pings
    public ReceiveService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // create the custom or default notification

        startForeground();
        AudioManager myAudioMgr = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        String sampleRateStr = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        int defaultSampleRate = Integer.parseInt(sampleRateStr);
        String framesPerBurstStr = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        int defaultFramesPerBurst = Integer.parseInt(framesPerBurstStr);

        try {
            pinger(InetAddress.getByName(ReceiveActivity.server_ip), 5432);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        native_setDefaultStreamValues(defaultSampleRate, defaultFramesPerBurst);


    }
    private void startForeground() {
        String NOTIFICATION_CHANNEL_ID = "example.permanence";
        String channelName = "Background Service";
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_MIN);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(channel);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("Audio Reception Service")
                .setContentText("Audio is being received")
                // this is important, otherwise the notification will show the way
                // you want i.e. it will show some default notification
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }
    @Override
    public void onDestroy() {
        pinger_running = false;
    }

    public void pinger(InetAddress serverAddress,int pingPort) {
        pinger_running = true;
        new Thread(() -> {
            DatagramSocket socket = null;
            byte[] receiveData = new byte[4];
            byte[] sendData = clientDisplayName.getBytes();
            try{
                DatagramPacket snd_pkt = new DatagramPacket(sendData, sendData.length, serverAddress,pingPort);
                DatagramPacket recv_pkt = new DatagramPacket(receiveData, receiveData.length);
                socket = new DatagramSocket();
                socket.setSoTimeout(PING_TIMEOUT);
                while(pinger_running) {
                    for (int i = 0; i < MAX_PING_TRIES; i++) {
                        socket.send(snd_pkt);
                        try {
                            socket.receive(recv_pkt);
                            String received = new String(recv_pkt.getData());

                            if(!ReceiveActivity.connected) {
                                // Update the layout server connected
                                Intent intent = new Intent("ACTION_LAYOUT_CHANGE");
                                //start audio reception in native side
                                start(ReceiveActivity.server_ip);
                                intent.putExtra("EXTRA_LAYOUT_NUMBER", 1);
                                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                            }
                            ReceiveActivity.connected = true;
                            //break; // Break if ping is successful
                        } catch (IOException e) {
                            System.out.println("Ping failed: " + e.getMessage());
                            if(ReceiveActivity.connected) {
                                // Update the layout server disconnected
                                Intent intent = new Intent("ACTION_LAYOUT_CHANGE");
                                intent.putExtra("EXTRA_LAYOUT_NUMBER", 0);
                                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                            }
                            ReceiveActivity.connected = false;
                            if (i == MAX_PING_TRIES - 1) {
                                System.out.println("Max tries reached, connection failed");
                                Toast.makeText(this, "Max tries reached. Connection failed", Toast.LENGTH_SHORT).show();
                                //TODO : stop the receive service
                                break;
                            }
                        }
                        Thread.sleep(1000);
                    }
                    Thread.sleep(1000);
                }
                socket.close();
            } catch(Exception e){
                Log.d("udp_broadcast","error  " + e.toString());
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }
        }).start();
    }

    // Method to trigger layout change in the fragment
    public native void start(String ip);
    public native void native_setDefaultStreamValues(int defaultSampleRate,int defaultFramesPerBurst);
}