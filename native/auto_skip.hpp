#pragma once

// Skip Engine: tells the frontend when to run at maximum speed (like fast forward, sound muted) through loading screens,
// transitions and save waits, and when a faster disc read speed is safe (not while the game streams video or music
// from the disc). Console-independent: the app reports what its core did (disc data read or sought, backup memory
// accesses), every delivered frame and every sound sample.
//
// Detection
//  - Picture: 32x24 sampled pixels per delivered frame. Black = 98% of them dark; still = at most 2% changed since the
//    previous frame (small animations such as a spinning icon or blinking text still count as still).
//  - Sound: the loudest sample since the last emulated frame; quiet = below about -35 dB.
//  - Loads: disc activity in the last half second, a still or black picture and silence (videos and streamed music
//    have a moving picture or sound, so they play normally).
//  - Transitions: 3+ black frames in a row and silence.
//  - Save waits: bulk backup memory access (64+ accesses in one frame) in the last half second.
// The skip starts after 3 frames meeting a condition and stays on 8 frames after the last one (no flicker).
// Disc streaming (activity + moving picture + sound for 10 frames) asks for the original read speed; 1 s without it
// allows the faster one again (like DuckStation, which turns its CD read speedup off for XA/CD audio and video).

#include <array>
#include <atomic>
#include <cstdint>

namespace gb {

class AutoSkip {
public:
    // Options (any thread)
    std::atomic<bool> skipLoads{false}, skipTransitions{false}, skipSaves{false};

    // Emulation thread: every delivered frame (32-bit pixels, any channel order)
    void OnFrame(const uint32_t *fb, uint32_t width, uint32_t height);
    // Sound thread: every output sample (before muting it while skipping)
    void OnSample(int16_t l, int16_t r) {
        const int peak = (l < 0 ? -l : l) > (r < 0 ? -r : r) ? (l < 0 ? -l : l) : (r < 0 ? -r : r);
        if (peak > m_audioPeak.load(std::memory_order_relaxed)) {
            m_audioPeak.store(peak, std::memory_order_relaxed);
        }
    }
    // Emulation thread, once per emulated frame: whether the disc read or sought data this frame, and how many times
    // the backup memory was accessed this frame
    void Update(bool discActivity, uint32_t saveAccesses);

    // Run at maximum speed now (any thread)
    bool Active() const { return m_active.load(std::memory_order_relaxed); }
    // A faster disc read speed is fine now (false while the game streams from the disc); emulation thread
    bool FastDiscOk() const { return m_fastDiscOk; }
    // Frames run at maximum speed since the last call (performance log); emulation thread
    uint32_t TakeActiveFrames() {
        const uint32_t n = m_activeFrames;
        m_activeFrames = 0;
        return n;
    }
    void Reset();

private:
    static constexpr uint32_t kGX = 32, kGY = 24;
    std::array<uint32_t, kGX * kGY> m_sig{};
    bool m_sigValid = false, m_still = false, m_black = false;
    int m_blackRun = 0;
    std::atomic<int> m_audioPeak{0};
    int m_discRecent = 0, m_saveRecent = 0;
    int m_wantRun = 0, m_holdRun = 0;
    int m_streamRun = 0, m_calmRun = 0;
    bool m_fastDiscOk = true;
    uint32_t m_activeFrames = 0;
    std::atomic<bool> m_active{false};
};

} // namespace gb
