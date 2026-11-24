// JNI surface for app.ocarinatuner.audio.NativeEngine. All calls are allocation-free on the
// Java side: the UI passes preallocated arrays every frame.
#include <jni.h>
#include <memory>
#include "PitchEngine.h"

using ocarina::PitchEngine;
using ocarina::PitchFrame;

static PitchEngine *engine() {
    static PitchEngine *e = new PitchEngine();  // lives for the process
    return e;
}

static constexpr int kMaxDrain = 1024;
static PitchFrame gScratch[kMaxDrain];  // only touched from the UI thread

extern "C" {

JNIEXPORT jint JNICALL
Java_app_ocarinatuner_audio_NativeEngine_nativeStart(JNIEnv *, jobject) {
    return engine()->start();
}

JNIEXPORT void JNICALL
Java_app_ocarinatuner_audio_NativeEngine_nativeStop(JNIEnv *, jobject) {
    engine()->stop();
}

JNIEXPORT void JNICALL
Java_app_ocarinatuner_audio_NativeEngine_nativeSetGate(JNIEnv *, jobject, jfloat db, jfloat clarity) {
    engine()->setGate(db, clarity);
}

// Copies up to times.length new frames. Returns how many were written.
JNIEXPORT jint JNICALL
Java_app_ocarinatuner_audio_NativeEngine_nativeDrain(JNIEnv *env, jobject, jlongArray times,
                                                     jfloatArray hz, jfloatArray clarity,
                                                     jfloatArray rms) {
    int cap = env->GetArrayLength(times);
    if (cap > kMaxDrain) cap = kMaxDrain;
    const int n = engine()->drain(gScratch, cap);
    if (n <= 0) return 0;
    jlong t[kMaxDrain];
    jfloat h[kMaxDrain], c[kMaxDrain], r[kMaxDrain];
    for (int i = 0; i < n; ++i) {
        t[i] = gScratch[i].timeNs;
        h[i] = gScratch[i].hz;
        c[i] = gScratch[i].clarity;
        r[i] = gScratch[i].rmsDb;
    }
    env->SetLongArrayRegion(times, 0, n, t);
    env->SetFloatArrayRegion(hz, 0, n, h);
    env->SetFloatArrayRegion(clarity, 0, n, c);
    env->SetFloatArrayRegion(rms, 0, n, r);
    return n;
}

JNIEXPORT void JNICALL
Java_app_ocarinatuner_audio_NativeEngine_nativeStats(JNIEnv *env, jobject, jdoubleArray out) {
    double s[ocarina::EngineStats::kCount] = {0};
    engine()->stats(s);
    int n = env->GetArrayLength(out);
    if (n > ocarina::EngineStats::kCount) n = ocarina::EngineStats::kCount;
    env->SetDoubleArrayRegion(out, 0, n, s);
}

}  // extern "C"
