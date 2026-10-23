#include "gl_renderer.hpp"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <android/hardware_buffer.h>
#include <android/log.h>

#include <atomic>
#include <cstring>
#include <condition_variable>
#include <mutex>
#include <thread>
#include <vector>

#define TAG "GBNativeGL"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace glr {
namespace {

const char *kVertex = R"(#version 300 es
out vec2 vUv;
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vUv = vec2(p.x, 1.0 - p.y);
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
)";

const char *kFragment = R"(#version 300 es
precision highp float;
in vec2 vUv;
out vec4 outColor;
uniform sampler2D uTex;
uniform vec2 uTexSize;
uniform vec2 uOutSize;
uniform vec2 uSrcSize; // native (1x) size of the frame: patterns follow the original pixels at any internal resolution
uniform int uFilter;
uniform int uShader;

const float PI = 3.14159265;

vec2 filterUv(vec2 uv) {
    if (uFilter == 0) {
        return (floor(uv * uTexSize) + 0.5) / uTexSize;
    } else if (uFilter == 2) {
        // Sharp bilinear: integer pre-scale, bilinear only on the edges between texels.
        vec2 texel = uv * uTexSize;
        vec2 scale = max(floor(uOutSize / uTexSize), vec2(1.0));
        vec2 fl = floor(texel);
        vec2 s = fract(texel);
        vec2 range = 0.5 - 0.5 / scale;
        vec2 dist = s - 0.5;
        vec2 f = (dist - clamp(dist, -range, range)) * scale + 0.5;
        return (fl + f) / uTexSize;
    }
    return uv;
}

// CRT Royale (single pass): an approximation of RetroArch's crt-royale in one pass for mobile GPUs. In linear light
// (CRT gamma 2.4 in, 2.2 out): each output pixel gathers the 4 nearest scanlines, each a gaussian beam whose width grows
// with brightness (bright phosphors bloom into the gap, dark lines stay thin), sampled with a horizontal linear filter;
// a wide halation glow from the blurred picture; an aperture grille phosphor mask (3-px RGB triads on wide screens,
// 2-px magenta/green on narrow ones) with its brightness compensated; and a soft shoulder instead of a hard clip.
// Interlaced frames use the low-res line grid (uSrcSize), so both fields blend like crt-royale's bob pass.
vec3 royaleLine(float u, float row) {
    // Horizontally at the texture's own resolution (hi-res 704-wide pictures keep their detail)
    vec3 c = texture(uTex, vec2(u, (row + 0.5) / uSrcSize.y)).rgb;
    return pow(c, vec3(2.4));
}

vec3 crtRoyale(vec2 uv) {
    float sy = uv.y * uSrcSize.y - 0.5;
    float y0 = floor(sy);
    float fy = sy - y0;
    vec3 col = vec3(0.0);
    for (int k = -1; k <= 2; ++k) {
        float fk = float(k);
        vec3 c = royaleLine(uv.x, y0 + fk);
        vec3 sigma = mix(vec3(0.28), vec3(0.46), sqrt(c));
        vec3 d = vec3(fy - fk) / sigma;
        col += c * exp(-0.5 * d * d) / sigma * 0.3989;
    }
    vec2 px = 1.0 / uSrcSize;
    vec3 halo = pow(texture(uTex, uv).rgb, vec3(2.4));
    halo += pow(texture(uTex, uv + vec2(1.5 * px.x, 0.0)).rgb, vec3(2.4));
    halo += pow(texture(uTex, uv - vec2(1.5 * px.x, 0.0)).rgb, vec3(2.4));
    halo += pow(texture(uTex, uv + vec2(0.0, 1.5 * px.y)).rgb, vec3(2.4));
    halo += pow(texture(uTex, uv - vec2(0.0, 1.5 * px.y)).rgb, vec3(2.4));
    col = mix(col, halo * 0.2, 0.10);
    const float dark = 0.30;
    vec3 mask;
    float avg;
    if (uOutSize.x >= 960.0) {
        int m = int(mod(gl_FragCoord.x, 3.0));
        mask = vec3(m == 0 ? 1.0 : dark, m == 1 ? 1.0 : dark, m == 2 ? 1.0 : dark);
        avg = (1.0 + 2.0 * dark) / 3.0;
    } else {
        bool mg = mod(gl_FragCoord.x, 2.0) < 1.0;
        mask = mg ? vec3(1.0, dark, 1.0) : vec3(dark, 1.0, dark);
        avg = (1.0 + dark) / 2.0;
    }
    col *= mask / avg;
    col /= 1.0 + max(col - 0.8, 0.0) * 0.6;
    return pow(clamp(col, 0.0, 1.0), vec3(1.0 / 2.2));
}

