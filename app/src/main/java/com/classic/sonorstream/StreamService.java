package com.classic.sonorstream;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;
import static com.classic.sonorstream.StreamActivity.client_data_list;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.StrictMode;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class StreamService extends Service {
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private Thread audioCaptureThread;
    ClientListManager clientListManager;
    private AudioRecord audioRecord;
    private boolean broadcast_service_running = false;
    private boolean pingReply_service_running = false;
    int buff_siz = Constants.BUFFER_SIZE_IN_BYTES; //for native access don't delete
    WifiManager.WifiLock wifiLock;
    private static final ReentrantLock lock = new ReentrantLock();
    private final Condition pauseCondition = lock.newCondition();
    private static volatile boolean isPaused = false;

    static {
        System.loadLibrary("native-lib");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    //The Entry
    @Override
    public void onCreate() {
        super.onCreate();
        clientListManager = new ClientListManager();
        // Start services
        startForeground(); // Notification
        broadcastService(12345); // Server Name broadcast loop (Broadcast socket)
        pingReplyService(5432); // Client ping reply loop (Uni-cast socket)

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF , "MyWifiLock");
        wifiLock.acquire();

        //initialize once if needed after
        mediaProjectionManager = (MediaProjectionManager) getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        switch (StreamActivity.streamMode){
            //Case 0 not needed as already initialized before
            case 0: { // Sys Audio
                break;
            }
            case 1: { // Mic Audio
                //set optimal rates for mic recording and start.
                AudioManager myAudioMgr = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
                set_nativeRate_andStart(Integer.parseInt(myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)), Integer.parseInt(myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)));

                break;
            }
            case 2: { //From file
                //Todo: not yet implemented
                Toast.makeText(this, "Not implemented", Toast.LENGTH_SHORT).show();
                stopService(new Intent(this, StreamService.class));
                break;
            }
            default: {
                Toast.makeText(this, "Invalid Stream Mode", Toast.LENGTH_SHORT).show();
                stopService(new Intent(this, StreamService.class));
                break;
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return Service.START_NOT_STICKY;
        } else {
            switch (intent.getAction()) {
                case Constants.ACTION_START: {
                    mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK,
                            intent.getParcelableExtra(Constants.EXTRA_RESULT_DATA));
                    switch (StreamActivity.streamMode){
                        case 0: { //System audio
                            startAudioCapture();
                            break;
                        }
                        case 1: { // Mic Audio
                            break;
                        }
                        case 2: { //From file
                            //Todo: not yet implemented
                            Toast.makeText(this, "Not implemented", Toast.LENGTH_SHORT).show();
                            stopService(new Intent(this, StreamService.class));
                            break;
                        }
                        default:break;
                    }
                    return Service.START_STICKY;
                }
                case Constants.ACTION_STOP: {
                    stopAudioCapture();
                    return Service.START_NOT_STICKY;
                }
                default: {
                    throw new IllegalArgumentException("Unexpected action received: ${intent.action}");
                }
            }
        }
    }

    private void startAudioCapture() {
        AudioPlaybackCaptureConfiguration config = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
        }

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT) //samples will be of 16bit
                .setSampleRate(44100) //no.of samples to be captured per sec.
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO) //mono channel
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Recording Permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioRecord = new AudioRecord.Builder()
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(Constants.BUFFER_SIZE_IN_BYTES)
                    .setAudioPlaybackCaptureConfig(config)
                    .build();
        }

        audioRecord.startRecording();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        audioCaptureThread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            short[] capturedAudioSamples = new short[Constants.NUM_SAMPLES_PER_READ+1]; //declared short as samples are of 16-bit (short is of 16bit)
            init_native_stream();

            while (!audioCaptureThread.isInterrupted()) {
                //If you want to save cpu when no one is connected. then uncomment the below code.
                //TODO: Set an option for the pausing the thread. also measure performance difference.
                /*
                lock.lock();
                try {
                    while (isPaused) {
                        pauseCondition.await();
                    }
                */

                //blocking function of the loop
                // "NUM_SAMPLES_PER_READ" itne sample ane tak ye function wait karega. 1ms me 44.1 sample hai toh x ms me 1024...
                //long start2 = System.currentTimeMillis();
                audioRecord.read(capturedAudioSamples, 0, Constants.NUM_SAMPLES_PER_READ); // no. of samples read in a iteration of loop
                //long start1 = System.nanoTime();
                send_natively(capturedAudioSamples);
                //long end1 = System.nanoTime();
                //long end2 = System.currentTimeMillis();
                //System.out.println(end2-start2 + " ");

                //Continuation of the lock code
                /*
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                } finally {
                    lock.unlock();
                }
                */
            }
        });
        audioCaptureThread.start();
    }

    public static void pauseAudioCapture() {
        lock.lock();
        try {
            isPaused = true;
        } finally {
            lock.unlock();
        }
    }

    public void resumeAudioCapture() {
        lock.lock();
        try {
            isPaused = false;
            pauseCondition.signal();
        } finally {
            lock.unlock();
        }
    }

    void stopAudioCapture() {
        //requireNotNull(mediaProjection) { "Tried to stop audio capture, but there was no ongoing capture in place!" }
        if (mediaProjection == null) {
            return;
        }
        if(audioCaptureThread != null)
            audioCaptureThread.interrupt();
        try {
            if(audioCaptureThread != null) {
                audioCaptureThread.join();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if(audioRecord == null)
            return;
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;
        mediaProjection.stop();
        //stopSelf();
    }

    //Mode 0: System Audio
    //Mode 1: Mic Audio
    //Mode 2: File Audio
    public void changeStrMode(int mode){
        switch(StreamActivity.streamMode){ //get current mode
            case 0: {
                StreamActivity.streamMode = mode;
                //Stop Sys audio transmission
                stopAudioCapture();
                if(mode == 1){
                    //Start Mic
                }
                else {
                    //Start file picker
                }
                break;
            }
            case 1: {
                StreamActivity.streamMode = mode;
                //Stop Mic transmission
                if(mode == 0){
                    startAudioCapture();
                }
                else{
                    //Todo: Start file picker
                    int x = 0;
                }
                break;
            }
            case 2: {
                StreamActivity.streamMode = mode;
                //Stop file Transmission

                if(mode == 0){
                    startAudioCapture();
                }
                else{
                    //Todo:start mic capture
                }
                break;
            }
        }
    }
    @Override
    public void onDestroy() {
        if (wifiLock != null) if (wifiLock.isHeld()) wifiLock.release();
        clientListManager.stopHeartbeatScheduler();
        broadcast_service_running = false;
        pingReply_service_running = false;
        stopAudioCapture();
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
                .setContentTitle("SonorStream")
                .setContentText("Server Running")
                // this is important, otherwise the notification will show the way
                // you want i.e. it will show some default notification
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Specify FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            // For older versions, simply start foreground service without specifying type
            startForeground(2, notification);
        }
    }
    public void pingReplyService(int pingReplyPort){
        pingReply_service_running = true;
        String replyData = "Y";
        Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket(pingReplyPort);
                byte[] sendData = replyData.getBytes();
                DatagramPacket pingReplyPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), pingReplyPort);

                byte[] recv_buffer = new byte[128];
                DatagramPacket recvPacket = new DatagramPacket(recv_buffer, recv_buffer.length);
                String receivedIp;
                while(pingReply_service_running){
                    socket.receive(recvPacket);

                    // Update client list
                    receivedIp = recvPacket.getAddress().getHostAddress();
                    clientListManager.processClientHeartbeat(receivedIp);
                    if(!client_data_list.containsKey(receivedIp)) {
                        client_data_list.put(receivedIp, "clientname1");
                        addClientNative(receivedIp);
                        resumeAudioCapture();
                        mainHandler.post(() -> StreamActivity.cla.notifyItemRangeChanged(0,client_data_list.size()));
                    }

                    pingReplyPacket.setAddress(recvPacket.getAddress());
                    pingReplyPacket.setPort(recvPacket.getPort());
                    socket.send(pingReplyPacket);
                    System.out.println("Sent Ping reply");
                }
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException: " + e.getMessage());
            }
        }).start();
    }
    public void broadcastService(int broadcastPort){
        broadcast_service_running = true;
        String data = getLocalIpAddress()+"\n"+"14444"+"\n"+StreamActivity.serverDisplayName; //uni-cast port
        new Thread(() -> {
            DatagramSocket broadcastSocket = null;
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
            try {
                broadcastSocket = new DatagramSocket();
                broadcastSocket.setBroadcast(true);
                byte[] sendData = data.getBytes();
                DatagramPacket broadcastPacket = new DatagramPacket(sendData, sendData.length,getBroadcastAddress(InetAddress.getByName(getLocalIpAddress())) , broadcastPort); //Broadcast Port
                while(broadcast_service_running){
                    broadcastSocket.send(broadcastPacket);
                    System.out.println("Sent broadcast");
                    Thread.sleep(1000);
                }
                broadcastSocket.close();
            } catch (IOException | InterruptedException e) {
                System.out.println("IOException: " + e.getMessage());
            } finally {
                if (broadcastSocket != null && !broadcastSocket.isClosed()) {
                    broadcastSocket.close();
                }
            }
        }).start();
    }
    public InetAddress getBroadcastAddress(InetAddress inetAddr) {
        NetworkInterface temp;
        InetAddress iAddr = null;
        try {
            temp = NetworkInterface.getByInetAddress(inetAddr);
            List<InterfaceAddress> addresses = temp.getInterfaceAddresses();

            for (InterfaceAddress inetAddress: addresses)
                iAddr = inetAddress.getBroadcast();
            //System.out.println("iAddr=" + iAddr);
            return iAddr;
        } catch (SocketException e) {
            e.printStackTrace();
            System.out.println("getBroadcast" + e.getMessage());
        }
        return null;
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
    public native void set_nativeRate_andStart(int sampleRate, int framesPerBurst);
    public native void send_natively(short[] buffer);
    public native void init_native_stream();
    native void addClientNative(String ip);
}
