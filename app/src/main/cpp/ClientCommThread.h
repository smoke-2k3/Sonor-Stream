#include <android/log.h>
#include <thread>
#include <cstring>
#include <iostream>
#include <mutex>
#include <thread>
#include <condition_variable>
#include "Constants.h"
#include "mpg123/src/libmpg123/mpg123.h"
#include "SocketInit.h"
#include "CircularQueue.h"
//#include "OboePlayer.h"

class ClientCommThread {
public:
    ClientCommThread();
    //void startFileTransmission(const char* path);
    void stopFileTransmission();
    void stopServer();
    void playAudio();
    void pauseAudio();

private:
    bool commThreadRunning = false;
    bool fileTransferRunning = false;
    size_t audioHeadPacketSize{};
    unsigned char *audioHeadPacket{};
    bool audioHeadPacketPresent = false;
    //CircularQueue<uint8_t**> sentData;
    std::mutex mtx;
    std::condition_variable cv;
    bool transmissionStartSignal = false;
    //OboePlayer player1;
    function<void()> lowBufferReached();
    void startCommThread();
    void infoBroadcaster(int delay, const char *sName, const char *IP);
};

//Maintain a small buffer so that previously sent buffer is not to be sent again buffer.The new client can wait till it is syncronized with main buffer.
ClientCommThread::ClientCommThread() = default;

void ClientCommThread::startCommThread() {
    /*
    // generate a audio head packet stating that there is no transmission running
    if(audioHeadPacketPresent) delete[] audioHeadPacket; //Clear previous packet
    audioHeadPacketPresent = true;
    audioHeadPacketSize = sizeof('A') + sizeof('N');
    audioHeadPacket = new unsigned char[audioHeadPacketSize];
    std::memcpy(audioHeadPacket, reinterpret_cast<const void *>('A'), sizeof('A'));
    std::memcpy(audioHeadPacket + sizeof('A'), reinterpret_cast<unsigned char*>('N'), sizeof('N'));
    // packet generation ends
     */

    __android_log_print(ANDROID_LOG_ERROR, "AudioStreamer", "Client comm thread started");

    //Socket Must be uni-cast
    SocketInit init;
    int uniSock = init.init_socket(0);
    if(uniSock == -1)
        __android_log_print(ANDROID_LOG_ERROR, "AudioStreamer", "Socket init failed");
    commThreadRunning = true;

    char buffer[1];
    struct sockaddr_in clientAddr{};
    socklen_t clientAddrLen = sizeof(clientAddr);

    //Client request Reception Thread
    while (commThreadRunning) {

        // Receive packet from a client
        ssize_t bytesRead = recvfrom(uniSock, buffer, sizeof(buffer), 0, (struct sockaddr*)&clientAddr, &clientAddrLen);
        if (bytesRead < 0) {
            std::cerr << "Failed to receive packet." << std::endl;
            continue;
        }

        //Buffer[0] = packet type (defined in receiving side)
        switch (buffer[0]) {
            case 'P' : {
                //Ping packet reply. Send 2 times if packet lost somewhere.
                //Also handle newly connected client list.
                ssize_t bytesSent = sendto(uniSock, reinterpret_cast<const void *const>('R'), 1, 0, (struct sockaddr*)&clientAddr, clientAddrLen);
                sendto(uniSock, reinterpret_cast<const void *const>('R'), 1, 0, (struct sockaddr*)&clientAddr, clientAddrLen);
                if (bytesSent < 0) {
                    std::cerr << "Failed to send reply." << std::endl;
                }
                break;
            }
            case 'H': {
                //Audio head request(channels, bitrate, length, etc)
                sendto(uniSock, audioHeadPacket, audioHeadPacketSize, 0, (struct sockaddr *) &multicastAddr,sizeof(multicastAddr));
                sendto(uniSock, audioHeadPacket, audioHeadPacketSize, 0, (struct sockaddr *) &multicastAddr,sizeof(multicastAddr));
                break;
            }
            /*
            case 'B': {
                //Newly connected, send buffer till main thread sent. Not required now.
                break;
            }
            */
            case 'D': {
                //Client Disconnected Todo: update client list
                break;
            }
            default: break;
        }
        /*
        // Process the received packet
        //std::cout << "Received packet from: " << inet_ntoa(clientAddr.sin_addr) << std::endl;
        //std::cout << "Message: " << buffer << std::endl;

        // Prepare reply message
        const char* replyMsg = "Reply from server";
        size_t replyMsgLen = strlen(replyMsg);

        // Send the reply to the client
        ssize_t bytesSent = sendto(sockfd, replyMsg, replyMsgLen, 0, (struct sockaddr*)&clientAddr, clientAddrLen);
        if (bytesSent < 0) {
            std::cerr << "Failed to send reply." << std::endl;
            continue;
        }
         */
    }
}

