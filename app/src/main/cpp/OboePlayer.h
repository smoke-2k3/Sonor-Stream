//
// Created by Acer on 21-Nov-24.
//

#ifndef SONOR_STREAM_OBOEPLAYER_H
#define SONOR_STREAM_OBOEPLAYER_H

#include <oboe/Oboe.h>
#include <utility>
#include "LockFreeQueue.h"
#include "Constants.h"
#include "OboePlayer.h"

class OboePlayer : public oboe::AudioStreamCallback {
public:
    OboePlayer();
    void start();
    void stop();
    void pause();
    LockFreeQueue<short,PLAYER_BUFFER> lfQueue;
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

#endif //SONOR_STREAM_OBOEPLAYER_H
