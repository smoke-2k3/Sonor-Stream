//
// Created by Acer on 10-Jul-23.
//
#ifndef SONOR_STREAM_SYSTEMAUDIOTRANSMISSION_H
#define SONOR_STREAM_SYSTEMAUDIOTRANSMISSION_H

#include <iostream>
#include <cstdio>
#include "E:/AndroidStudio/SDK/ndk/25.1.8937393/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/include/arpa/inet.h"
#include "E:\AndroidStudio\SDK\ndk\25.1.8937393\toolchains\llvm\prebuilt\windows-x86_64\sysroot\usr\include\sys\socket.h"
#include "E:\AndroidStudio\SDK\ndk\25.1.8937393\toolchains\llvm\prebuilt\windows-x86_64\sysroot\usr\include\netinet\in.h"
#include "Constants.h"

class SystemAudioTransmission {
public:

private:
    bool initStreaming();
    int sockfd;
    struct in_addr serverAddr;
    struct sockaddr_in multicastAddr;
};

bool SystemAudioTransmission::initStreaming() {
    // Create a UDP socket
    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    if (sockfd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "AudioStreamer", "Failed to create socket");
        return false;
    }

    // Configure server address
    memset(&serverAddr, 0, sizeof(serverAddr));
    multicastAddr.sin_family = AF_INET;
    multicastAddr.sin_port = htons(SERVER_PORT);
    multicastAddr.sin_addr.s_addr = inet_addr(MULTICAST_ADDR);
    serverAddr.s_addr = inet_addr(getLocalIpAddress());

    // Bind socket to server address
    if (setsockopt(sockfd, IPPROTO_IP, IP_MULTICAST_IF, (char *)&serverAddr, sizeof(serverAddr)) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "AudioStreamer", "Failed to bind socket");
        close(sockfd);
        return false;
    }
    return true;
}

#endif //SONOR_STREAM_SYSTEMAUDIOTRANSMISSION_H
