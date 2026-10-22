#pragma once

#include <string>
#include <vector>
#include <cstdint>

// CPU placement and Android performance hints for the emulation threads.
namespace cputune {
// Keeps an Android performance hint session (Android 13+) for the emulation thread. PinEmulationThreads is cheap: call it
// again from time to time (and right after threading options change) to place recreated helper threads.
// Helper threads by name prefix: `fast` ones go on the next fast core(s) (e.g. sound, the heaviest render thread),
// `other` ones on any core except the emulation thread's. Call once before PinEmulationThreads.
void SetThreadRoles(std::vector<std::string> fast, std::vector<std::string> other);
// Pins the calling (emulation) thread alone on the fastest core and places the helper threads.
void PinEmulationThreads();
// Number of fast (big/prime) cores: within 60% of the fastest core's capacity
int FastCoreCount();
// Reports the time spent emulating the last frame to the performance hint session.
void ReportFrameWork(int64_t ns);
} // namespace cputune
