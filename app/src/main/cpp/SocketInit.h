#ifndef SONOR_STREAM_SOCKETINIT_H
#define SONOR_STREAM_SOCKETINIT_H

#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <android/log.h>
#include <unistd.h>
#include <cstring>
#include <linux/if.h>
#include <cstdio>
#include "Constants.h"


static struct sockaddr_in multicastAddr;

class SocketInit {
public:
    int init_socket(int mode);
    static void del_socket(int &socket);
private:
    struct in_addr serverAddr{};
    struct sockaddr_in serverAddress;
    static const char* getLocalIpAddress();
};

//Mode 0: Uni-cast socket
//Mode 1: Multicast Socket
//Mode 2: Broadcast Socket
int SocketInit::init_socket(int mode) {
    // Create a UDP socket
    static int sockfd = -1;
    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    if (sockfd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "AudioStreamer", "Failed to create socket");
        return -1;
    }
    else __android_log_print(ANDROID_LOG_ERROR, "AudioStreamer", "Socket Created");

    //Unicast config
    memset(&serverAddress, 0, sizeof(serverAddress));
    serverAddress.sin_family    = AF_INET; // IPv4
    serverAddress.sin_addr.s_addr = INADDR_ANY;
    serverAddress.sin_port = htons(12222);

    // Configure server address for multicast
    memset(&serverAddr, 0, sizeof(serverAddr));
    multicastAddr.sin_family = AF_INET;
    multicastAddr.sin_port = htons(SERVER_PORT);
    multicastAddr.sin_addr.s_addr = inet_addr(MULTICAST_ADDR);
    serverAddr.s_addr = inet_addr(getLocalIpAddress());

    switch (mode) {
        case 0: { // Uni-cast socket
            if ( bind(sockfd, (const struct sockaddr *)&serverAddress,sizeof(serverAddress)) < 0 ) {
                perror("bind failed");
                exit(EXIT_FAILURE);
            }
            return sockfd;
        }
        case 1: { //Multicast Socket
            if (setsockopt(sockfd, IPPROTO_IP, IP_MULTICAST_IF, (char *)&serverAddr, sizeof(serverAddr)) < 0) {
                __android_log_print(ANDROID_LOG_ERROR, "AudioStreamer", "Failed to bind socket");
                close(sockfd);
                return -1;
            }
            else __android_log_print(ANDROID_LOG_ERROR, "AudioStreamer", "Socket bound");
            return sockfd;
        }
        case 2: {
            //broadcast Todo:
            return -1;
        }
    }
    return -1;
}

const char* SocketInit::getLocalIpAddress() {
    int sock;
    struct ifconf ifConf{};
    struct ifreq ifr[50];
    unsigned int interfaceCount;
    int i;
    char *localIp;

    // Create a socket
    sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) {
        perror("socket");
        return nullptr;
    }

    // Retrieve the interface configuration
    ifConf.ifc_buf = (char*)ifr;
    ifConf.ifc_len = sizeof(ifr);

    if (ioctl(sock, SIOCGIFCONF, &ifConf) == -1) {
        perror("ioctl");
        close(sock);
        return nullptr;
    }

    // Calculate the number of interfaces
    interfaceCount = ifConf.ifc_len / sizeof(struct ifreq);

    // Iterate through the interfaces
    for (i = 0; i < interfaceCount; i++) {
        auto* saddr = (struct sockaddr_in*)&ifr[i].ifr_addr;

        // Skip loopback interface
        if (strcmp(ifr[i].ifr_name, "lo") == 0)
            continue;

        // Skip non-IPv4 addresses
        if (saddr->sin_family != AF_INET)
            continue;

        //__android_log_print(ANDROID_LOG_DEBUG, "SocketInit", "Interface: %s", ifr[i].ifr_name);

        // Get the IP address as a string
        localIp = inet_ntoa(saddr->sin_addr);

        // Additional check for own hotspot interface
        if (strcmp(ifr[i].ifr_name, "wlan0") == 0) {
            // Close the socket
            close(sock);
            return localIp;
        }
        if (strcmp(ifr[i].ifr_name, "ap0") == 0) {
            // Close the socket
            close(sock);
            return localIp;
        }
    }
    // Close the socket
    close(sock);
    __android_log_print(ANDROID_LOG_ERROR, "AudioStreamer", "getLocalIP is Null");
    return nullptr;
}

void SocketInit::del_socket(int &socket) {
    close(socket);
}

#endif