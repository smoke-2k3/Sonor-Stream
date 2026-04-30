#include "StreamEngine.h"
#include <cmath>
#include <jni.h>
#include <fstream>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <cstring>
#include <thread>
#include <chrono>
#include <netdb.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <cerrno>
#include <iostream>
#include <mutex>
#include <vector>
#include "SocketInit.h"
#include "Constants.h"
#include "libmpg123/mpg123.h"
//#include "OboeMicRecorder.cpp" // change to .h
//#include "ClientCommThread.cpp" // change to .h

// Global variables
int sockfd;
int sndSocket;
struct sockaddr_in destAddr{};
std::vector<struct sockaddr_in> clientList;
std::mutex clientListMutex;

bool issysAudioThreadRunning = false;

// JNI function to stop the audio transmission
extern "C" JNIEXPORT void JNICALL
Java_com_classic_sonorstream_StreamActivity_stopAudioTransmission(JNIEnv *env, jobject /* this */) {
    // Set the audio thread flag to false
    issysAudioThreadRunning = false;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_classic_sonorstream_StreamService_set_1nativeRate_1andStart(JNIEnv *env, jobject thiz,
                                                                     jint sample_rate,
                                                                     jint frames_per_burst) {
    //OboeMicRecorder omr;
    //omr.setOptimalRates((int32_t) sample_rate, (int32_t) frames_per_burst);
    //omr.start_mic_rec();
}


extern "C"
JNIEXPORT void JNICALL
Java_com_classic_sonorstream_StreamService_send_1natively(JNIEnv *env, jobject thiz,
                                                          jshortArray array) {
    jshort *shortElements = env->GetShortArrayElements(array, nullptr);
    size_t byteLength = env->GetArrayLength(array) * sizeof(jshort);
    {
        std::lock_guard<std::mutex> lock(clientListMutex);
        int numClients = clientList.size();
        
        if (numClients > 0) {
            // Variable Length Array (VLA) for message headers
            struct mmsghdr msgvec[numClients];
            struct iovec iov[1];
            
            iov[0].iov_base = shortElements;
            iov[0].iov_len = byteLength;

            for (int i = 0; i < numClients; i++) {
                memset(&msgvec[i], 0, sizeof(struct mmsghdr));
                msgvec[i].msg_hdr.msg_name = &clientList[i];
                msgvec[i].msg_hdr.msg_namelen = sizeof(struct sockaddr_in);
                msgvec[i].msg_hdr.msg_iov = iov;
                msgvec[i].msg_hdr.msg_iovlen = 1;
            }

            // Send to all clients simultaneously in a single kernel system call
            sendmmsg(sndSocket, msgvec, numClients, 0);
        }
    }
    env->ReleaseShortArrayElements(array, shortElements, 0);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_classic_sonorstream_StreamService_init_1native_1stream(JNIEnv *env, jobject thiz) {
    sndSocket = SocketInit().init_socket(0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_classic_sonorstream_StreamService_addClientNative(JNIEnv *env, jobject thiz,
                                                                  jstring ip) {
    const char *ipAddress = env->GetStringUTFChars(ip, nullptr);
    destAddr.sin_family = AF_INET;
    destAddr.sin_port = htons(14444); // Set the destination port
    destAddr.sin_addr.s_addr = inet_addr(ipAddress); // Set the destination IP address
    {
        std::lock_guard<std::mutex> lock(clientListMutex);
        clientList.push_back(destAddr);
    }
    env->ReleaseStringUTFChars(ip, ipAddress);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_classic_sonorstream_ClientListManager_removeClientNative(JNIEnv *env, jobject thiz,
                                                                  jstring ip) {
    const char *ipAddress = env->GetStringUTFChars(ip, nullptr);
    {
        std::lock_guard<std::mutex> lock(clientListMutex);
        for (int i = 0; i < clientList.size(); i++) {
            if (clientList[i].sin_addr.s_addr == inet_addr(ipAddress)) {
                clientList.erase(clientList.begin() + i);
                break;
            }
        }
    }
    env->ReleaseStringUTFChars(ip, ipAddress);
}