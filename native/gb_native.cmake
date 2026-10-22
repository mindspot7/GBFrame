# Shared native helpers for the GB emulator family. Include from the app's CMakeLists.txt:
#   include(${UI_GB_DIR}/native/gb_native.cmake)
#   gb_native_add(myapp_jni APP_ID "app.example.emu")
# Adds to the target: the OpenGL ES presenter (gl_renderer: zero-copy AHardwareBuffer frames, filters, shaders),
# CPU placement + performance hints (cpu_tuning) and PGO training support (pgo, active with GB_PGO_GEN).
set(GB_NATIVE_DIR "${CMAKE_CURRENT_LIST_DIR}")
function(gb_native_add target)
    cmake_parse_arguments(GBN "" "APP_ID" "" ${ARGN})
    target_sources(${target} PRIVATE "${GB_NATIVE_DIR}/gl_renderer.cpp" "${GB_NATIVE_DIR}/cpu_tuning.cpp"
                                     "${GB_NATIVE_DIR}/pgo.cpp")
    target_include_directories(${target} PRIVATE "${GB_NATIVE_DIR}")
    target_compile_definitions(${target} PRIVATE GB_APP_ID="${GBN_APP_ID}")
    target_link_libraries(${target} PRIVATE android log EGL GLESv3 nativewindow)
endfunction()
