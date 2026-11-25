// Desktop tests for the YIN detector: accuracy across the A4-F6 range, rejection of
// silence and noise, and analysis time per window.
#include "PitchDetector.h"
#include "TestTone.h"
#include <chrono>
#include <cstdio>
#include <random>

using namespace ocarina;

static int failures = 0;
#define CHECK(cond, ...) do { if (!(cond)) { ++failures; std::printf("FAIL: " __VA_ARGS__); std::printf("\n"); } } while (0)

static std::vector<float> tone(int sr, double hz, int n, double amp, double noiseAmp, unsigned seed,
                               double vibratoCents = 0, double phase0 = 0.3) {
    std::mt19937 rng(seed);
    std::normal_distribution<double> g(0.0, 1.0);
    std::vector<float> x(n);
    double ph = phase0;
    for (int i = 0; i < n; ++i) {
        double f = hz * std::pow(2.0, vibratoCents * std::sin(2 * M_PI * 5.0 * i / sr) / 1200.0);
        ph += 2 * M_PI * f / sr;
        // Ocarina: strong fundamental, weak 2nd/3rd harmonics, breath noise.
        double v = std::sin(ph) + 0.06 * std::sin(2 * ph + 0.5) + 0.03 * std::sin(3 * ph + 1.1);
        x[i] = (float) (amp * v + noiseAmp * g(rng));
    }
    return x;
}

static double cents(double a, double b) { return 1200.0 * std::log2(a / b); }

int main() {
    const int rates[] = {48000, 44100};
    for (int sr : rates) {
        const int N = 1024;
        PitchDetector det(sr, N);
        std::printf("sr=%d window=%d tau=[%d,%d]\n", sr, N, det.tauMin(), det.tauMax());

        double worst = 0;
        // Every semitone A4..F6 plus +/-30 and +/-47 cent offsets.
        for (int midi = 69; midi <= 89; ++midi) {
            for (double off : {-47.0, -30.0, 0.0, 13.0, 30.0, 47.0}) {
                double hz = 440.0 * std::pow(2.0, (midi - 69 + off / 100.0) / 12.0);
                for (unsigned seed = 1; seed <= 3; ++seed) {
                    auto x = tone(sr, hz, N, 0.3, 0.003, seed, 0, 0.1 * seed);
                    auto r = det.analyze(x.data());
                    CHECK(!std::isnan(r.hz), "no pitch for %.2f Hz at %d", hz, sr);
                    if (!std::isnan(r.hz)) {
                        double e = std::fabs(cents(r.hz, hz));
                        worst = std::max(worst, e);
                        CHECK(e < 1.0, "%.2f Hz -> %.2f Hz (%.2f cents) at %d", hz, r.hz, e, sr);
                    }
                    CHECK(r.clarity > 0.9, "low clarity %.3f at %.1f Hz", r.clarity, hz);
                }
            }
        }
        std::printf("  worst error over A4..F6 (clean-ish tone): %.3f cents\n", worst);

        // Noisy tone (SNR about 20 dB) should still be within a few cents.
        double worstNoisy = 0;
        for (int midi = 69; midi <= 89; midi += 2) {
            double hz = 440.0 * std::pow(2.0, (midi - 69) / 12.0);
            auto x = tone(sr, hz, N, 0.3, 0.02, 7);
            auto r = det.analyze(x.data());
            CHECK(!std::isnan(r.hz), "noisy: no pitch for %.1f", hz);
            if (!std::isnan(r.hz)) worstNoisy = std::max(worstNoisy, std::fabs(cents(r.hz, hz)));
        }
        std::printf("  worst error, 20 dB SNR: %.3f cents\n", worstNoisy);
        CHECK(worstNoisy < 4.0, "noisy error too large: %.2f", worstNoisy);

        // Silence and white noise must not produce a pitch.
        std::vector<float> silence(N, 0.f);
        CHECK(std::isnan(det.analyze(silence.data()).hz), "silence produced a pitch");
        auto noise = tone(sr, 1000, N, 0.0, 0.1, 99);
        auto rn = det.analyze(noise.data());
        CHECK(std::isnan(rn.hz), "white noise produced %.1f Hz (clarity %.2f)", rn.hz, rn.clarity);

        // Out-of-band tones must not be reported as an in-band note.
        for (double hz : {220.0, 300.0, 2000.0}) {
            auto x = tone(sr, hz, N, 0.3, 0.001, 3);
            auto r = det.analyze(x.data());
            bool bad = !std::isnan(r.hz) && std::fabs(cents(r.hz, hz)) > 50 &&
                       std::fabs(cents(r.hz, hz * 2)) > 50;  // octave-up of a low tone is acceptable-ish
            CHECK(!bad, "out-of-band %.0f Hz reported as %.1f Hz", hz, r.hz);
            std::printf("  out-of-band %.0f Hz -> %s%.1f\n", hz, std::isnan(r.hz) ? "none " : "", r.hz);
        }

        // Timing.
        auto x = tone(sr, 523.25, N, 0.3, 0.003, 1);
        const int iters = 20000;
        volatile float sink = 0;
        auto t0 = std::chrono::steady_clock::now();
        for (int i = 0; i < iters; ++i) sink += det.analyze(x.data()).hz;
        auto t1 = std::chrono::steady_clock::now();
        double us = std::chrono::duration<double, std::micro>(t1 - t0).count() / iters;
        std::printf("  analysis time: %.1f us per window (desktop x86)\n", us);
    }

    // The debug test tone (used on the emulator) must read as the note it plays, at the level
    // it was asked for, and stay silent when off.
    {
        const int sr = 48000, N = 1024;
        PitchDetector det(sr, N);
        TestTone tt;
        std::vector<float> buf(N);
        double worstTone = 0, worstDb = 0;
        for (int midi = 69; midi <= 89; ++midi) {
            for (double db : {-20.0, -45.0}) {
                double hz = 440.0 * std::pow(2.0, (midi - 69) / 12.0);
                tt.set((float) hz, (float) db);
                tt.render(buf.data(), N, sr);  // warm-up block, then analyse the next one
                tt.render(buf.data(), N, sr);
                auto r = det.analyze(buf.data());
                CHECK(!std::isnan(r.hz), "test tone: no pitch for %.1f Hz", hz);
                if (!std::isnan(r.hz)) worstTone = std::max(worstTone, std::fabs(cents(r.hz, hz)));
                worstDb = std::max(worstDb, std::fabs(r.rmsDb - db));
            }
        }
        std::printf("test tone: worst %.3f cents, level within %.2f dB\n", worstTone, worstDb);
        CHECK(worstTone < 1.0, "test tone pitch error %.2f cents", worstTone);
        CHECK(worstDb < 0.5, "test tone level error %.2f dB", worstDb);
        tt.set(0, -20);
        CHECK(!tt.active(), "test tone still active after off");
    }

    // Median3 behaviour.
    Median3 m;
    CHECK(m.push(1.f) == 1.f, "median pass-through 1");
    CHECK(m.push(5.f) == 5.f, "median pass-through 2");
    CHECK(m.push(2.f) == 2.f, "median of 1,5,2");
    CHECK(m.push(100.f) == 5.f, "median of 5,2,100");
    CHECK(std::isnan(m.push(NAN)), "median NaN resets");
    CHECK(m.push(7.f) == 7.f, "median after reset");

    std::printf(failures ? "\n%d FAILURES\n" : "\nALL DSP TESTS PASSED\n", failures);
    return failures ? 1 : 0;
}
