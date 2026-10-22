#pragma once

#include <android/native_window_jni.h>
#include <cstdint>

// GPU presentation path: takes the software-rendered Saturn frame, uploads it as a texture and draws it with a
// selectable scaling filter and post-process shader on a dedicated EGL/OpenGL ES 3 thread.
#include <string>

namespace glr {

// Takes ownership of one reference to `window` (releases it when replaced). Pass nullptr to detach.
void SetWindow(ANativeWindow *window);
// Latest-frame-wins; copies the pixels (RGBA8888).
void Submit(const uint32_t *fb, uint32_t width, uint32_t height, uint32_t scale = 1);
// filter: 0 nearest, 1 bilinear.
// shader: 0 none, 1 scanlines, 2 CRT, 3 LCD3x, 4 dot matrix.
void SetOptions(int filter, int shader);
void Shutdown();

// GPU name reported by the GL driver (empty until the presenter started)
std::string GpuName();
// OpenGL ES could not start (EGL setup failed): the app falls back to the plain window output
bool Failed();
} // namespace glr
