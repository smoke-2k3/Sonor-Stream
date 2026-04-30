package com.classic.sonorstream;

import android.os.Handler;
import android.os.Looper;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerListManager {
    private final ConcurrentHashMap<String, Long> serverLastHeartbeat = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1);
    private static final long HEARTBEAT_INTERVAL = 5000; // Heartbeat interval in milliseconds
    private static final long TIMEOUT_THRESHOLD = 2 * HEARTBEAT_INTERVAL; // Timeout threshold in milliseconds

    public ServerListManager() {
        // Schedule the heartbeat task
        heartbeatScheduler.scheduleAtFixedRate(this::checkServerStatus, 0, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void processServerHeartbeat(String serverAddress) {
        // Update or add server with the current timestamp
        serverLastHeartbeat.put(serverAddress, System.currentTimeMillis());
    }

    private void checkServerStatus() {
        long currentTime = System.currentTimeMillis();

        Iterator<Map.Entry<String, Long>> iterator = serverLastHeartbeat.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            long elapsedTime = currentTime - entry.getValue();

            if (elapsedTime > TIMEOUT_THRESHOLD) {
                // Server is inactive, handle accordingly
                String serverAddress = entry.getKey();
                iterator.remove();
                handleInactiveServer(serverAddress);
            }
        }
    }

    private void handleInactiveServer(String serverAddress) {
        // Perform actions when a server becomes inactive
        Handler mainHandler = new Handler(Looper.getMainLooper());
        System.out.println("Server " + serverAddress + " is inactive.");
        ReceiveActivity.server_data_list.remove(serverAddress);
        mainHandler.post(() -> ReceiveActivity.sla.notifyItemRangeRemoved(0,ReceiveActivity.server_data_list.size()+1));

        // You may want to remove the inactive server from the list or take other appropriate actions.
    }

    // Example method to stop the heartbeat scheduler when it's no longer needed
    public void stopHeartbeatScheduler() {
        heartbeatScheduler.shutdown();
    }
}
