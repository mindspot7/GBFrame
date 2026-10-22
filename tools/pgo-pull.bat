@echo off
rem Copies the PGO training data from the phone (USB debugging on, PGO training build installed and played).
rem Result: %NAME%-pgo.zip in this folder - send it to be merged into apps/android/pgo/gbsat.profdata.
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
echo Files on the phone:
adb shell ls -l /sdcard/Android/media/%APP%/pgo
if exist %NAME%-pgo rmdir /s /q %NAME%-pgo
adb pull /sdcard/Android/media/%APP%/pgo %NAME%-pgo
if not exist %NAME%-pgo\*.profraw (
  echo.
  echo No training data found. Play with the PGO training build for over a minute, then run this again.
  pause
  exit /b 1
)
powershell -NoProfile -Command "Compress-Archive -Force -Path %NAME%-pgo\* -DestinationPath %NAME%-pgo.zip"
echo.
echo Done: %CD%\%NAME%-pgo.zip
pause
