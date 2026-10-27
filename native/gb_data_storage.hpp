#pragma once
#include <cstdio>
#include <filesystem>
#include <istream>
#include <ostream>
#include <streambuf>
#include <sys/stat.h>

namespace gb {
bool IsDataPath(const char* path);
int DataOpen(const char* path, int flags, int mode = 0600);
int DataStat(const char* path, struct stat* result);
int DataRename(const char* from, const char* to);
int DataMkdir(const char* path, int mode = 0700);

// Streams use stdio descriptors supplied by SAF, rather than libc++ reopening a URI/path.
class DataStreamBuffer final : public std::streambuf {
    FILE* file;
protected:
    int_type underflow() override {
        int c = std::fgetc(file);
        if (c == EOF) return traits_type::eof();
        std::ungetc(c, file);
        return c;
    }
    int_type uflow() override { int c = std::fgetc(file); return c == EOF ? traits_type::eof() : c; }
    std::streamsize xsgetn(char* s, std::streamsize n) override { return std::fread(s, 1, n, file); }
    std::streamsize xsputn(const char* s, std::streamsize n) override { return std::fwrite(s, 1, n, file); }
    int_type overflow(int_type c) override { return traits_type::eq_int_type(c, traits_type::eof()) ? traits_type::not_eof(c) : std::fputc(c, file); }
    int sync() override { return std::fflush(file); }
    pos_type seekoff(off_type off, std::ios_base::seekdir way, std::ios_base::openmode) override {
        if (fseeko(file, off, way == std::ios::beg ? SEEK_SET : way == std::ios::cur ? SEEK_CUR : SEEK_END)) return pos_type(-1);
        return ftello(file);
    }
    pos_type seekpos(pos_type pos, std::ios_base::openmode mode) override { return seekoff(pos, std::ios::beg, mode); }
public:
    explicit DataStreamBuffer(FILE* f) : file(f) {}
    bool valid() const { return file != nullptr; }
    ~DataStreamBuffer() override { if (file) std::fclose(file); }
};
class DataInputFile : public std::istream {
    DataStreamBuffer buffer;
public:
    explicit DataInputFile(const std::filesystem::path& p, std::ios::openmode = std::ios::binary)
        : std::istream(nullptr), buffer(std::fopen(p.c_str(), "rb")) {
        if (buffer.valid()) rdbuf(&buffer);
    }
};
class DataOutputFile : public std::ostream {
    DataStreamBuffer buffer;
public:
    explicit DataOutputFile(const std::filesystem::path& p, std::ios::openmode = std::ios::binary)
        : std::ostream(nullptr), buffer(std::fopen(p.c_str(), "wb")) { if (buffer.valid()) rdbuf(&buffer); }
};
}
