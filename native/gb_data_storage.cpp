#include "gb_data_storage.hpp"
#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/mman.h>
#include <cerrno>
#include <cstdarg>
#include <cstring>
#include <mutex>
#include <unordered_map>
#include <memory>
#include <string>
#include <vector>

namespace {
JavaVM* vm;
jclass bridge;
jmethodID openMethod, infoMethod, listMethod, mkdirMethod, removeMethod, moveMethod;
std::vector<std::string> roots;
struct Env {
    JNIEnv* value = nullptr; bool attached = false;
    Env() { if (vm && vm->GetEnv(reinterpret_cast<void**>(&value), JNI_VERSION_1_6) != JNI_OK) attached = vm->AttachCurrentThread(&value, nullptr) == JNI_OK; }
    ~Env() { if (attached) vm->DetachCurrentThread(); }
};
bool clearError(JNIEnv* env) { if (!env || env->ExceptionCheck()) { if (env) env->ExceptionClear(); errno = EIO; return true; } return false; }
bool booleanCall(jmethodID method, const char* path, const char* second = nullptr) {
    Env e; if (!e.value) { errno = EIO; return false; }
    auto a = e.value->NewStringUTF(path); auto b = second ? e.value->NewStringUTF(second) : nullptr;
    bool result = second ? e.value->CallStaticBooleanMethod(bridge, method, a, b) : e.value->CallStaticBooleanMethod(bridge, method, a);
    e.value->DeleteLocalRef(a); if (b) e.value->DeleteLocalRef(b);
    if (clearError(e.value)) return false;
    if (!result) errno = EIO;
    return result;
}
struct Directory { std::vector<std::string> names; size_t position = 0; dirent entry{}; };
std::mutex directoriesMutex;
std::unordered_map<DIR*, std::unique_ptr<Directory>> directories;
}

extern "C" JNIEXPORT void JNICALL Java_gb_frame_DataAccess_bindNative(JNIEnv* env, jclass cls, jobjectArray paths) {
    env->GetJavaVM(&vm);
    if (bridge) env->DeleteGlobalRef(bridge);
    bridge = static_cast<jclass>(env->NewGlobalRef(cls));
    openMethod = env->GetStaticMethodID(cls, "openFd", "(Ljava/lang/String;I)I");
    infoMethod = env->GetStaticMethodID(cls, "info", "(Ljava/lang/String;)[J");
    listMethod = env->GetStaticMethodID(cls, "children", "(Ljava/lang/String;)[Ljava/lang/String;");
    mkdirMethod = env->GetStaticMethodID(cls, "makeDirectory", "(Ljava/lang/String;)Z");
    removeMethod = env->GetStaticMethodID(cls, "remove", "(Ljava/lang/String;)Z");
    moveMethod = env->GetStaticMethodID(cls, "move", "(Ljava/lang/String;Ljava/lang/String;)Z");
    roots.clear();
    for (int i = 0; i < env->GetArrayLength(paths); ++i) {
        auto s = static_cast<jstring>(env->GetObjectArrayElement(paths, i));
        auto value = env->GetStringUTFChars(s, nullptr); roots.emplace_back(value);
        env->ReleaseStringUTFChars(s, value); env->DeleteLocalRef(s);
    }
}

