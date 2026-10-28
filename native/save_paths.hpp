#pragma once
// Save paths shared with the Kotlin side (gb.frame.SaveData / SaveStateFiles); keep the two in step.
#include <string>
#include <string_view>

namespace gb {

/** Folder name for a game: its file name without extension, minus characters folders can't hold. */
inline std::string gameFolderName(const std::string &gamePath) {
    std::string name = gamePath.substr(gamePath.find_last_of('/') + 1);
    if (auto dot = name.find_last_of('.'); dot != std::string::npos) name.resize(dot);
    for (char &c : name) {
        if (std::string_view("\\/:*?\"<>|").find(c) != std::string_view::npos) c = '_';
    }
    const auto first = name.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) return "game";
    return name.substr(first, name.find_last_not_of(" \t\r\n") - first + 1);
}

/** `savestates/<Game>/<Game>_<slot>.state`, relative to the data folder. */
inline std::string stateRelativePath(const std::string &gamePath, const std::string &slot) {
    const auto name = gameFolderName(gamePath);
    return "savestates/" + name + "/" + name + "_" + slot + ".state";
}

} // namespace gb
