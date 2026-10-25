#pragma once

// Reading disc files that the Android app exposes as symlinks to /proc/self/fd/N (shared by every console core of the
// GB emulator family: include <gb_proc_fd.hpp>, see gb_native_core() in gb_native.cmake).
//
// The app opens documents picked through the Storage Access Framework as file descriptors and stages them as symlinks to
// /proc/self/fd/N, so path-based loaders keep working. Reopening such a link *by path* (fopen, ifstream, open + mmap) is
// refused on some devices (Anbernic RG DS: stat works but open fails, reported as "file not found"). The helpers here
// read through the descriptor itself, which needs no reopen. Paths that are not such links open normally.

#include <algorithm>
#include <cerrno>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <istream>
#include <memory>
#include <streambuf>

#if defined(__unix__) || defined(__ANDROID__) || defined(__APPLE__)
    #define GB_PROC_FD 1
    #include <fcntl.h>
    #include <sys/types.h>
    #include <unistd.h>
#endif

namespace gb {

// The descriptor number when `path` is a symlink to /proc/self/fd/N, otherwise -1.
inline int ProcFdOf(const std::filesystem::path &path) {
#ifdef GB_PROC_FD
    char target[64];
    const ssize_t n = readlink(path.c_str(), target, sizeof(target) - 1);
    if (n <= 0) {
        return -1;
    }
    target[n] = '\0';
    static constexpr char kPrefix[] = "/proc/self/fd/";
    constexpr size_t kPrefixLen = sizeof(kPrefix) - 1;
    if (std::strncmp(target, kPrefix, kPrefixLen) != 0) {
        return -1;
    }
    char *end = nullptr;
    const long fd = std::strtol(target + kPrefixLen, &end, 10);
    if (end == target + kPrefixLen || *end != '\0' || fd < 0) {
        return -1;
    }
    return static_cast<int>(fd);
#else
    (void)path;
    return -1;
#endif
}

#ifdef GB_PROC_FD

// Read-only stream buffer over a file descriptor it owns, positioned with pread (the descriptor's own offset is never used).
class FdStreamBuf final : public std::streambuf {
public:
    explicit FdStreamBuf(int fd) : m_fd(fd) {
        const off_t end = lseek(fd, 0, SEEK_END);
        m_size = end > 0 ? static_cast<int64_t>(end) : 0;
    }
    ~FdStreamBuf() override {
        close(m_fd);
    }

    FdStreamBuf(const FdStreamBuf &) = delete;
    FdStreamBuf &operator=(const FdStreamBuf &) = delete;

protected:
    int_type underflow() override {
        if (gptr() < egptr()) {
            return traits_type::to_int_type(*gptr());
        }
        const int64_t offset = m_base + (egptr() - eback());
        const ssize_t n = ReadAt(m_buf, sizeof(m_buf), offset);
        if (n <= 0) {
            return traits_type::eof();
        }
        m_base = offset;
        setg(m_buf, m_buf, m_buf + n);
        return traits_type::to_int_type(*gptr());
    }

    std::streamsize xsgetn(char *dest, std::streamsize count) override {
        std::streamsize done = 0;
        // What is already buffered
        const std::streamsize buffered = std::min<std::streamsize>(egptr() - gptr(), count);
        if (buffered > 0) {
            std::memcpy(dest, gptr(), static_cast<size_t>(buffered));
            gbump(static_cast<int>(buffered));
            done = buffered;
        }
        while (done < count) {
            const int64_t offset = m_base + (gptr() - eback());
            const std::streamsize want = count - done;
            if (want >= static_cast<std::streamsize>(sizeof(m_buf))) {
                // Large read: straight into the destination
                const ssize_t n = ReadAt(dest + done, static_cast<size_t>(want), offset);
                if (n <= 0) {
                    break;
                }
                done += n;
                m_base = offset + n;
                setg(m_buf, m_buf, m_buf);
            } else {
                if (underflow() == traits_type::eof()) {
                    break;
                }
                const std::streamsize take = std::min<std::streamsize>(egptr() - gptr(), want);
                std::memcpy(dest + done, gptr(), static_cast<size_t>(take));
                gbump(static_cast<int>(take));
                done += take;
            }
        }
        return done;
    }

    pos_type seekoff(off_type off, std::ios_base::seekdir dir, std::ios_base::openmode which) override {
        if (!(which & std::ios_base::in)) {
            return pos_type(off_type(-1));
        }
        const int64_t current = m_base + (gptr() - eback());
        int64_t target;
        switch (dir) {
        case std::ios_base::beg: target = off; break;
        case std::ios_base::cur: target = current + off; break;
        case std::ios_base::end: target = m_size + off; break;
        default: return pos_type(off_type(-1));
        }
        if (target < 0) {
            return pos_type(off_type(-1));
        }
        if (target != current) {
            m_base = target;
            setg(m_buf, m_buf, m_buf); // empty: the next read refills from the new position
        }
        return pos_type(off_type(target));
    }

    pos_type seekpos(pos_type pos, std::ios_base::openmode which) override {
        return seekoff(off_type(pos), std::ios_base::beg, which);
    }

private:
    ssize_t ReadAt(void *buffer, size_t size, int64_t offset) const {
        for (;;) {
            const ssize_t n = pread(m_fd, buffer, size, static_cast<off_t>(offset));
            if (n < 0 && errno == EINTR) {
                continue;
            }
            return n;
        }
    }

    int m_fd;
    int64_t m_size = 0;
    int64_t m_base = 0; // file offset of the first byte of m_buf (or of the next read when the buffer is empty)
    char m_buf[64 * 1024];
};

#endif

// A read-only binary input stream opened from a path: a drop-in for `std::ifstream{path, std::ios::binary}`. A symlink to
// /proc/self/fd/N is read through the descriptor (see the top of this file); any other path opens normally. Like an
// ifstream, it converts to false when the file could not be opened (errno is left set).
class InputFile final : public std::istream {
public:
    explicit InputFile(const std::filesystem::path &path) : std::istream(nullptr) {
#ifdef GB_PROC_FD
        if (const int procFd = ProcFdOf(path); procFd >= 0) {
            const int fd = fcntl(procFd, F_DUPFD_CLOEXEC, 0);
            if (fd >= 0) {
                m_buf = std::make_unique<FdStreamBuf>(fd);
            }
        }
#endif
        if (!m_buf) {
            auto file = std::make_unique<std::filebuf>();
            if (file->open(path, std::ios::in | std::ios::binary)) {
                m_buf = std::move(file);
            }
        }
        if (m_buf) {
            rdbuf(m_buf.get()); // also clears the stream state
        } else {
            setstate(std::ios::failbit);
        }
    }

    InputFile(const InputFile &) = delete;
    InputFile &operator=(const InputFile &) = delete;

private:
    std::unique_ptr<std::streambuf> m_buf;
};

} // namespace gb
