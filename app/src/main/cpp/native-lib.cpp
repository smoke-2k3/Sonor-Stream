#include <cmath>
#include <jni.h>

extern "C"
JNIEXPORT void JNICALL
Java_com_classic_sonorstream_StreamService_stopAudioCapture(JNIEnv *env, jobject thiz) {
    printf("audio capture stopped");
}