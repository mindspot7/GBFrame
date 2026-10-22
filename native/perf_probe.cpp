#include "perf_probe.hpp"

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <dlfcn.h>
#include <atomic>
#include <mutex>
#include <sys/resource.h>
#include <thread>
#include <sys/system_properties.h>
#include <unistd.h>
#include <unordered_map>

namespace perfprobe {

namespace {

std::mutex g_mutex;
std::unordered_map<pid_t, unsigned long long> g_prevTicks;
std::chrono::steady_clock::time_point g_prevTime{};

// utime + stime (clock ticks) and the last core from /proc/self/task/<tid>/stat; the name sits in parentheses and may
// contain spaces, so fields are counted after the last ')'.
bool ReadStat(pid_t tid, std::string &name, unsigned long long &ticks, int &core) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/self/task/%d/stat", tid);
    FILE *f = fopen(path, "r");
    if (!f) return false;
    char buf[1024];
    const size_t n = fread(buf, 1, sizeof(buf) - 1, f);
    fclose(f);
    buf[n] = 0;
    char *open = strchr(buf, '(');
    char *close = strrchr(buf, ')');
    if (!open || !close || close < open) return false;
    name.assign(open + 1, close - open - 1);
    // Fields after ')': 3 state, ..., 14 utime, 15 stime, ..., 39 processor
    char *p = close + 2;
    unsigned long long utime = 0, stime = 0;
    core = -1;
    for (int field = 3; *p && field <= 39; ++field) {
        char *end = nullptr;
        if (field == 14) utime = strtoull(p, &end, 10);
        else if (field == 15) stime = strtoull(p, &end, 10);
        else if (field == 39) core = (int)strtol(p, &end, 10);
        char *sp = strchr(p, ' ');
        if (!sp) break;
        p = sp + 1;
    }
    ticks = utime + stime;
    return true;
}

struct Thermal {
    using Acquire = void *(*)();
    using Status = int (*)(void *);
    using Headroom = float (*)(void *, int);
    void *manager = nullptr;
    Status status = nullptr;
    Headroom headroom = nullptr;
    Thermal() {
        void *lib = dlopen("libandroid.so", RTLD_NOW);
        if (!lib) return;
        auto acquire = reinterpret_cast<Acquire>(dlsym(lib, "AThermal_acquireManager"));
        status = reinterpret_cast<Status>(dlsym(lib, "AThermal_getCurrentThermalStatus"));
        headroom = reinterpret_cast<Headroom>(dlsym(lib, "AThermal_getThermalHeadroom"));
        if (acquire) manager = acquire();
    }
};

Thermal &GetThermal() {
    static Thermal t;
    return t;
}

std::string Prop(const char *key) {
    char v[PROP_VALUE_MAX] = {};
    __system_property_get(key, v);
    return v;
}

} // namespace

std::vector<ThreadUse> SampleThreads() {
    std::lock_guard lk(g_mutex);
    std::vector<ThreadUse> out;
    const auto now = std::chrono::steady_clock::now();
    const double secs = g_prevTime.time_since_epoch().count() == 0 ? 0.0 : std::chrono::duration<double>(now - g_prevTime).count();
    g_prevTime = now;
    const double tck = (double)sysconf(_SC_CLK_TCK);
    std::unordered_map<pid_t, unsigned long long> ticksNow;
    if (DIR *d = opendir("/proc/self/task")) {
        while (dirent *e = readdir(d)) {
            if (e->d_name[0] < '0' || e->d_name[0] > '9') continue;
            const pid_t tid = (pid_t)atoi(e->d_name);
            std::string name;
            unsigned long long ticks;
            int core;
            if (!ReadStat(tid, name, ticks, core)) continue;
            ticksNow[tid] = ticks;
            if (secs > 0) {
                auto it = g_prevTicks.find(tid);
                const unsigned long long before = it != g_prevTicks.end() ? it->second : ticks;
                out.push_back({tid, name, (double)(ticks - before) / tck / secs * 100.0, core});
            }
        }
        closedir(d);
    }
    g_prevTicks.swap(ticksNow);
    return out;
}

int CoreMHz(int core) {
    if (core < 0) return -1;
    char path[96];
    snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq", core);
    FILE *f = fopen(path, "r");
    if (!f) return -1;
    long khz = -1;
    if (fscanf(f, "%ld", &khz) != 1) khz = -1;
    fclose(f);
    return khz > 0 ? (int)(khz / 1000) : -1;
}

int ThermalStatus() {
    auto &t = GetThermal();
    return t.manager && t.status ? t.status(t.manager) : -1;
}

float ThermalHeadroom() {
    // Android limits how often the forecast may be asked for; it is cached here for 2 s
    static float cached = -1.0f;
    static std::chrono::steady_clock::time_point last{};
    auto &t = GetThermal();
    if (!t.manager || !t.headroom) return -1.0f;
    const auto now = std::chrono::steady_clock::now();
    if (last.time_since_epoch().count() == 0 || now - last >= std::chrono::seconds(2)) {
        last = now;
        const float h = t.headroom(t.manager, 10);
        cached = h == h ? h : -1.0f; // NaN: not supported
    }
    return cached;
}

std::string DeviceName() {
    std::string s = Prop("ro.product.manufacturer") + " " + Prop("ro.product.model");
    std::string soc = Prop("ro.soc.model");
    if (soc.empty()) soc = Prop("ro.board.platform");
    if (!soc.empty()) s += " (" + soc + ")";
    return s;
}

namespace {
std::mutex g_snapMutex;
Snapshot g_snap;
std::atomic<bool> g_samplerRunning{false};
std::atomic<int64_t> g_lastAsk{0};

int64_t NowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}

void Sampler() {
    setpriority(PRIO_PROCESS, 0, 10); // background: never compete with the emulation threads
    const int cores = (int)sysconf(_SC_NPROCESSORS_CONF);
    SampleThreads(); // baseline
    while (NowMs() - g_lastAsk.load(std::memory_order_relaxed) < 5000) {
        std::this_thread::sleep_for(std::chrono::seconds(1));
        Snapshot s;
        s.threads = SampleThreads();
        for (int c = 0; c < cores; ++c) s.coreMHz.push_back(CoreMHz(c));
        s.thermal = ThermalStatus();
        s.headroom = ThermalHeadroom();
        std::lock_guard lk(g_snapMutex);
        g_snap = std::move(s);
    }
    g_samplerRunning = false;
}
} // namespace

Snapshot Latest() {
    g_lastAsk.store(NowMs(), std::memory_order_relaxed);
    if (!g_samplerRunning.exchange(true)) {
        std::thread(Sampler).detach();
    }
    std::lock_guard lk(g_snapMutex);
    return g_snap;
}

} // namespace perfprobe
