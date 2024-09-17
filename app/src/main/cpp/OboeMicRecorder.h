//
// Created by Acer on 10-Jul-23.
//
#ifndef SONOR_STREAM_OBOEMICRECORDER_H
#define SONOR_STREAM_OBOEMICRECORDER_H

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
#include "SocketInit.h"
#include <cstdio>
#include <fstream>

using namespace oboe;

class OboeMicRecorder : public oboe::AudioStreamCallback {
public:
    OboeMicRecorder();
    void start_mic_rec();
    void stop_mic_rec();
    static void setOptimalRates(int32_t sampleRate, int32_t framesPerBurst);
    void setOptions();
private:
    int sock_mic = -1;
    SocketInit mic_sock;
    oboe::AudioStreamBuilder builder;
    AudioStream* stream{};
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* audioStream, void* audioData, int32_t numFrames) override;
};

OboeMicRecorder::OboeMicRecorder() {
    builder.setCallback(this);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(SharingMode::Exclusive);
    builder.setDirection(Direction::Input);
    builder.setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium);
    builder.setFormat(oboe::AudioFormat::I16);
    builder.setSampleRate(44100);
    builder.setChannelCount(oboe::ChannelCount::Mono);
}

DataCallbackResult
OboeMicRecorder::onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames)  {
    //static_cast<const char*>(audioData), numFrames * sizeof(float);
    return oboe::DataCallbackResult::Continue;
}

void OboeMicRecorder::start_mic_rec() {
    sock_mic = mic_sock.init_socket(0);

    Result r = builder.openStream(&stream);
    if (r != Result::OK)
    {
        printf("Error Opening Stream %d",r);
        //handle err
    }
    r = stream->requestStart();
    if (r != Result::OK)
    {
        printf("Error Starting Stream %d",r);
        //handle err
    }
}

void OboeMicRecorder::stop_mic_rec() {
    close(sock_mic);
    if (stream) {
        Result res = stream->requestStop();
        if (res != oboe::Result::OK) {
            // Handle error
            printf("Error Stopping Stream %d\n", res);
            return;
        }
        stream->close();
        stream = nullptr;
    }
    printf("Recording stopped\n");
}

void OboeMicRecorder::setOptimalRates(int32_t sampleRate, int32_t framesPerBurst) {
    oboe::DefaultStreamValues::SampleRate = (int32_t) sampleRate;
    oboe::DefaultStreamValues::FramesPerBurst = (int32_t) framesPerBurst;
}

void OboeMicRecorder::setOptions() {

}

#endif //SONOR_STREAM_OBOEMICRECORDER_H
