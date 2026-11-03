#pragma once

#include <android/native_window_jni.h>
#include <android/hardware_buffer.h>
#include <cstdint>
#include <cstddef>
#include <functional>

// GPU presentation path: takes the software-rendered Saturn frame, uploads it as a texture and draws it with a
// selectable scaling filter and post-process shader on a dedicated EGL/OpenGL ES 3 thread.
#include <string>

namespace glr {

// Hardware-buffer slots the presenter rotates; a VulkanTarget token carries a slot index below this. A producer that
// keeps one imported image per slot needs this many entries.
constexpr int kTargetSlots = 5;

struct VulkanTarget {
    AHardwareBuffer *buffer = nullptr;
    uint64_t token = 0; // slot index + allocation generation; valid until SubmitVulkanTarget/CancelVulkanTarget
    uint32_t stride = 0;
};

// Takes ownership of one reference to `window` (releases it when replaced). Pass nullptr to detach.
void SetWindow(ANativeWindow *window);
// Latest-frame-wins; copies the pixels (RGBA8888).
void Submit(const uint32_t *fb, uint32_t width, uint32_t height, uint32_t scale = 1);
// Vulkan readback path; skips the secondary CPU-to-AHardwareBuffer upload.
void SubmitCpu(const uint32_t *fb, uint32_t width, uint32_t height, uint32_t scale = 1);
// Reserve a presenter-owned AHardwareBuffer for a Vulkan GPU copy. The returned target is not CPU writable and must be
// submitted or cancelled. Returns an empty target if EGLImage sharing is unavailable or no free slot exists.
// Every call hands the caller one reference to `buffer`: keep it (one per imported VkImage) or release it, also when
// the same buffer is already imported or the import fails, or the buffers of old sizes are never freed.
VulkanTarget AcquireVulkanTarget(uint32_t width, uint32_t height);
void SubmitVulkanTarget(uint64_t token, uint32_t width, uint32_t height, uint32_t scale = 1);
void CancelVulkanTarget(uint64_t token);
// Reads the frame on screen when it came from a Vulkan target (pixels, width, height, stride in pixels); false otherwise.
// Holds the presenter lock while `fn` runs: for rare reads such as save state thumbnails, never per frame.
bool ReadShownFrame(const std::function<void(const uint32_t *, uint32_t, uint32_t, uint32_t)> &fn);
// filter: 0 nearest, 1 bilinear.
// shader: 0 none, 1 scanlines, 2 CRT, 3 LCD3x, 5 CRT Royale, 6 GTU (4, dot matrix, was removed: shows none).
void SetOptions(int filter, int shader);
// Colour correction of the source colours, with any shader: 0 none, 1 bsnes gamma ramp.
void SetColorCorrection(int mode);
void Shutdown();

// GPU name reported by the GL driver (empty until the presenter started)
std::string GpuName();
// GPU time measurement of the presenter's draws (GL_EXT_disjoint_timer_query): available, and the GPU nanoseconds
// measured since the last call
bool GpuTimed();
uint64_t TakeGpuNs();
// OpenGL ES could not start (EGL setup failed): the app falls back to the plain window output
bool Failed();
} // namespace glr
