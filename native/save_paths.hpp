#pragma once
#include <string>

namespace gb {
// Must match SaveData.gameFolderName and DataLayout on the JVM.
inline std::string gameFolderName(std::string path) {
    const auto slash = path.find_last_of('/');
    if (slash != std::string::npos) path.erase(0, slash + 1);
    const auto dot = path.find_last_of('.');
    if (dot != std::string::npos) path.resize(dot);
    for (char& ch : path) if (std::string("\\/:*?\"<>|").find(ch) != std::string::npos) ch = '_';
    const auto first = path.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) return "game";
    path.erase(0, first);
    const auto last = path.find_last_not_of(" \t\r\n.");
    if (last == std::string::npos) return "game";
    path.resize(last + 1);
    return path;
}
inline std::string stateRelativePath(const std::string& game, const std::string& slot) {
    const auto name = gameFolderName(game);
    return "savestates/" + name + "/" + name + "_" + slot + ".state";
}
}
