//
// Created by Acer on 21-Nov-24.
//

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

class SocketInit {
public:
    int init_socket(int mode);
    static void del_socket(int &socket);
private:
    struct in_addr serverAddr{};
    struct sockaddr_in serverAddress;
    struct sockaddr_in multicastAddr;
    static const char* getLocalIpAddress();
};

#endif //SONOR_STREAM_SOCKETINIT_H