void main() {
    vec2 uv = vUv;

    if (uShader == 5) {
        outColor = vec4(crtRoyale(uv), 1.0);
        return;
    }

    if (uShader == 2) {
        // CRT: barrel distortion
        vec2 c = uv * 2.0 - 1.0;
        c *= 1.0 + 0.05 * dot(c, c);
        uv = c * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            outColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
    }

    vec3 col = texture(uTex, filterUv(uv)).rgb;
    float line = fract(uv.y * uSrcSize.y);

    if (uShader == 1) {
        col *= 0.60 + 0.40 * sin(line * PI);
    } else if (uShader == 2) {
        col *= 0.55 + 0.45 * sin(line * PI);
        int m = int(mod(gl_FragCoord.x, 3.0));
        vec3 mask = vec3(m == 0 ? 1.0 : 0.72, m == 1 ? 1.0 : 0.72, m == 2 ? 1.0 : 0.72);
        col *= mask * 1.15;
        vec2 v = uv * (1.0 - uv.yx);
        col *= pow(clamp(v.x * v.y * 16.0, 0.0, 1.0), 0.25);
    } else if (uShader == 3) {
        // LCD3x: 3x3 pixel cells with RGB sub-pixels and a dark gap
        vec2 g = mod(gl_FragCoord.xy, 3.0);
        int sub = int(g.x);
        vec3 mask = vec3(sub == 0 ? 1.0 : 0.35, sub == 1 ? 1.0 : 0.35, sub == 2 ? 1.0 : 0.35);
        float gap = g.y < 1.0 ? 0.55 : 1.0;
        col *= mask * gap * 1.5;
    }
    outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
)";

struct State {
    std::mutex m;
    std::condition_variable cv;
    std::thread th;
    bool started = false;
    bool stop = false;

    ANativeWindow *pendingWindow = nullptr;
    bool windowPending = false;

    std::vector<uint32_t> frame;
    uint32_t fw = 0, fh = 0, fscale = 1;
    bool frameReady = false;

    // Zero-copy path: frames are written straight into GPU-visible AHardwareBuffers (no glTexSubImage2D upload, no
    // intermediate copy). Several slots rotate so the emulator never writes the buffer being displayed.
    static constexpr int kSlots = 4;
    struct Slot {
        AHardwareBuffer *hb = nullptr;
        uint32_t w = 0, h = 0, stride = 0;
        uint32_t gen = 0; // bumped when the buffer is reallocated
    } slots[kSlots];
    int readySlot = -1, shownSlot = -1, writingSlot = -1;
    std::atomic<bool> hbDisabled{false}; // set when the device lacks the EGL/GL extensions or allocation fails

    std::atomic<int> filter{1};
    std::atomic<int> shader{0};
    bool dirty = false;
};

State S;
std::mutex gpuNameMutex;
std::string gpuName;
std::atomic<bool> failed{false};
// GPU time of the presenter's draws (GL_EXT_disjoint_timer_query), for the overlay's GPU usage
std::atomic<uint64_t> gpuNs{0};
std::atomic<bool> gpuTimed{false};

GLuint Compile(GLenum type, const char *src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetShaderInfoLog(s, sizeof(log), nullptr, log);
        LOGE("shader compile failed: %s", log);
    }
    return s;
}

