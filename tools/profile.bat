@echo off
rem CPU profile: records 20 seconds of the running game with simpleperf (built into Android)
rem and saves %NAME%-profile.txt next to this script.
rem Before running: install the profiling build, start a game, connect the device by USB (USB debugging on).
setlocal
rem ---- app settings (edit when copying to another app) ----
set APP=app.gbsat.android
set NAME=gbsat
rem ---------------------------------------------------------
set PKG=%APP%
set OUT=%~dp0%NAME%-profile.txt
where adb >nul 2>&1 || (echo adb not found: run this from the platform-tools folder or add it to PATH & pause & exit /b 1)
adb get-state >nul 2>&1 || (echo No device: connect it by USB and accept the debugging prompt & pause & exit /b 1)
echo Recording 20 seconds... keep playing.
adb shell simpleperf record --app %PKG% -e cpu-clock -f 2000 --duration 20 -o /data/local/tmp/%NAME%.data
if errorlevel 1 (echo Recording failed: is the profiling build installed and the game running? & pause & exit /b 1)
echo Building the report...
> "%OUT%" echo === profile ===
>> "%OUT%" adb shell getprop ro.product.model
>> "%OUT%" adb shell getprop ro.build.version.release
>> "%OUT%" echo.
>> "%OUT%" echo === Per thread ===
>> "%OUT%" adb shell simpleperf report -i /data/local/tmp/%NAME%.data --sort comm --percent-limit 0.5
>> "%OUT%" echo.
>> "%OUT%" echo === Per function ===
>> "%OUT%" adb shell simpleperf report -i /data/local/tmp/%NAME%.data --sort comm,symbol --percent-limit 0.2
adb shell rm /data/local/tmp/%NAME%.data
echo Done: %OUT%
pause
