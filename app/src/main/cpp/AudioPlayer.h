#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <SLES/OpenSLES_AndroidConfiguration.h>

#ifndef SONOR_STREAM_AUDIOPLAYER_H
#define SONOR_STREAM_AUDIOPLAYER_H

static SLAndroidSimpleBufferQueueItf playerBufferQueue{};

class AudioPlayer {
public:
    AudioPlayer();
    ~AudioPlayer();
    void start();
    void stop();

private:
    SLObjectItf engineObject{};
    SLEngineItf engineEngine{};
    SLObjectItf outputMixObject{};
    SLObjectItf playerObject{};
    SLPlayItf playerPlay{};

    static void playerCallback(SLAndroidSimpleBufferQueueItf bufferQueue, void* context);
};

AudioPlayer::AudioPlayer() {
    // Create the OpenSL ES engine object
    slCreateEngine(&engineObject, 0, nullptr, 0, nullptr, nullptr);
    (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);

    // Create the output mix object
    const SLInterfaceID ids[1] = { SL_IID_ENVIRONMENTALREVERB };
    const SLboolean req[1] = { SL_BOOLEAN_FALSE };
    (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 1, ids, req);
    (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);

    // Set up the audio player
    SLDataLocator_AndroidSimpleBufferQueue loc_bufq = { SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2 };
    SLDataFormat_PCM format_pcm = { SL_DATAFORMAT_PCM, 1, SL_SAMPLINGRATE_44_1,
                                    SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
                                    SL_SPEAKER_FRONT_CENTER, SL_BYTEORDER_LITTLEENDIAN };
    SLDataSource audioSrc = { &loc_bufq, &format_pcm };

    SLDataLocator_OutputMix loc_outmix = { SL_DATALOCATOR_OUTPUTMIX, outputMixObject };
    SLDataSink audioSnk = { &loc_outmix, nullptr };

    const SLInterfaceID playerIDs[1] = { SL_IID_BUFFERQUEUE };
    const SLboolean playerReq[1] = { SL_BOOLEAN_TRUE };
    (*engineEngine)->CreateAudioPlayer(engineEngine, &playerObject, &audioSrc, &audioSnk, 1, playerIDs, playerReq);
    (*playerObject)->Realize(playerObject, SL_BOOLEAN_FALSE);
    (*playerObject)->GetInterface(playerObject, SL_IID_PLAY, &playerPlay);
    (*playerObject)->GetInterface(playerObject, SL_IID_BUFFERQUEUE, &playerBufferQueue);
    (*playerBufferQueue)->RegisterCallback(playerBufferQueue, playerCallback, this);
}
AudioPlayer::~AudioPlayer() {
    // Destroy audio player and associated objects
    (*playerObject)->Destroy(playerObject);
    (*outputMixObject)->Destroy(outputMixObject);
    (*engineObject)->Destroy(engineObject);
}
void AudioPlayer::start() {
    (*playerPlay)->SetPlayState(playerPlay, SL_PLAYSTATE_PLAYING);
}
void AudioPlayer::stop() {
    (*playerPlay)->SetPlayState(playerPlay, SL_PLAYSTATE_STOPPED);
}
void AudioPlayer::playerCallback(SLAndroidSimpleBufferQueueItf bufferQueue, void* context) {
    // This function is called when the audio buffer needs to be refilled
    // auto *player = static_cast<AudioPlayer *>(context);
    // Implement your logic here to refill the buffer if necessary
}

#endif //SONOR_STREAM_AUDIOPLAYER_H