void Loop() {
    EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(dpy, nullptr, nullptr);
    const EGLint cfgAttr[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                              EGL_RED_SIZE,        8,                  EGL_GREEN_SIZE,   8,
                              EGL_BLUE_SIZE,       8,                  EGL_ALPHA_SIZE,   0,
                              EGL_NONE};
    EGLConfig cfg = nullptr;
    EGLint n = 0;
    eglChooseConfig(dpy, cfgAttr, &cfg, 1, &n);
    const EGLint ctxAttr[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLContext ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctxAttr);
    if (n == 0 || ctx == EGL_NO_CONTEXT) {
        LOGE("EGL setup failed");
        failed = true;
        return;
    }

    EGLSurface surf = EGL_NO_SURFACE;
    ANativeWindow *win = nullptr;
    GLuint prog = 0, tex = 0, vao = 0;
    GLint uSrcSize = -1;
    GLint uTexSize = -1, uOutSize = -1, uFilter = -1, uShader = -1;
    uint32_t texW = 0, texH = 0;
    float texScale = 1.0f;
    std::vector<uint32_t> local;
    uint32_t lw = 0, lh = 0;
    bool haveFrame = false;
    int frameSlot = -1; // slot of the new frame (zero-copy path), -1 = frame in `local`
    struct SlotGl { GLuint tex = 0; EGLImageKHR img = EGL_NO_IMAGE_KHR; uint32_t gen = 0; } slotGl[State::kSlots];
    auto eglGetNativeClientBuffer = (PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC)eglGetProcAddress("eglGetNativeClientBufferANDROID");
    auto imageTargetTexture = (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)eglGetProcAddress("glEGLImageTargetTexture2DOES");
    auto createImage = (PFNEGLCREATEIMAGEKHRPROC)eglGetProcAddress("eglCreateImageKHR");
    auto destroyImage = (PFNEGLDESTROYIMAGEKHRPROC)eglGetProcAddress("eglDestroyImageKHR");
    if (!eglGetNativeClientBuffer || !imageTargetTexture || !createImage || !destroyImage) {
        S.hbDisabled = true;
    }
    GLuint drawTex = 0; // texture sampled this frame
    uint32_t drawW = 0, drawH = 0;

    // GPU timing: a few GL_TIME_ELAPSED queries in flight, read when the GPU has finished them (never waits)
    constexpr int kQueries = 4;
    GLuint queries[kQueries] = {};
    bool queryPending[kQueries] = {};
    int queryNext = 0;
    PFNGLGETQUERYOBJECTUI64VEXTPROC getQueryU64 = nullptr;
    auto collectQueries = [&] {
        GLint disjoint = 0;
        glGetIntegerv(GL_GPU_DISJOINT_EXT, &disjoint); // clock change, power state...: results not meaningful
        for (int i = 0; i < kQueries; ++i) {
            if (!queryPending[i]) continue;
            GLuint avail = 0;
            glGetQueryObjectuiv(queries[i], GL_QUERY_RESULT_AVAILABLE, &avail);
            if (!avail) continue;
            GLuint64 ns = 0;
            getQueryU64(queries[i], GL_QUERY_RESULT_EXT, &ns);
            queryPending[i] = false;
            if (!disjoint) gpuNs.fetch_add(ns, std::memory_order_relaxed);
        }
    };

    for (;;) {
        bool newFrame = false;
        {
            std::unique_lock lk(S.m);
            S.cv.wait(lk, [] { return S.stop || S.windowPending || S.frameReady || S.dirty; });
            if (S.stop) {
                break;
            }
            if (S.windowPending) {
                if (surf != EGL_NO_SURFACE) {
                    eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
                    eglDestroySurface(dpy, surf);
                    surf = EGL_NO_SURFACE;
                }
                if (win) {
                    ANativeWindow_release(win);
                }
                win = S.pendingWindow;
                S.pendingWindow = nullptr;
                S.windowPending = false;
                if (win) {
                    surf = eglCreateWindowSurface(dpy, cfg, win, nullptr);
                    if (surf != EGL_NO_SURFACE && eglMakeCurrent(dpy, surf, surf, ctx)) {
                        eglSwapInterval(dpy, 1);
                        if (!prog) {
                            GLuint vs = Compile(GL_VERTEX_SHADER, kVertex);
                            GLuint fs = Compile(GL_FRAGMENT_SHADER, kFragment);
                            prog = glCreateProgram();
                            glAttachShader(prog, vs);
                            glAttachShader(prog, fs);
                            glLinkProgram(prog);
                            uTexSize = glGetUniformLocation(prog, "uTexSize");
                            uSrcSize = glGetUniformLocation(prog, "uSrcSize");
                            uOutSize = glGetUniformLocation(prog, "uOutSize");
                            uFilter = glGetUniformLocation(prog, "uFilter");
                            uShader = glGetUniformLocation(prog, "uShader");
                            if (const char *r = (const char *)glGetString(GL_RENDERER)) {
                                std::lock_guard nl(gpuNameMutex);
                                gpuName = r;
                            }
                            const char *exts = (const char *)glGetString(GL_EXTENSIONS);
                            if (exts && strstr(exts, "GL_EXT_disjoint_timer_query")) {
                                getQueryU64 = (PFNGLGETQUERYOBJECTUI64VEXTPROC)eglGetProcAddress("glGetQueryObjectui64vEXT");
                                if (getQueryU64) {
                                    glGenQueries(kQueries, queries);
                                    gpuTimed = true;
                                }
                            }
                            glGenVertexArrays(1, &vao);
                            glGenTextures(1, &tex);
                            glBindTexture(GL_TEXTURE_2D, tex);
                            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                            texW = texH = 0;
                        }
                    } else {
                        LOGE("eglCreateWindowSurface/MakeCurrent failed");
                        surf = EGL_NO_SURFACE;
                    }
                }
            }
            if (S.frameReady) {
                if (S.readySlot >= 0) {
                    frameSlot = S.readySlot;
                    S.shownSlot = frameSlot;
                    S.readySlot = -1;
                } else {
                    frameSlot = -1;
                    std::swap(local, S.frame);
                }
                lw = S.fw;
                lh = S.fh;
                texScale = (float)S.fscale;
                S.frameReady = false;
                newFrame = true;
                haveFrame = true;
            }
            S.dirty = false;
        }
        if (surf == EGL_NO_SURFACE || !haveFrame || !prog) {
            continue;
        }

        if (newFrame && frameSlot >= 0) {
            // Zero-copy: sample the hardware buffer directly through an EGLImage
            auto &gl = slotGl[frameSlot];
            const auto &sl = S.slots[frameSlot];
            if (gl.gen != sl.gen || gl.img == EGL_NO_IMAGE_KHR) {
                if (gl.img != EGL_NO_IMAGE_KHR) { destroyImage(dpy, gl.img); gl.img = EGL_NO_IMAGE_KHR; }
                if (!gl.tex) {
                    glGenTextures(1, &gl.tex);
                    glBindTexture(GL_TEXTURE_2D, gl.tex);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                }
                const EGLint attrs[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
                gl.img = createImage(dpy, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, eglGetNativeClientBuffer(sl.hb), attrs);
                glBindTexture(GL_TEXTURE_2D, gl.tex);
                if (gl.img != EGL_NO_IMAGE_KHR) imageTargetTexture(GL_TEXTURE_2D, (GLeglImageOES)gl.img);
                if (gl.img == EGL_NO_IMAGE_KHR || glGetError() != GL_NO_ERROR) {
                    LOGE("EGLImage from AHardwareBuffer failed, using texture uploads");
                    S.hbDisabled = true; // next frames come through the upload path
                    continue;
                }
                gl.gen = sl.gen;
            }
            drawTex = gl.tex;
            drawW = lw;
            drawH = lh;
        } else if (newFrame) {
            drawTex = tex;
            glBindTexture(GL_TEXTURE_2D, tex);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            if (lw != texW || lh != texH) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, lw, lh, 0, GL_RGBA, GL_UNSIGNED_BYTE, local.data());
                texW = lw;
                texH = lh;
            } else {
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, lw, lh, GL_RGBA, GL_UNSIGNED_BYTE, local.data());
            }
            drawW = texW;
            drawH = texH;
        }

        GLuint query = 0;
        if (getQueryU64) {
            collectQueries();
            if (!queryPending[queryNext]) { // else the GPU is behind: this draw isn't timed
                query = queries[queryNext];
                queryPending[queryNext] = true;
                queryNext = (queryNext + 1) % kQueries;
                glBeginQuery(GL_TIME_ELAPSED_EXT, query);
            }
        }
        EGLint sw = 0, sh = 0;
        eglQuerySurface(dpy, surf, EGL_WIDTH, &sw);
        eglQuerySurface(dpy, surf, EGL_HEIGHT, &sh);
        glViewport(0, 0, sw, sh);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(prog);
        glBindVertexArray(vao);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, drawTex ? drawTex : tex);
        glUniform1i(glGetUniformLocation(prog, "uTex"), 0);
        glUniform2f(uTexSize, (float)drawW, (float)drawH);
        {
            // Hi-res (640/704 wide) and interlaced (448+ lines) modes, used by many games in menus and title screens,
            // are mapped back to the low-res grid so the scanline/dot patterns keep the same size instead of doubling up
            float nw = (float)drawW / texScale, nh = (float)drawH / texScale;
            if (nw >= 560.0f) nw *= 0.5f;
            if (nh >= 400.0f) nh *= 0.5f;
            glUniform2f(uSrcSize, nw, nh);
        }
        glUniform2f(uOutSize, (float)sw, (float)sh);
        glUniform1i(uFilter, S.filter.load());
        glUniform1i(uShader, S.shader.load());
        glDrawArrays(GL_TRIANGLES, 0, 3);
        if (query) glEndQuery(GL_TIME_ELAPSED_EXT);
        eglSwapBuffers(dpy, surf);
    }

    if (surf != EGL_NO_SURFACE) {
        eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(dpy, surf);
    }
    if (win) {
        ANativeWindow_release(win);
    }
    eglDestroyContext(dpy, ctx);
    eglTerminate(dpy);
}

