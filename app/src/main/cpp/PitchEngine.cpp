#include "PitchEngine.h"

#include <android/log.h>
#include <time.h>
#include <cstring>
#include <thread>

#define LOG_TAG "OcarinaEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace ocarina {

static inline int64_t nowNs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t) ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

int PitchEngine::openStream(oboe::InputPreset preset) {
    oboe::AudioStreamBuilder b;
    b.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setInputPreset(preset)
        // No sample rate requested: we take the device's native rate so there is no
        // resampler in the path.
        ->setDataCallback(this)
        ->setErrorCallback(this);
    std::shared_ptr<oboe::AudioStream> s;
    oboe::Result r = b.openStream(s);
    if (r != oboe::Result::OK) return (int) r;
    stream_ = s;
    presetUsed_ = preset;
    return 0;
}

int PitchEngine::start() {
    std::lock_guard<std::mutex> g(lock_);
    wantRunning_.store(true);
    if (stream_) return 0;

    // Unprocessed = no noise suppression / AGC. Fall back to VoiceRecognition, which on
    // most devices also skips those effects.
    int res = openStream(oboe::InputPreset::Unprocessed);
    if (res != 0) {
        LOGW("Unprocessed preset failed (%d), trying VoiceRecognition", res);
        res = openStream(oboe::InputPreset::VoiceRecognition);
    }
    if (res != 0) return res;

    const int sr = stream_->getSampleRate();
    // ~1024 samples at 48 kHz, scaled for other rates so the time span stays ~21 ms.
    window_ = std::max(512, (int) std::lround(1024.0 * sr / 48000.0));
    hop_ = std::max(64, (int) std::lround(128.0 * sr / 48000.0));
    detector_ = std::make_unique<PitchDetector>(sr, window_);
    history_.assign(window_ * 2, 0.f);
    writePos_ = 0;
    sinceAnalysis_ = 0;
    median_.reset();
    ring_.clear();
    analysisMaxUs_.store(0);
    lastCallbackNs_ = 0;

    // Smallest stable buffer: one burst. For input this mostly matters on the legacy path.
    stream_->setBufferSizeInFrames(stream_->getFramesPerBurst());

    oboe::Result r = stream_->requestStart();
    if (r != oboe::Result::OK) {
        stream_->close();
        stream_.reset();
        return (int) r;
    }
    running_.store(true);
    LOGI("Input started: %d Hz, burst %d, api %d, exclusive %d, window %d, hop %d", sr,
         stream_->getFramesPerBurst(), (int) stream_->getAudioApi(),
         stream_->getSharingMode() == oboe::SharingMode::Exclusive, window_, hop_);
    return 0;
}

void PitchEngine::stop() {
    std::lock_guard<std::mutex> g(lock_);
    wantRunning_.store(false);
    if (stream_) {
        stream_->requestStop();
        stream_->close();
        stream_.reset();
    }
    running_.store(false);
}

void PitchEngine::onErrorAfterClose(oboe::AudioStream *, oboe::Result error) {
    // Typically a device change (headset plugged in). Reopen if we still want audio.
    LOGW("Stream closed with error %d, restarting", (int) error);
    {
        std::lock_guard<std::mutex> g(lock_);
        stream_.reset();
        running_.store(false);
    }
    if (wantRunning_.load()) start();
}

oboe::DataCallbackResult PitchEngine::onAudioReady(oboe::AudioStream *stream, void *audioData,
                                                   int32_t numFrames) {
    const int64_t t = nowNs();
    if (lastCallbackNs_ != 0) callbackIntervalMs_.store((t - lastCallbackNs_) / 1e6);
    lastCallbackNs_ = t;
    lastCallbackFrames_.store(numFrames);
    const int64_t n = callbacks_.fetch_add(1) + 1;

    const float *in = static_cast<const float *>(audioData);
    const int w = window_;
    float *h = history_.data();
    for (int i = 0; i < numFrames; ++i) {
        const float v = in[i];
        h[writePos_] = v;
        h[writePos_ + w] = v;
        writePos_ = (writePos_ + 1) % w;
        if (++sinceAnalysis_ >= hop_) {
            sinceAnalysis_ = 0;
            analyzeWindow();
        }
    }

    // Input latency estimate (time from the ADC to this callback), refreshed ~every 100 ms.
    if (n % 32 == 0) {
        auto lat = stream->calculateLatencyMillis();
        if (lat) latencyMs_.store(lat.value());
    }
    return oboe::DataCallbackResult::Continue;
}

void PitchEngine::analyzeWindow() {
    const int64_t t0 = nowNs();
    // writePos_ points at the oldest sample; the window is contiguous in the doubled ring.
    PitchResult r = detector_->analyze(history_.data() + writePos_);

    float hz = r.hz;
    if (r.rmsDb < gateDb_.load(std::memory_order_relaxed) ||
        r.clarity < gateClarity_.load(std::memory_order_relaxed)) {
        hz = NAN;
    }
    hz = median_.push(hz);

    const int64_t t1 = nowNs();
    const double us = (t1 - t0) / 1000.0;
    // Exponential moving average and running max of analysis cost.
    analysisAvgUs_.store(analysisAvgUs_.load(std::memory_order_relaxed) * 0.98 + us * 0.02,
                         std::memory_order_relaxed);
    if (us > analysisMaxUs_.load(std::memory_order_relaxed)) analysisMaxUs_.store(us);

    if (!ring_.push(PitchFrame{t1, hz, r.clarity, r.rmsDb})) dropped_.fetch_add(1);
}

void PitchEngine::stats(double *o) {
    std::lock_guard<std::mutex> g(lock_);
    auto s = stream_;
    o[0] = s ? s->getSampleRate() : 0;
    o[1] = s ? s->getFramesPerBurst() : 0;
    o[2] = s ? s->getBufferSizeInFrames() : 0;
    o[3] = s ? s->getBufferCapacityInFrames() : 0;
    o[4] = latencyMs_.load();
    o[5] = analysisAvgUs_.load();
    o[6] = analysisMaxUs_.load();
    o[7] = lastCallbackFrames_.load();
    o[8] = (double) callbacks_.load();
    auto x = s ? s->getXRunCount() : oboe::ResultWithValue<int32_t>(0);
    o[9] = x ? x.value() : -1;
    o[10] = (double) dropped_.load();
    o[11] = hop_;
    o[12] = window_;
    o[13] = s ? (int) s->getAudioApi() : 0;  // 1 = OpenSL ES, 2 = AAudio
    o[14] = (int) presetUsed_;
    o[15] = s && s->getSharingMode() == oboe::SharingMode::Exclusive ? 1 : 0;
    o[16] = callbackIntervalMs_.load();
}

}  // namespace ocarina
