// Time-domain YIN pitch detector tuned for the ocarina range (about 400-1500 Hz).
//
// Header-only and free of Android dependencies so it can be unit tested on a
// desktop (see tests/dsp). No allocation happens after construction, so it is
// safe to call from the real-time audio callback.
#pragma once

#include <cmath>
#include <cstdint>
#include <vector>
#include <algorithm>

namespace ocarina {

struct PitchResult {
    float hz = NAN;        // NAN when no confident pitch
    float clarity = 0.f;   // 1 - min(CMNDF), 1 = perfectly periodic
    float rmsDb = -120.f;  // RMS level of the analysis window in dBFS
};

class PitchDetector {
public:
    // windowSize: total samples looked at per analysis (integration window + max lag).
    PitchDetector(int sampleRate, int windowSize, float minHz = 400.f, float maxHz = 1500.f,
                  float yinThreshold = 0.15f)
        : sampleRate_(sampleRate), windowSize_(windowSize), threshold_(yinThreshold) {
        // Small margins so a note right at the band edges still has a dip on both sides.
        tauMin_ = std::max(2, (int) std::floor(sampleRate / (maxHz * 1.03f)));
        tauMax_ = (int) std::ceil(sampleRate / (minHz * 0.97f)) + 2;
        if (tauMax_ > windowSize_ / 2) tauMax_ = windowSize_ / 2;
        integration_ = windowSize_ - tauMax_;
        diff_.assign(tauMax_ + 2, 0.f);
        cmndf_.assign(tauMax_ + 2, 1.f);
        centered_.assign(windowSize_, 0.f);
    }

    int sampleRate() const { return sampleRate_; }
    int windowSize() const { return windowSize_; }
    int tauMin() const { return tauMin_; }
    int tauMax() const { return tauMax_; }

    // x must point at windowSize() contiguous samples, oldest first.
    PitchResult analyze(const float *x) {
        PitchResult r;
        const int n = windowSize_;

        // Remove DC and measure level in one pass.
        double sum = 0.0;
        for (int i = 0; i < n; ++i) sum += x[i];
        const float mean = (float) (sum / n);
        double energy = 0.0;
        for (int i = 0; i < n; ++i) {
            const float v = x[i] - mean;
            centered_[i] = v;
            energy += (double) v * v;
        }
        const double rms = std::sqrt(energy / n);
        r.rmsDb = rms > 1e-9 ? (float) (20.0 * std::log10(rms)) : -120.f;
        if (rms < 1e-7) return r;

        const float *c = centered_.data();
        const int W = integration_;

        // Difference function d(tau) = sum (x[j] - x[j+tau])^2 for tau in [1, tauMax].
        // O(W * tauMax), about 100k multiply-adds at 48 kHz: tens of microseconds.
        diff_[0] = 0.f;
        for (int tau = 1; tau <= tauMax_; ++tau) {
            const float *a = c;
            const float *b = c + tau;
            float acc0 = 0.f, acc1 = 0.f, acc2 = 0.f, acc3 = 0.f;
            int j = 0;
            for (; j + 3 < W; j += 4) {
                const float d0 = a[j] - b[j];
                const float d1 = a[j + 1] - b[j + 1];
                const float d2 = a[j + 2] - b[j + 2];
                const float d3 = a[j + 3] - b[j + 3];
                acc0 += d0 * d0; acc1 += d1 * d1; acc2 += d2 * d2; acc3 += d3 * d3;
            }
            for (; j < W; ++j) { const float d = a[j] - b[j]; acc0 += d * d; }
            diff_[tau] = acc0 + acc1 + acc2 + acc3;
        }

        // Cumulative mean normalized difference.
        cmndf_[0] = 1.f;
        double running = 0.0;
        for (int tau = 1; tau <= tauMax_; ++tau) {
            running += diff_[tau];
            cmndf_[tau] = running > 0.0 ? (float) (diff_[tau] * tau / running) : 1.f;
        }

        // First dip under the threshold inside the allowed lag band, then walk to its bottom.
        int best = -1;
        for (int tau = tauMin_; tau <= tauMax_; ++tau) {
            if (cmndf_[tau] < threshold_) {
                while (tau + 1 <= tauMax_ && cmndf_[tau + 1] < cmndf_[tau]) ++tau;
                best = tau;
                break;
            }
        }
        if (best < 0) {
            // No confident dip: report the global minimum's clarity but no pitch.
            float m = 1.f;
            for (int tau = tauMin_; tau <= tauMax_; ++tau) m = std::min(m, cmndf_[tau]);
            r.clarity = std::max(0.f, 1.f - m);
            return r;
        }
        // Reject a dip sitting on the edge of the band (pitch is outside the range).
        if (best <= tauMin_ || best >= tauMax_) {
            r.clarity = std::max(0.f, 1.f - cmndf_[best]);
            return r;
        }

        // Octave guard: a tone above the band (e.g. 2 kHz) has its first in-band dip at
        // twice its period. If half the lag is also a deep dip, the real pitch is out of range.
        {
            const int half = best / 2;
            if (half >= 2) {
                float m = cmndf_[half];
                m = std::min(m, cmndf_[half - 1]);
                m = std::min(m, cmndf_[half + 1]);
                if (half < tauMin_ && m < threshold_) {
                    r.clarity = std::max(0.f, 1.f - cmndf_[best]);
                    return r;
                }
            }
        }

        // Parabolic interpolation on the raw difference function (smoother than CMNDF
        // near the minimum, so less bias).
        const float y0 = diff_[best - 1], y1 = diff_[best], y2 = diff_[best + 1];
        const float denom = y0 - 2.f * y1 + y2;
        float shift = 0.f;
        if (std::fabs(denom) > 1e-12f) shift = 0.5f * (y0 - y2) / denom;
        shift = std::clamp(shift, -0.5f, 0.5f);
        const float tauRefined = (float) best + shift;

        r.clarity = std::max(0.f, 1.f - cmndf_[best]);
        r.hz = (float) sampleRate_ / tauRefined;
        return r;
    }

private:
    int sampleRate_;
    int windowSize_;
    float threshold_;
    int tauMin_ = 0, tauMax_ = 0, integration_ = 0;
    std::vector<float> diff_, cmndf_, centered_;
};

// Median of the last three voiced estimates. Resets on unvoiced frames so a new
// note is not dragged by the previous one.
class Median3 {
public:
    float push(float v) {
        if (std::isnan(v)) { count_ = 0; return v; }
        buf_[idx_] = v;
        idx_ = (idx_ + 1) % 3;
        if (count_ < 3) ++count_;
        if (count_ < 3) return v;  // not enough history yet, pass through (no added delay)
        float a = buf_[0], b = buf_[1], c = buf_[2];
        return std::max(std::min(a, b), std::min(std::max(a, b), c));
    }
    void reset() { count_ = 0; }
private:
    float buf_[3] = {0, 0, 0};
    int idx_ = 0, count_ = 0;
};

}  // namespace ocarina
