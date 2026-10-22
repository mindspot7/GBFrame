// Profile-guided optimization support. In the training build (GB_PGO_GEN defined, code built with -fprofile-generate)
// the counters are written every 30 s to Android/media/<GB_APP_ID>/pgo (readable over USB with tools/pgo-pull.bat),
// since Android apps are usually killed instead of exiting normally. One file per process (%p).
// CMake: add_compile_definitions(GB_PGO_GEN GB_APP_ID="app.example.emu")
#ifdef GB_PGO_GEN
#include <chrono>
#include <sys/stat.h>
#include <thread>

extern "C" void __llvm_profile_set_filename(const char *);
extern "C" int __llvm_profile_write_file(void);

namespace {
struct PgoWriter {
    PgoWriter() {
        static constexpr const char *kDir = "/sdcard/Android/media/" GB_APP_ID "/pgo";
        mkdir(kDir, 0770);
        __llvm_profile_set_filename("/sdcard/Android/media/" GB_APP_ID "/pgo/app-%p.profraw");
        std::thread([] {
            while (true) {
                std::this_thread::sleep_for(std::chrono::seconds(30));
                __llvm_profile_write_file();
            }
        }).detach();
    }
} pgoWriter;
} // namespace
#endif