void EnsureThread() {
    if (!S.started) {
        S.started = true;
        S.stop = false;
        S.th = std::thread(Loop);
    }
}

} // namespace

void SetWindow(ANativeWindow *window) {
    std::lock_guard lk(S.m);
    if (S.windowPending && S.pendingWindow) {
        ANativeWindow_release(S.pendingWindow);
    }
    S.pendingWindow = window;
    S.windowPending = true;
    EnsureThread();
    S.cv.notify_all();
}

bool SubmitHardwareBuffer(const uint32_t *fb, uint32_t width, uint32_t height, uint32_t scale) {
    int idx = -1;
    {
        std::lock_guard lk(S.m);
        if (!S.started) return true;
        for (int i = 0; i < State::kSlots; i++) {
            if (i != S.readySlot && i != S.shownSlot) { idx = i; break; }
        }
        if (idx < 0) return false;
        S.writingSlot = idx;
    }
    auto &sl = S.slots[idx];
    bool ok = true;
    if (!sl.hb || sl.w != width || sl.h != height) {
        if (sl.hb) { AHardwareBuffer_release(sl.hb); sl.hb = nullptr; }
        AHardwareBuffer_Desc d{};
        d.width = width;
        d.height = height;
        d.layers = 1;
        d.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
        d.usage = AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
        ok = AHardwareBuffer_allocate(&d, &sl.hb) == 0;
        if (ok) {
            AHardwareBuffer_describe(sl.hb, &d);
            sl.w = width; sl.h = height; sl.stride = d.stride; sl.gen++;
        }
    }
    void *dst = nullptr;
    ok = ok && AHardwareBuffer_lock(sl.hb, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, nullptr, &dst) == 0 && dst;
    if (ok) {
        if (sl.stride == width) {
            memcpy(dst, fb, (size_t)width * height * 4);
        } else {
            for (uint32_t y = 0; y < height; y++) {
                memcpy((uint32_t *)dst + (size_t)y * sl.stride, fb + (size_t)y * width, (size_t)width * 4);
            }
        }
        AHardwareBuffer_unlock(sl.hb, nullptr);
    }
    std::lock_guard lk(S.m);
    S.writingSlot = -1;
    if (!ok) {
        S.hbDisabled = true;
        LOGE("AHardwareBuffer path unavailable, using texture uploads");
        return false;
    }
    S.readySlot = idx;
    S.fw = width;
    S.fh = height;
    S.fscale = scale ? scale : 1;
    S.frameReady = true;
    S.cv.notify_all();
    return true;
}

