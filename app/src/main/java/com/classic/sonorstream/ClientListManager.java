package com.classic.sonorstream;

import android.os.Handler;
import android.os.Looper;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientListManager {
    private final ConcurrentHashMap<String, Long> clientLastHeartbeat = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1);
    private static final long HEARTBEAT_INTERVAL = 5000; // Heartbeat interval in milliseconds
    private static final long TIMEOUT_THRESHOLD = 2 * HEARTBEAT_INTERVAL; // Timeout threshold in milliseconds

    public ClientListManager() {
        // Schedule the heartbeat task
        heartbeatScheduler.scheduleAtFixedRate(this::checkClientStatus, 0, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void processClientHeartbeat(String clientAddress) {
        // Update or add server with the current timestamp
        clientLastHeartbeat.put(clientAddress, System.currentTimeMillis());
    }

    private void checkClientStatus() {
        long currentTime = System.currentTimeMillis();

        Iterator<Map.Entry<String, Long>> iterator = clientLastHeartbeat.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            long elapsedTime = currentTime - entry.getValue();

            if (elapsedTime > TIMEOUT_THRESHOLD) {
                // Client is inactive, handle accordingly
                String clientAddress = entry.getKey();
                iterator.remove();
                handleInactiveClient(clientAddress);
            }
        }
    }

    private void handleInactiveClient(String clientAddress) {
        // Perform actions when a client becomes inactive
        Handler mainHandler = new Handler(Looper.getMainLooper());
        System.out.println("Client " + clientAddress + " is inactive.");
        StreamActivity.client_data_list.remove(clientAddress);
        if(StreamActivity.client_data_list.size() != 0) removeClientNative(clientAddress);
        mainHandler.post(() -> StreamActivity.cla.notifyItemRangeRemoved(0,StreamActivity.client_data_list.size()+1));
        if(StreamActivity.client_data_list.size() == 0){
            StreamService.pauseAudioCapture();
        }
        // You may want to remove the inactive client from the list or take other appropriate actions.
    }

    // Example method to stop the heartbeat scheduler when it's no longer needed
    public void stopHeartbeatScheduler() {
        heartbeatScheduler.shutdown();
    }

    public native void removeClientNative(String clientAddress);
}
