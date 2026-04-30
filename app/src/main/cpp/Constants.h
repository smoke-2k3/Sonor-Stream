//
// Created by Acer on 26-Jun-23.
//

#ifndef SONOR_STREAM_CONSTANTS_H
#define SONOR_STREAM_CONSTANTS_H

// Define the server IP address and port
#define SERVER_PORT 14444         // Replace with the server port number
#define MULTICAST_ADDR "233.45.17.10" // Multicast address
#define DEBUG_TAG "MulticastReceiver"
#define JAVA_NATIVE_BUFF_CAP 10
#define LOW_BUFFER 32768 // 32 KB
#define PLAYER_BUFFER 1024 // ~23ms at 44100Hz mono (lower latency than 2048)

#endif //SONOR_STREAM_CONSTANTS_H
