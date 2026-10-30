#include "auto_skip.hpp"

#include <algorithm>
#include <cstdlib>

namespace gb {

void AutoSkip::OnFrame(const uint32_t *fb, uint32_t width, uint32_t height, uint32_t stridePixels) {
    if (!fb || width == 0 || height == 0) {
        return;
    }
    if (stridePixels == 0) stridePixels = width;
    std::lock_guard<std::mutex> lock(m_pictureMutex);
    uint32_t dark = 0, changed = 0;
    for (uint32_t y = 0; y < kGY; ++y) {
        const uint32_t sy = ((2 * y + 1) * height) / (2 * kGY);
        for (uint32_t x = 0; x < kGX; ++x) {
            const uint32_t sx = ((2 * x + 1) * width) / (2 * kGX);
            const uint32_t px = fb[(size_t)sy * stridePixels + sx];
            const int c0 = px & 0xFF, c1 = (px >> 8) & 0xFF, c2 = (px >> 16) & 0xFF;
            if (c0 < 24 && c1 < 24 && c2 < 24) ++dark;
            uint32_t &old = m_sig[y * kGX + x];
            if (std::abs(c0 - (int)(old & 0xFF)) > 16 || std::abs(c1 - (int)((old >> 8) & 0xFF)) > 16 ||
                std::abs(c2 - (int)((old >> 16) & 0xFF)) > 16) {
                ++changed;
            }
            old = px;
        }
    }
    m_black = dark >= kGX * kGY * 98 / 100;
    m_still = m_sigValid && changed <= kGX * kGY * 2 / 100;
    m_sigValid = true;
    m_blackRun = m_black ? m_blackRun + 1 : 0;
}

void AutoSkip::Update(bool discActivity, uint32_t saveAccesses) {
    std::lock_guard<std::mutex> lock(m_pictureMutex);
    m_discRecent = discActivity ? 30 : std::max(0, m_discRecent - 1);
    m_saveRecent = saveAccesses >= 64 ? 30 : std::max(0, m_saveRecent - 1);
    const bool quiet = m_audioPeak.exchange(0, std::memory_order_relaxed) < 600; // about -35 dB

    // Disc streaming (video, music read from the disc): the original read speed
    const bool streaming = m_discRecent > 0 && !quiet;
    m_streamRun = streaming ? m_streamRun + 1 : 0;
    m_calmRun = streaming ? 0 : m_calmRun + 1;
    if (m_streamRun >= 10) {
        m_fastDiscOk = false;
    } else if (m_calmRun >= 60) {
        m_fastDiscOk = true;
    }

    uint32_t reason = None;
    if (skipLoads.load(std::memory_order_relaxed) && m_discRecent > 0 && quiet && (m_still || m_black)) {
        reason |= Loading;
    }
    if (skipTransitions.load(std::memory_order_relaxed) && m_blackRun >= 3 && quiet) {
        reason |= Transition;
    }
    if (skipSaves.load(std::memory_order_relaxed) && m_saveRecent > 0 && quiet && (m_still || m_black)) {
        reason |= SaveWait;
    }
    const bool want = m_sigValid && reason != None;
    m_wantRun = want ? m_wantRun + 1 : 0;
    if (m_wantRun >= 3) {
        m_holdRun = 8;
    } else if (m_holdRun > 0) {
        --m_holdRun;
    }
    // Do not carry fast-forward into gameplay/video/audio, including games that save in the background.
    if (!m_sigValid || !quiet || (!m_still && !m_black) ||
        !(skipLoads.load(std::memory_order_relaxed) || skipTransitions.load(std::memory_order_relaxed) || skipSaves.load(std::memory_order_relaxed))) {
        m_holdRun = m_wantRun = 0;
    }
    const bool on = m_holdRun > 0;
    if (!on) m_reason.store(None, std::memory_order_relaxed);
    else if (want) m_reason.store(reason, std::memory_order_relaxed);
    if (on) {
        ++m_activeFrames;
    }
    m_active.store(on, std::memory_order_relaxed);
}

void AutoSkip::Reset() {
    std::lock_guard<std::mutex> lock(m_pictureMutex);
    m_sigValid = m_still = m_black = false;
    m_blackRun = m_discRecent = m_saveRecent = m_wantRun = m_holdRun = m_streamRun = m_calmRun = 0;
    m_fastDiscOk = true;
    m_activeFrames = 0;
    m_audioPeak.store(0, std::memory_order_relaxed);
    m_active.store(false, std::memory_order_relaxed);
    m_reason.store(None, std::memory_order_relaxed);
}

} // namespace gb
