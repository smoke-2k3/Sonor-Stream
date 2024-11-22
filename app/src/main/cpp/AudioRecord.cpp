// This file holds the functions of the AudioRecord class in java.
// source - https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/jni/android_media_AudioRecord.cpp;l=21?q=native_read_in_direct_buffer
// Using this file we can directly feed the read data from the native read function to socket for streaming.
// To be used in future...
// Copy the audioRecord class java code from https://cs.android.com/ and include it in our import.
// it will use AudioRecord.cpp as the native file for functions also include its headers code from https://cs.android.com/