namespace gb {
bool IsDataPath(const char* path) {
    if (!path) return false;
    for (const auto& root : roots) if (std::strncmp(path, root.c_str(), root.size()) == 0 && (path[root.size()] == '/' || path[root.size()] == '\0')) return true;
    return false;
}
int DataOpen(const char* path, int flags, int mode) {
    if (!IsDataPath(path)) return ::open(path, flags, mode);
    Env e; if (!e.value) { errno = EIO; return -1; }
    auto s = e.value->NewStringUTF(path);
    int fd = e.value->CallStaticIntMethod(bridge, openMethod, s, flags);
    e.value->DeleteLocalRef(s);
    if (clearError(e.value)) return -1;
    if (fd < 0) errno = ENOENT;
    return fd;
}
int DataStat(const char* path, struct stat* result) {
    if (!IsDataPath(path)) return ::stat(path, result);
    Env e; if (!e.value) { errno = EIO; return -1; }
    auto s = e.value->NewStringUTF(path);
    auto info = static_cast<jlongArray>(e.value->CallStaticObjectMethod(bridge, infoMethod, s));
    e.value->DeleteLocalRef(s);
    if (clearError(e.value)) return -1;
    if (!info) { errno = ENOENT; return -1; }
    jlong values[3]; e.value->GetLongArrayRegion(info, 0, 3, values); e.value->DeleteLocalRef(info);
    std::memset(result, 0, sizeof(*result)); result->st_mode = (values[0] ? S_IFDIR : S_IFREG) | 0600;
    result->st_size = values[1]; result->st_mtime = values[2] / 1000; return 0;
}
int DataRename(const char* from, const char* to) { return booleanCall(moveMethod, from, to) ? 0 : -1; }
int DataMkdir(const char* path, int mode) { return IsDataPath(path) ? (booleanCall(mkdirMethod, path) ? 0 : -1) : ::mkdir(path, mode); }
}

extern "C" {
FILE* __real_fopen(const char*, const char*);
int __real_open(const char*, int, ...);
int __real_stat(const char*, struct stat*);
int __real_access(const char*, int);
int __real_mkdir(const char*, mode_t);
int __real_rename(const char*, const char*);
int __real_unlink(const char*);
int __real_remove(const char*);
DIR* __real_opendir(const char*);
dirent* __real_readdir(DIR*);
int __real_closedir(DIR*);

FILE* __wrap_fopen(const char* path, const char* mode) {
    if (!gb::IsDataPath(path)) return __real_fopen(path, mode);
    int flags = std::strchr(mode, '+') ? O_RDWR : (*mode == 'r' ? O_RDONLY : O_WRONLY);
    if (*mode == 'w') flags |= O_CREAT | O_TRUNC;
    if (*mode == 'a') flags |= O_CREAT | O_APPEND;
    int fd = gb::DataOpen(path, flags);
    if (fd < 0) return nullptr;
    FILE* result = fdopen(fd, mode); if (!result) close(fd); return result;
}
FILE* __wrap_fopen64(const char* path, const char* mode) { return __wrap_fopen(path, mode); }
int __wrap_open(const char* path, int flags, ...) {
    mode_t mode = 0600;
    if (flags & O_CREAT) { va_list args; va_start(args, flags); mode = va_arg(args, int); va_end(args); }
    return gb::IsDataPath(path) ? gb::DataOpen(path, flags, mode) : __real_open(path, flags, mode);
}
int __wrap_open64(const char* path, int flags, ...) {
    mode_t mode = 0600;
    if (flags & O_CREAT) { va_list args; va_start(args, flags); mode = va_arg(args, int); va_end(args); }
    return __wrap_open(path, flags, mode);
}
int __wrap___open_2(const char* path, int flags) { return __wrap_open(path, flags); }
int __wrap_stat(const char* path, struct stat* result) { return gb::IsDataPath(path) ? gb::DataStat(path, result) : __real_stat(path, result); }
int __wrap_access(const char* path, int mode) { struct stat s; return gb::IsDataPath(path) ? gb::DataStat(path, &s) : __real_access(path, mode); }
int __wrap_mkdir(const char* path, mode_t mode) { return gb::IsDataPath(path) ? gb::DataMkdir(path, mode) : __real_mkdir(path, mode); }
int __wrap_rename(const char* a, const char* b) { return (gb::IsDataPath(a) || gb::IsDataPath(b)) ? gb::DataRename(a, b) : __real_rename(a, b); }
int __wrap_unlink(const char* path) { return gb::IsDataPath(path) ? (booleanCall(removeMethod, path) ? 0 : -1) : __real_unlink(path); }
int __wrap_remove(const char* path) { return gb::IsDataPath(path) ? __wrap_unlink(path) : __real_remove(path); }
DIR* __wrap_opendir(const char* path) {
    if (!gb::IsDataPath(path)) return __real_opendir(path);
    Env e; if (!e.value) return nullptr;
    auto s = e.value->NewStringUTF(path);
    auto list = static_cast<jobjectArray>(e.value->CallStaticObjectMethod(bridge, listMethod, s)); e.value->DeleteLocalRef(s);
    if (clearError(e.value) || !list) { errno = ENOENT; return nullptr; }
    auto dir = std::make_unique<Directory>();
    for (int i = 0; i < e.value->GetArrayLength(list); ++i) {
        auto name = static_cast<jstring>(e.value->GetObjectArrayElement(list, i)); auto text = e.value->GetStringUTFChars(name, nullptr);
        dir->names.emplace_back(text); e.value->ReleaseStringUTFChars(name, text); e.value->DeleteLocalRef(name);
    }
    e.value->DeleteLocalRef(list);
    auto handle = reinterpret_cast<DIR*>(dir.get());
    std::lock_guard<std::mutex> lock(directoriesMutex); directories.emplace(handle, std::move(dir)); return handle;
}
dirent* __wrap_readdir(DIR* handle) {
    std::lock_guard<std::mutex> lock(directoriesMutex);
    auto found = directories.find(handle); if (found == directories.end()) return __real_readdir(handle);
    auto& d = *found->second; if (d.position == d.names.size()) return nullptr;
    std::memset(&d.entry, 0, sizeof(d.entry)); d.entry.d_type = DT_UNKNOWN;
    std::strncpy(d.entry.d_name, d.names[d.position++].c_str(), sizeof(d.entry.d_name) - 1); return &d.entry;
}
int __wrap_closedir(DIR* handle) {
    std::lock_guard<std::mutex> lock(directoriesMutex);
    if (directories.erase(handle)) return 0;
    return __real_closedir(handle);
}
}

