# Shared native helpers for the GB emulator family. Include from the app's CMakeLists.txt:
#   include(${GBFrame_DIR}/native/gb_native.cmake)
#   gb_native_add(myapp_jni APP_ID "app.example.emu")
# Adds to the target: the OpenGL ES presenter (gl_renderer: zero-copy AHardwareBuffer frames, filters, shaders),
# CPU placement + performance hints (cpu_tuning), PGO training support (pgo, active with GB_PGO_GEN) and device-test
# diagnostics for performance logs (perf_probe: CPU per thread, core clock, thermal status, device name), the Skip
# Engine (auto_skip: maximum speed through loads, transitions and save waits; safe disc read speed) and A/B diagnostic
# switches read from adb-set properties (diag_props.hpp).
set(GB_NATIVE_DIR "${CMAKE_CURRENT_LIST_DIR}")
function(gb_native_storage target)
    target_sources(${target} PRIVATE "${GB_NATIVE_DIR}/gb_data_storage.cpp")
    target_include_directories(${target} PRIVATE "${GB_NATIVE_DIR}")
    foreach(symbol fopen fopen64 open open64 __open_2 stat access mkdir rename unlink remove opendir readdir closedir)
        target_link_options(${target} PRIVATE "-Wl,--wrap=${symbol}")
    endforeach()
endfunction()
function(gb_native_add target)
    cmake_parse_arguments(GBN "" "APP_ID" "" ${ARGN})
    target_sources(${target} PRIVATE "${GB_NATIVE_DIR}/gl_renderer.cpp" "${GB_NATIVE_DIR}/cpu_tuning.cpp"
                                     "${GB_NATIVE_DIR}/pgo.cpp" "${GB_NATIVE_DIR}/perf_probe.cpp"
                                     "${GB_NATIVE_DIR}/auto_skip.cpp")
    target_include_directories(${target} PRIVATE "${GB_NATIVE_DIR}")
    target_compile_definitions(${target} PRIVATE GB_APP_ID="${GBN_APP_ID}")
    target_link_libraries(${target} PRIVATE android log EGL GLESv3 nativewindow)
endfunction()

# Lets a console core (the app's emulation library) use the kit's header-only helpers, e.g. gb_proc_fd.hpp: read the
# /proc/self/fd/N links the app stages for disc files through the descriptor, since reopening them by path is refused on
# some devices. PUBLIC, so everything that links the core sees the same definitions.
function(gb_native_core target)
    target_include_directories(${target} PUBLIC "${GB_NATIVE_DIR}")
endfunction()
