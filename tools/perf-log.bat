@echo off
rem Copies the app's performance log from the phone (USB debugging on). One line per second while a game runs:
rem game, renderer, resolution scale, threaded options, frame skip, fast forward, emulated fps, shown fps, ms per frame.
rem Result: %NAME%-perf-log.csv in this folder - send it for analysis.
setlocal
rem ---- app settings (edit when copying to another app) ----
set APP=app.gbsat.android
set NAME=gbsat
rem ---------------------------------------------------------
cd /d "%~dp0"
where adb >nul 2>nul
if errorlevel 1 (
  echo adb not found. Copy this file to the platform-tools folder and run it there.
  pause
  exit /b 1
)
adb pull /sdcard/Android/media/%APP%/perf-log.csv %NAME%-perf-log.csv
if not exist %NAME%-perf-log.csv (
  echo.
  echo No log found. Play a game for a few seconds first.
  pause
  exit /b 1
)
echo.
echo Done: %CD%\%NAME%-perf-log.csv
echo To start a new log, run: adb shell rm /sdcard/Android/media/%APP%/perf-log.csv
pause