extern "C" JNIEXPORT jboolean JNICALL Java_gb_frame_DataAccess_verifyNativeFileOperations(JNIEnv* env, jclass, jstring path) {
    const char* chars = env->GetStringUTFChars(path, nullptr);
    std::string name(chars); env->ReleaseStringUTFChars(path, chars);
    const std::string renamed = name + ".renamed";
    bool good = false;
    FILE* file = std::fopen(name.c_str(), "wb+");
    if (file) {
        good = std::fwrite("gb-data", 1, 7, file) == 7 && std::fflush(file) == 0;
        char buffer[8]{};
        good = good && std::fseek(file, 0, SEEK_SET) == 0 && std::fread(buffer, 1, 7, file) == 7 && std::strcmp(buffer, "gb-data") == 0;
        if (good) {
            void* mapped = mmap(nullptr, 7, PROT_READ | PROT_WRITE, MAP_SHARED, fileno(file), 0);
            good = mapped != MAP_FAILED;
            if (good) { static_cast<char*>(mapped)[0] = 'G'; good = msync(mapped, 7, MS_SYNC) == 0; munmap(mapped, 7); }
        }
        good = std::fclose(file) == 0 && good;
        struct stat info;
        good = good && ::stat(name.c_str(), &info) == 0 && info.st_size == 7;
        good = good && std::rename(name.c_str(), renamed.c_str()) == 0;
        gb::DataInputFile stream(renamed);
        std::string text; stream >> text;
        good = good && text == "Gb-data";
        const std::string parent = name.substr(0, name.find_last_of('/'));
        DIR* directory = opendir(parent.c_str());
        bool listed = false;
        if (directory) {
            while (auto entry = readdir(directory)) if (renamed.substr(renamed.find_last_of('/') + 1) == entry->d_name) listed = true;
            closedir(directory);
        }
        good = good && listed;
    }
    std::remove(name.c_str()); std::remove(renamed.c_str());
    return good;
}
