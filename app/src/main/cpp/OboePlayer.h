//
// Created by Acer on 28-Jun-23.
//
#include "include/Oboe.h"
#include "include/AudioStreamBuilder.h"
#include "include/AudioStreamCallback.h"
#include "include/Definitions.h"
#include "include/AudioStream.h"
#include "include/AudioStreamBase.h"
#include "include/FifoBuffer.h"
#include "include/FifoControllerBase.h"
#include "include/LatencyTuner.h"
#include "include/OboeExtensions.h"
#include "include/ResultWithValue.h"
#include "include/StabilizedCallback.h"
#include "include/Utilities.h"
#include "include/Version.h"
#include "LockFreeQueue.h"
#include "Constants.h"

#ifndef SONOR_STREAM_OBOEPLAYER_H
#define SONOR_STREAM_OBOEPLAYER_H

static LockFreeQueue<short,PLAYER_BUFFER> lfQueue;

class OboePlayer : public oboe::AudioStreamCallback {
public:
    OboePlayer();
    void start();
    void stop();
    void pause();
    void setChannel(int ch);
    void setRate(long rate);
    void setLowBufferCallback(std::function<void()> callback);  // Set the callback function

private:
    oboe::AudioStream* mStream;
    short* mBuffer;
    int mBufferSize;
    int mWriteIndex;
    int channel = 1;
    oboe::AudioStreamBuilder builder;
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* audioStream, void* audioData, int32_t numFrames) override;
    std::function<void()> lbc;  // Callback function to be called
};

OboePlayer::OboePlayer() : mStream(nullptr), mBuffer(nullptr), mBufferSize(0), mWriteIndex(0) {

    builder.setFormat(oboe::AudioFormat::I16);
    builder.setCallback(this);
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setChannelCount(oboe::ChannelCount::Mono);
    builder.setSampleRate(44100);
    //builder.setContentType(oboe::ContentType::Music);
    oboe::Result result = builder.openStream(&mStream);
    if (result != oboe::Result::OK) {
        // Handle error opening the audio stream
    }
}

void OboePlayer::start() {
    oboe::Result result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        // Handle error starting the audio stream
    }
}

void OboePlayer::stop() {
    oboe::Result result = mStream->requestStop();
    if (result != oboe::Result::OK) {
        // Handle error stopping the audio stream
    }
    mStream->close();
}

oboe::DataCallbackResult OboePlayer::onAudioReady(oboe::AudioStream* audioStream, void* audioData, int32_t numFrames) {

    int16_t* outputBuffer = static_cast<int16_t*>(audioData);
    int totalSamples = numFrames * channel;
    for (int i = 0; i < totalSamples; ++i) {
        bool popped = lfQueue.pop(outputBuffer[i]);
        if (!popped) {
            outputBuffer[i] = 0x00;
        }
    }
    return oboe::DataCallbackResult::Continue;
}

void OboePlayer::setChannel(int ch) {
    channel = ch;
    builder.setChannelCount(channel);
}

void OboePlayer::setRate(long rate) {
    builder.setSampleRate(rate);
}

void OboePlayer::pause() {
    oboe::Result result = mStream->requestPause();
    if (result != oboe::Result::OK) {
        // Handle error pausing the audio stream
    }
}

void OboePlayer::setLowBufferCallback(std::function<void()> callback) {
    lbc = callback; // Set the callback function
}

#endif //SONOR_STREAM_OBOEPLAYER_H
