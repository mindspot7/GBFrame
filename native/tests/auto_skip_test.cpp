#include "auto_skip.hpp"
#include <algorithm>
#include <array>
#include <cassert>
#include <cstdio>
#include <thread>

using Picture = std::array<uint32_t, 32 * 24>;
static Picture image(uint32_t colour) { Picture p; p.fill(colour); return p; }
static void tick(gb::AutoSkip& engine, const Picture& p, bool disc = false, uint32_t storage = 0, int16_t audio = 0) {
    engine.OnFrame(p.data(), 32, 24); engine.OnSample(audio, audio); engine.Update(disc, storage);
}
int main() {
    const auto black = image(0), still = image(0x00705090);
    int passed = 0;
    {
        gb::AutoSkip e; for (int i = 0; i < 90; ++i) tick(e, black, true, 512); assert(!e.Active()); ++passed;
    }
    {
        gb::AutoSkip e; e.skipLoads = true;
        for (int i = 0; i < 8; ++i) tick(e, still, true);
        assert(e.Active() && (e.ActiveReason() & gb::AutoSkip::Loading)); ++passed;
        tick(e, still, true, 0, 4000); assert(!e.Active()); ++passed;
    }
    {
        gb::AutoSkip e; e.skipLoads = true;
        for (int i = 0; i < 20; ++i) tick(e, black);
        assert(!e.Active()); ++passed;
    }
    {
        gb::AutoSkip e; e.skipTransitions = true;
        for (int i = 0; i < 8; ++i) tick(e, black);
        assert(e.Active() && e.ActiveReason() == gb::AutoSkip::Transition); ++passed;
        tick(e, still); assert(!e.Active()); ++passed;
    }
    {
        gb::AutoSkip e; e.skipSaves = true;
        for (int i = 0; i < 8; ++i) tick(e, still, false, 512);
        assert(e.Active() && e.ActiveReason() == gb::AutoSkip::SaveWait); ++passed;
        tick(e, still, false, 512, 2000); assert(!e.Active()); ++passed;
    }
    {
        gb::AutoSkip e; e.skipSaves = e.skipLoads = e.skipTransitions = true;
        for (int i = 0; i < 30; ++i) tick(e, image(i % 2 ? 0x00dddddd : 0x00505050), true, 512);
        assert(!e.Active()); ++passed;
    }
    {
        gb::AutoSkip e; e.skipTransitions = true;
        for (int i = 0; i < 8; ++i) tick(e, black);
        e.skipTransitions = false; tick(e, black); assert(!e.Active()); ++passed;
    }
    {
        gb::AutoSkip e; e.skipLoads = e.skipSaves = true;
        for (int i = 0; i < 60; ++i) e.Update(true, 512);
        assert(!e.Active()); ++passed;
    }
    {
        gb::AutoSkip e;
        for (int i = 0; i < 12; ++i) tick(e, still, true, 0, 4000);
        assert(!e.FastDiscOk()); ++passed;
        for (int i = 0; i < 65; ++i) tick(e, still);
        assert(e.FastDiscOk()); ++passed;
    }
    {
        gb::AutoSkip e; e.skipLoads = true;
        for (int i = 0; i < 12; ++i) {
            auto p = still; p[i] = 0x00ffffff; tick(e, p, true);
        }
        assert(e.Active()); ++passed;
        assert(e.TakeActiveFrames() > 0 && e.TakeActiveFrames() == 0); ++passed;
        e.Reset(); assert(!e.Active() && e.ActiveReason() == 0 && e.FastDiscOk() && e.TakeActiveFrames() == 0); ++passed;
    }
    {
        gb::AutoSkip e; e.skipTransitions = true;
        std::thread render([&] { for (int i = 0; i < 2000; ++i) e.OnFrame(black.data(), 32, 24); });
        std::thread audio([&] { for (int i = 0; i < 10000; ++i) e.OnSample(1200, -1200); });
        for (int i = 0; i < 2000; ++i) { e.Update(false, 0); if (i % 50 == 0) e.Reset(); }
        render.join(); audio.join(); e.Reset(); assert(!e.Active()); ++passed;
    }
    std::printf("AutoSkip: %d checks passed\n", passed);
}
