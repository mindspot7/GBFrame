[CmdletBinding()]
param([string]$Serial, [string]$NdkDirectory)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (-not $NdkDirectory) {
    $NdkDirectory = (Get-ChildItem -LiteralPath (Join-Path $sdk 'ndk') -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
}
$compiler = Join-Path $NdkDirectory 'toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android28-clang++.cmd'
$output = Join-Path $env:TEMP 'gbframe-auto-skip-test'
& $compiler -std=c++17 -static-libstdc++ -pthread -I (Join-Path $repo 'native') (Join-Path $repo 'native/auto_skip.cpp') (Join-Path $repo 'native/tests/auto_skip_test.cpp') -o $output
if ($LASTEXITCODE -ne 0) { throw 'Skip Engine native tests did not compile.' }
$adb = Join-Path $sdk 'platform-tools/adb.exe'
$device = @(); if ($Serial) { $device = @('-s', $Serial) }
& $adb @device push $output /data/local/tmp/gbframe-auto-skip-test
if ($LASTEXITCODE -ne 0) { throw 'Could not transfer native tests to the Android device.' }
& $adb @device shell chmod 755 /data/local/tmp/gbframe-auto-skip-test
& $adb @device shell /data/local/tmp/gbframe-auto-skip-test
if ($LASTEXITCODE -ne 0) { throw 'Skip Engine native tests failed.' }
