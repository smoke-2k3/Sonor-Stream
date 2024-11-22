//
// Created by Acer on 28-Jun-23.
//
#include "OboePlayer.h"

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

    auto* outputBuffer = static_cast<int16_t*>(audioData);
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
    lbc = std::move(callback); // Set the callback function
}