void Submit(const uint32_t *fb, uint32_t width, uint32_t height, uint32_t scale) {
    if (!S.hbDisabled.load() && SubmitHardwareBuffer(fb, width, height, scale)) {
        return;
    }
    std::lock_guard lk(S.m);
    if (!S.started) {
        return;
    }
    S.readySlot = -1;
    S.frame.assign(fb, fb + (size_t)width * height);
    S.fw = width;
    S.fh = height;
    S.fscale = scale ? scale : 1;
    S.frameReady = true;
    S.cv.notify_all();
}

bool Failed() {
    return failed.load();
}

std::string GpuName() {
    std::lock_guard lk(gpuNameMutex);
    return gpuName;
}

bool GpuTimed() {
    return gpuTimed.load();
}

uint64_t TakeGpuNs() {
    return gpuNs.exchange(0, std::memory_order_relaxed);
}

void SetOptions(int filter, int shader) {
    S.filter = filter;
    S.shader = shader;
    std::lock_guard lk(S.m);
    S.dirty = true;
    S.cv.notify_all();
}

void Shutdown() {
    {
        std::lock_guard lk(S.m);
        if (!S.started) {
            return;
        }
        S.stop = true;
        S.cv.notify_all();
    }
    if (S.th.joinable()) {
        S.th.join();
    }
    S.started = false;
}

} // namespace glr
