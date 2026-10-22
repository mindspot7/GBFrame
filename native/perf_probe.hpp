#pragma once

#include <string>
#include <vector>
#include <sys/types.h>

// Device-test diagnostics for the performance log: what each thread costs, where it runs, how hot the device is.
// All calls read /proc, /sys or system properties; call them about once per second, from any thread.
namespace perfprobe {

struct ThreadUse {
    pid_t tid;
    std::string name; // as the kernel keeps it (15 characters at most)
    double cpuPct;    // CPU time since the previous SampleThreads call, in percent of one core
    int core;         // core the thread last ran on
};

// CPU use of every thread of the process since the previous call (the first call returns an empty list).
std::vector<ThreadUse> SampleThreads();

struct Snapshot {
    std::vector<ThreadUse> threads; // CPU use over the last second
    std::vector<int> coreMHz;       // clock of each core (-1: not readable)
    int thermal = -1;
    float headroom = -1.0f;
};
// The latest once-per-second sample, taken by a low-priority background thread so that reading /proc never stalls the
// caller (e.g. the emulation thread). The thread starts on the first call and stops when not asked for 5 s.
Snapshot Latest();
// Current clock of a core in MHz, or -1 when the system doesn't let the app read it.
int CoreMHz(int core);
// Android thermal status (0 none ... 6 shutdown), or -1 before Android 10/11 (needs AThermal).
int ThermalStatus();
// Thermal headroom forecast for the next 10 s (1.0 = about to throttle), or -1 when not available (Android 12+).
float ThermalHeadroom();
// "manufacturer model (SoC)" from the system properties, for the log.
std::string DeviceName();

} // namespace perfprobe
