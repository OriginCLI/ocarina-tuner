// Low-latency microphone input (Oboe / AAudio) plus pitch analysis.
//
// Threading:
//  * The Oboe data callback runs on a high-priority (SCHED_FIFO when allowed) audio thread.
//    Pitch analysis runs right there, every `hop` samples: about 20-60 us per window, far
//    inside a burst period, and it avoids the wake-up delay of handing samples to another
//    thread.
//  * Results go into a lock-free single-producer/single-consumer ring. The UI thread drains
//    it once per display frame.
#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>
#include "PitchDetector.h"

namespace ocarina {

struct PitchFrame {
    int64_t timeNs;   // CLOCK_MONOTONIC (same clock as System.nanoTime()) when analysis finished
    float hz;         // NAN when unvoiced or gated
    float clarity;
    float rmsDb;
};

class FrameRing {
public:
    static constexpr int kCapacity = 4096;  // ~10 s at 375 frames/s
    bool push(const PitchFrame &f) {
        const uint32_t w = write_.load(std::memory_order_relaxed);
        const uint32_t r = read_.load(std::memory_order_acquire);
        if (w - r >= kCapacity) return false;  // full: UI isn't draining (e.g. backgrounded)
        buf_[w % kCapacity] = f;
        write_.store(w + 1, std::memory_order_release);
        return true;
    }
    int pop(PitchFrame *out, int max) {
        const uint32_t r = read_.load(std::memory_order_relaxed);
        const uint32_t w = write_.load(std::memory_order_acquire);
        int n = (int) std::min<uint32_t>(w - r, (uint32_t) max);
        for (int i = 0; i < n; ++i) out[i] = buf_[(r + i) % kCapacity];
        read_.store(r + n, std::memory_order_release);
        return n;
    }
    void clear() { read_.store(write_.load(std::memory_order_acquire), std::memory_order_release); }
private:
    PitchFrame buf_[kCapacity];
    std::atomic<uint32_t> write_{0};
    std::atomic<uint32_t> read_{0};
};

struct EngineStats {
    double sampleRate, framesPerBurst, bufferSize, bufferCapacity;
    double inputLatencyMs, analysisAvgUs, analysisMaxUs, callbackFrames;
    double callbacks, xruns, dropped, hop, window, audioApi, inputPreset, exclusive;
    double callbackIntervalMs;
    static constexpr int kCount = 17;
};

class PitchEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    PitchEngine() = default;
    ~PitchEngine() override { stop(); }

    // Returns 0 on success, otherwise an oboe::Result value.
    int start();
    void stop();
    bool isRunning() const { return running_.load(); }

    void setGate(float minRmsDb, float minClarity) {
        gateDb_.store(minRmsDb);
        gateClarity_.store(minClarity);
    }

    int drain(PitchFrame *out, int max) { return ring_.pop(out, max); }
    void stats(double *out);

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData,
                                          int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    int openStream(oboe::InputPreset preset);
    void analyzeWindow();

    std::mutex lock_;  // guards open/close only, never taken on the audio thread
    std::shared_ptr<oboe::AudioStream> stream_;
    std::unique_ptr<PitchDetector> detector_;
    Median3 median_;
    FrameRing ring_;

    // Double-written ring so the latest `window_` samples are always contiguous.
    std::vector<float> history_;
    int window_ = 1024;
    int hop_ = 128;
    int writePos_ = 0;
    int sinceAnalysis_ = 0;
    oboe::InputPreset presetUsed_ = oboe::InputPreset::Unprocessed;

    std::atomic<bool> running_{false};
    std::atomic<bool> wantRunning_{false};
    std::atomic<float> gateDb_{-50.f};
    std::atomic<float> gateClarity_{0.85f};

    // Stats (written on the audio thread, read by the UI; relaxed is fine for display).
    std::atomic<double> latencyMs_{-1};
    std::atomic<double> analysisAvgUs_{0};
    std::atomic<double> analysisMaxUs_{0};
    std::atomic<int64_t> callbacks_{0};
    std::atomic<int64_t> dropped_{0};
    std::atomic<int32_t> lastCallbackFrames_{0};
    std::atomic<double> callbackIntervalMs_{0};
    int64_t lastCallbackNs_ = 0;
    int64_t maxWindowStartNs_ = 0;
};

}  // namespace ocarina