/*
void ClientCommThread::startFileTransmission(const char *path)  {
    if(sockfd == -1) return; // Uninitialized Socket

    std::unique_lock<std::mutex> lock(mtx);
    //Todo: use circular buffer :)
    fileTransferRunning = true;
    __android_log_print(ANDROID_LOG_ERROR, "AudioStreamer", "File audio thread started");

    // Initialize mpg123
    mpg123_handle *mpg123 = mpg123_new(nullptr, nullptr);
    if (mpg123_init() != MPG123_OK) {
        std::cerr << "Failed to initialize mpg123" << std::endl;
    }

    // Open the MP3 file
    if (mpg123_open(mpg123, path) != MPG123_OK) {
        std::cerr << "Failed to open file: " << path << std::endl;
        mpg123_delete(mpg123);
        mpg123_exit();
    }
    // Get the format information
    long rate;
    int channels, encoding;
    mpg123_getformat(mpg123, &rate, &channels, &encoding);

    // Set output format to PCM 16-bit
    mpg123_format(mpg123, rate,channels, MPG123_ENC_SIGNED_16);

    //Send Audio head 2 Times To Clients (in case packet lost somewhere)
    if(audioHeadPacketPresent) delete[] audioHeadPacket;
    audioHeadPacketPresent = true;
    audioHeadPacketSize = sizeof('A') + sizeof(rate) + sizeof(channels);
    audioHeadPacket = new unsigned char[audioHeadPacketSize];
    std::memcpy(audioHeadPacket, reinterpret_cast<const void *>('A'), sizeof('A'));
    std::memcpy(audioHeadPacket + sizeof('A'), reinterpret_cast<unsigned char*>(&rate), sizeof(rate));
    std::memcpy(audioHeadPacket + sizeof('A') + sizeof(rate), reinterpret_cast<unsigned char*>(&channels), sizeof(channels));
    sendto(sockfd, audioHeadPacket, audioHeadPacketSize, 0, (struct sockaddr *) &multicastAddr,sizeof(multicastAddr));
    sendto(sockfd, audioHeadPacket, audioHeadPacketSize, 0, (struct sockaddr *) &multicastAddr,sizeof(multicastAddr));
    //Packet formation ends

    // Initialize the buffer
    size_t buffer_size = 1440;
    auto *buffer = (unsigned char*)malloc(buffer_size * sizeof(unsigned char));
    auto *pcm_buffer = new int16_t[buffer_size / 2];  // Each sample is 2 bytes (16-bit)
    size_t bytes_written = 0;

    // Create RTP header
    uint8_t rtpHeader[12];
    rtpHeader[0] = 'M'; //  Version (2 bits) and Padding (0 bit) changed to Payload Type (Signals ke adan Pradan ke lea)
    rtpHeader[1] = 0x60; // Payload Type (7 bits) and Marker (1 bit) changed to Version  (Signals ke adan Pradan ke lea)
    rtpHeader[2] = 0x00; // Sequence Number (16 bits) - Higher byte (initialize to 0)
    rtpHeader[3] = 0x00; // Sequence Number (16 bits) - Lower byte (initialize to 0)
    rtpHeader[4] = 0x00; // Timestamp (32 bits) - First byte
    rtpHeader[5] = 0x00; // Timestamp (32 bits) - Second byte
    rtpHeader[6] = 0x00; // Timestamp (32 bits) - Third byte
    rtpHeader[7] = 0x00; // Timestamp (32 bits) - Fourth byte
    rtpHeader[8] = 0x00; // SSRC (32 bits) - First byte
    rtpHeader[9] = 0x00; // SSRC (32 bits) - Second byte
    rtpHeader[10] = 0x00; // SSRC (32 bits) - Third byte
    rtpHeader[11] = 0x00; // SSRC (32 bits) - Fourth byte

    // Get a pointer to the audio data buffer
    int packetSize = 1440 + sizeof(rtpHeader);
    auto* packet = new uint8_t[packetSize];
    // Start audio transmission loop
    while (fileTransferRunning && mpg123_read(mpg123, buffer, buffer_size, &bytes_written) == MPG123_OK) {

        if (encoding != MPG123_ENC_SIGNED_16) {
            mpg123_enc_enum target_encoding = MPG123_ENC_SIGNED_16;

            mpg123_format_none(mpg123);
            mpg123_format(mpg123, rate, channels, target_encoding);

            size_t decoded_size = bytes_written / (channels * mpg123_encsize(encoding));
            mpg123_decode(mpg123, buffer, bytes_written, reinterpret_cast<unsigned char*>(pcm_buffer), decoded_size * sizeof(int16_t), &bytes_written);
        }

        // Increment sequence number
        rtpHeader[2]++;
        if (rtpHeader[2] == 0x00) {
            rtpHeader[3]++;
        }

        // Combine RTP header and audio data
        memcpy(packet, rtpHeader, sizeof(rtpHeader));
        memcpy(packet + sizeof(rtpHeader), buffer, 1440);

        if(!sentData.enqueue(&packet)) //If Queue is full wait until the buffer drops to desired value.
        {
            cv.wait(lock, [this] { return transmissionStartSignal; });
            transmissionStartSignal = false; //setting to false as it does not blindly continue when it waits next time.I think this statement is not requires as the cv will wait until it is signalled....
        }
        sendto(sockfd, packet, packetSize, 0, (struct sockaddr *) &multicastAddr,
               sizeof(multicastAddr));
        // Sleep for a short duration to control the rate of transmission
        // std::this_thread::sleep_for(std::chrono::milliseconds(7));
    }
    // Clean up resources
    fileTransferRunning = false;
    delete[] packet;
    delete[] buffer;
    delete[] pcm_buffer;
    delete[] audioHeadPacket;
    audioHeadPacketPresent = false;
    mpg123_close(mpg123);
    mpg123_delete(mpg123);
    mpg123_exit();
}
 */

void ClientCommThread::stopFileTransmission() {

}

void ClientCommThread::playAudio() {
    //if(lfQueue.size() > LOW_BUFFER)
        //player1.start();
}

void ClientCommThread::pauseAudio() {
    //player1.pause();
}

function<void()> ClientCommThread::lowBufferReached() {
    // Implement your logic here when the queue size reaches below 4096
    // This function will be called by OboePlayer when the size of the LockFreeQueue is 4096 or greater
    // You can change variable values or perform any desired actions here
    {
        std::lock_guard<std::mutex> lock(mtx);
        transmissionStartSignal = true;
    }
    cv.notify_one();
    return {};
}

void ClientCommThread::stopServer() {
    commThreadRunning = false;

    //SocketInit::del_socket();
    delete[] audioHeadPacket;
}
