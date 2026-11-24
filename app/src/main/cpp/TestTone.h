// Synthetic input for testing without a microphone (emulator, desktop tests). Only debug
// builds turn it on, from a broadcast (see MainActivity). While on, it replaces the mic
// samples in the audio callback, so the whole path from analysis to screen is exercised.
#pragma once

#include <atomic>
#include <cmath>
#include <cstdint>

namespace ocarina {

class TestTone {
public:
    // hz <= 0 turns the tone off. dbfs is the RMS level of the fundamental.
    void set(float hz, float dbfs) {
        amp_.store(hz > 0 ? std::sqrt(2.f) * std::pow(10.f, dbfs / 20.f) : 0.f, std::memory_order_relaxed);
        hz_.store(hz > 0 ? hz : 0.f, std::memory_order_release);
    }

    bool active() const { return hz_.load(std::memory_order_relaxed) > 0; }

    // Overwrites n samples. Audio thread only.
    void render(float *out, int n, int sampleRate) {
        const float hz = hz_.load(std::memory_order_acquire);
        const float amp = amp_.load(std::memory_order_relaxed);
        const double inc = (double) hz / sampleRate;
        for (int i = 0; i < n; ++i) {
            const double p = 6.283185307179586 * phase_;  // 2 pi
            // Ocarina-like: strong fundamental, weak 2nd/3rd harmonics, a little breath noise.
            const double v = std::sin(p) + 0.06 * std::sin(2 * p + 0.5) + 0.03 * std::sin(3 * p + 1.1);
            out[i] = (float) (amp * v) + amp * 0.01f * noise();
            phase_ += inc;
            if (phase_ >= 1.0) phase_ -= 1.0;
        }
    }

private:
    // Uniform in [-1, 1). A tiny LCG: no allocation, no locks.
    float noise() {
        seed_ = seed_ * 1664525u + 1013904223u;
        return (float) (int32_t) seed_ * (1.0f / 2147483648.0f);
    }

    std::atomic<float> hz_{0.f};
    std::atomic<float> amp_{0.f};
    double phase_ = 0.0;
    uint32_t seed_ = 12345u;
};

}  // namespace ocarina
