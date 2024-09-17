#include <iostream>
#include <cstring>
#include <unistd.h>
#include <android/log.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <thread>
#include <jni.h>
#include <fstream>
#include "AudioPlayer.h"
#include "Constants.h"
#include "OboePlayer.h"

// Global variables
struct sockaddr_in addr;
int addrlen, sock;
socklen_t len;
struct ip_mreq mreq;

bool setSocketTimeout(int i);

bool isrecv = false;

// Function to initialize the UDP socket and join the multicast group
bool initializeMulticastSocket() {
    /* set up socket */
    sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "%s",
                            " error: socket()");
        return false;
    }
    bzero((char *) &addr, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons(SERVER_PORT);
    addrlen = sizeof(addr);

    /* receive */
    if (bind(sock, (struct sockaddr *) &addr, sizeof(addr)) < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "%s", " error: bind");
        return false;
    }

    mreq.imr_multiaddr.s_addr = inet_addr(MULTICAST_ADDR);
    mreq.imr_interface.s_addr = htonl(INADDR_ANY);
    if (setsockopt(sock, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, sizeof(mreq))
        < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "%s",
                            " error: setsockopt mreq");
        return false;
    }

    int reuse = 1;
    if (setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, (char *) &reuse,
                   sizeof(reuse)) < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "%s",
                            " Setting SO_REUSEADDR error");

        close(sock);
        return false;
    } else {
        __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "%s",
                            " Setting SO_REUSEADDR...OK");
    }
    return true;
}

bool initSocket(const char * serverIP) {
    // Create a UDP socket
    sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) {
        std::cerr << "Failed to create socket." << std::endl;
        return false;
    }

    /*
    // Enable broadcast on the socket
    int broadcastEnable = 1;
    int ret = setsockopt(sock, SOL_SOCKET, SO_BROADCAST, &broadcastEnable, sizeof(broadcastEnable));
    if (ret < 0) {
        std::cerr << "Error setting socket options" << std::endl;
        close(sock);
        return false;
    }
    */

    // Configure server address
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(SERVER_PORT);
    addr.sin_addr.s_addr = htonl(INADDR_ANY); //don't know why it's working. there is no requirement of server address in order to receive...

    len = sizeof(addr);

    // No need to Bind the socket to a specific address and port
    // If a UDP socket is unbound and either sendto or connect are called on it,
    // the system will automatically bind it for you and thus the recvfrom call later on will succeed.
    // recvfrom will not bind a socket, as this call expects the socket to have been bound already, or an error will be thrown.
    // so here, the connection to the server will be initiated by client and the socket will be automatically bound.

    //Not working without binding.....don't know why.. "may be because this is android environment". Try to test this on windows...
    if (bind(sock, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "%s",
                            "Bind failed");
        close(sock);
        return false;
    }

    // ssize_t bytesRead = recvfrom(sock, buffer, BUFFER_SIZE, 0,(struct sockaddr*)&clientAddr, &clientAddrLen);
    sendto(sock, "hello", strlen("hello"),0, (const struct sockaddr *) &addr,sizeof(addr));
    __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "%s","Sent message");
    return true;
}

bool setSocketTimeout(int i) {
    // Set the receive timeout
    struct timeval timeout{};
    timeout.tv_sec = i;  // Timeout in seconds
    timeout.tv_usec = 0;
    if (setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, (const char*)&timeout, sizeof(timeout)) == -1) {
        __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "%s",
                            "Failed To set Timeout");
        close(sock);
        return false;
    }
    return true;
}

// Function to receive and process audio packets
void receiveAudioPackets() {
    __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "%s",
                        "Receiving Started");
    // Receive and process audio packets
    isrecv = true;
    //std::thread audioThread(pingThread);
    //audioThread.detach();
    OboePlayer player;
    const int numSamples = 360;
    player.start();
    short buffer[numSamples];
    while (isrecv) {
        recvfrom(sock, buffer, numSamples * sizeof(short), 0,(struct sockaddr*) &addr,&len);
        for (short & i : buffer) {
            lfQueue.push(i);
        }
        /*
        //buffer[0] = packet type/Payload Type
        switch (buffer[0]) {

            default: {
                //Main Audio Data .This code is currently modified for testing.
                //dataPtr = buffer + 12;
                for (int i = 0; i < numSamples; i++) {
                    lfQueue.push((short)((buffer[i * 2 + 1] << 8) | (buffer[i * 2] & 0xFF)));
                }
                break;
            }
            case 'A': {
                //Audio head got. Update Oboe player
                //Audio head packet -> char(1 byte), rate(long), channel(4 bytes)
                // player.stop();
                // player.mStream->close();
                // after this only the channel and rate will be changed. if you change the rate while player is running, it will have no effect.
                if(buffer[1] == 'N') break; //No Transfers running
                int channel;
                long rate;
                std::memcpy(&rate, buffer + sizeof('A'), sizeof(long));
                std::memcpy(&channel, buffer + sizeof('A') + sizeof(long), sizeof(int));
                player.setChannel(channel);
                player.setRate(rate);
                break;
            }
            case 'L': {
                //Got Play signal
                break;
            }
            case 'U': {
                //Got Send Pause signal
                break;
            }
        }
         */
    }
    player.stop();
}

// Function to stop receiving audio packets and leave the multicast group
extern "C"
JNIEXPORT void JNICALL
Java_com_classic_sonorstream_ReceiveActivity_stop(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {
    // Leave the multicast group
    isrecv = false;
    if (setsockopt(sock, IPPROTO_IP, IP_DROP_MEMBERSHIP, (char *) &mreq,
                   sizeof(mreq)) < 0) {
        std::cerr << "Failed to leave multicast group." << std::endl;
    }

    // Close the socket
    close(sock);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_classic_sonorstream_ReceiveService_start(JNIEnv *env, __attribute__((unused)) jobject thiz, jstring ip) {
    // Initialize the UDP socket and join the multicast group
    if (!initSocket(env->GetStringUTFChars( ip,nullptr))) {
        std::cerr << "Failed to initialize socket." << std::endl;
        return;
    }
    // Start a new thread for audio reception
    std::thread audioThread(receiveAudioPackets);
    audioThread.detach();
}
extern "C"
JNIEXPORT void JNICALL
Java_com_classic_sonorstream_ReceiveService_native_1setDefaultStreamValues(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jint sampleRate,
                                                                           jint framesPerBurst) {
    oboe::DefaultStreamValues::SampleRate = (int32_t) sampleRate;
    oboe::DefaultStreamValues::FramesPerBurst = (int32_t) framesPerBurst;
}