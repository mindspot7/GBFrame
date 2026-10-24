#pragma once

// A/B diagnostics switched with adb while a game runs (no reinstall): the app reads Android system properties
// "debug.<app>.<name>" (writable by adb without root; reset when the device reboots), e.g. once per second, and the
// performance log marks the switches in use (diag=...). The test script (tools/emu-test.bat, option 6) sets them.

#include <sys/system_properties.h>

#include <string>

namespace gb {

/// True when the property is set to "1".
inline bool PropOn(const char *key) {
    char v[PROP_VALUE_MAX] = {};
    return __system_property_get(key, v) > 0 && v[0] == '1';
}

/// True when "debug.<app>.<name>" is "1" (app: the short app name, e.g. "gbsat").
inline bool DiagOn(const char *app, const char *name) {
    return PropOn((std::string("debug.") + app + "." + name).c_str());
}

} // namespace gb
