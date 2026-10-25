<#
.SYNOPSIS
  Non-interactive device tooling for the GB family of emulator apps (adb + gh), meant for scripted runs (Claude Code) as well
  as by hand. Model: GBFrame/tools/emu-test.ps1 - copy it to the app (GBSat: tools/gbsat.ps1) and adjust the block under
  "# ---- app". Same actions as tools/emu-test.bat, which is the interactive menu.
  Every command runs without prompts and writes its results to test-output\<timestamp>-<command>[-label]\ (git-ignored);
  the last line printed is "OUT: <folder>". 

.DESCRIPTION
  Commands
    Check       device and app status (model, Android, installed version, running pid); no files
    Install     download the latest successful CI build (or -Run <id>, or -Profiling for the profiling build) and install it
    Launch      start the app          Stop   force-stop the app          Wake  wake and unlock the screen
    Logcat      capture the log for -Seconds (0 = just dump the buffer); splits app lines, errors and the crash buffer
    Crash       crash buffer, ANR/crash entries of the dropbox, and the app's native tombstone summary lines
    Screenshot  screen capture (png)
    PerfLog     clear the perf log, start the app, wait -Seconds, pull perf-log.csv and summarize it (0 = pull only)
    Scroll      library scroll smoothness: gfxinfo reset, wait -Seconds, dump
    Profile     simpleperf CPU profile for -Seconds (needs the profiling build: Install -Profiling) with per-thread/function reports
    PgoPull     pull the PGO training data (PGO training build) as a zip
    Diag        set an A/B diagnostic property: -Key sh2_plain|vdp2_one_fb|vdp_nobatch|all  -Value 0|1

  Options: -Serial <adb serial>  -Seconds <n>  -Label <text, e.g. the game name>  -Run <run id>  -Profiling  -NoLaunch
           -Clear (Logcat: clear the buffer first)  -OutDir <folder>

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\gbsat.ps1 Check
  powershell -NoProfile -ExecutionPolicy Bypass -File tools\gbsat.ps1 Logcat -Clear -Seconds 60 -Label "Panzer Dragoon"
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet('Check', 'Install', 'Launch', 'Stop', 'Wake', 'Logcat', 'Crash', 'Screenshot', 'PerfLog', 'Scroll', 'Profile', 'PgoPull', 'Diag')]
    [string]$Command,
    [string]$Serial = '',
    [int]$Seconds = -1,
    [string]$Label = '',
    [string]$Run = '',
    [switch]$Profiling,
    [switch]$NoLaunch,
    [switch]$Clear,
    [string]$Key = '',
    [string]$Value = '1',
    [string]$OutDir = ''
)

# ---- app (tools/check-app-id.sh checks that these match build.gradle.kts, the manifest and the CMake APP_ID) -------------
$Title = 'GBSat'
$Name = 'gbsat'
$Repo = 'mindspot7/GBSat'
$Branch = 'android-port'
$App = 'app.gbsat.emulator'
# APP is both applicationId and namespace; Activity is the fully qualified source class (it may use a different package)
$Activity = 'app.gbsat.android.MainActivity'
$ProfileWorkflow = 'profile.yml'
$ApkWorkflow = 'android.yml'
$EmuThread = 'Thread-3'
$DiagKeys = @('sh2_plain', 'vdp2_one_fb', 'vdp_nobatch')
# Tags of the app's own log lines (Kotlin and native) plus the system tags that report its crashes
$LogTags = 'GBSat\S*|GBFrame\S*|GBNative\S*|AndroidRuntime|DEBUG|libc'
# --------------------------------------------------------------------------------------------------------------------
$Media = "/sdcard/Android/media/$App"
$RepoRoot = Split-Path -Parent $PSScriptRoot

function Fail([string]$Message) {
    Write-Host "ERRO: $Message" -ForegroundColor Red
    exit 1
}

# Runs adb (with -s when -Serial was given) and returns its output lines; stderr stays visible
function Invoke-Adb {
    # No param block on purpose: every token (-d, -v, -f ...) arrives untouched in $args
    $full = @()
    if ($Serial) { $full += @('-s', $Serial) }
    $full += $args
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & adb @full } finally { $ErrorActionPreference = $old }
}

function Test-Device {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { Fail 'adb nao encontrado no PATH (instale o platform-tools).' }
    $state = (Invoke-Adb get-state 2>&1 | Out-String).Trim()
    if ($state -ne 'device') {
        Fail "Aparelho nao encontrado ou nao autorizado (adb get-state: $state). Conecte por USB, aceite a depuracao e, com varios aparelhos, use -Serial."
    }
    Invoke-Adb shell input keyevent KEYCODE_WAKEUP 2>&1 | Out-Null
    Invoke-Adb shell wm dismiss-keyguard 2>&1 | Out-Null
}

function New-OutDir {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $folder = "$stamp-$($Command.ToLower())"
    if ($Label) { $folder += '-' + ($Label -replace '[^A-Za-z0-9]+', '_').Trim('_') }
    if ($OutDir) { $dir = $OutDir } else { $dir = Join-Path $RepoRoot (Join-Path 'test-output' $folder) }
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    return $dir
}

function Get-Prop([string]$Prop) {
    return (Invoke-Adb shell getprop $Prop | Out-String).Trim()
}

function Get-AppPids {
    $text = (Invoke-Adb shell pidof $App 2>&1 | Out-String).Trim()
    if (-not $text) { return @() }
    return @($text -split '\s+' | Where-Object { $_ -match '^\d+$' })
}

function Get-DeviceInfo {
    $lines = @()
    $lines += 'device: ' + (Get-Prop 'ro.product.manufacturer') + ' ' + (Get-Prop 'ro.product.model')
    $lines += 'android: ' + (Get-Prop 'ro.build.version.release') + ' (sdk ' + (Get-Prop 'ro.build.version.sdk') + ')'
    $lines += 'soc: ' + (Get-Prop 'ro.soc.model') + ' / ' + (Get-Prop 'ro.hardware') + ' / ' + (Get-Prop 'ro.product.cpu.abi')
    $lines += 'display: ' + ((Invoke-Adb shell wm size | Out-String).Trim() -replace '\s+', ' ') + ' ' + ((Invoke-Adb shell wm density | Out-String).Trim() -replace '\s+', ' ')
    $pkg = Invoke-Adb shell dumpsys package $App | Select-String -Pattern 'versionName=|versionCode=|lastUpdateTime=' | ForEach-Object { $_.Line.Trim() }
    if ($pkg) { $lines += ('app: ' + $App); $lines += ($pkg | ForEach-Object { '  ' + $_ }) } else { $lines += "app: $App NAO INSTALADO" }
    $appPids = Get-AppPids
    if ($appPids.Count -gt 0) { $lines += ('running: pid ' + ($appPids -join ',')) } else { $lines += 'running: no' }
    return $lines
}

function Start-App {
    Invoke-Adb shell am start -n "$App/$Activity" 2>&1 | Out-Null
}

function Get-Num($Text) {
    $d = 0.0
    if ([double]::TryParse([string]$Text, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$d)) { return $d }
    return $null
}

function Get-Stat($Rows, [string]$Column, [string]$What) {
    $values = @($Rows | ForEach-Object { Get-Num $_.$Column } | Where-Object { $null -ne $_ })
    if ($values.Count -eq 0) { return 'n/a' }
    $m = $values | Measure-Object -Average -Minimum -Maximum -Sum
    switch ($What) {
        'avg' { return ('{0:N1}' -f $m.Average) }
        'min' { return ('{0:N1}' -f $m.Minimum) }
        'max' { return ('{0:N1}' -f $m.Maximum) }
        'sum' { return ('{0:N0}' -f $m.Sum) }
    }
}

function Write-OutPath([string]$Dir) {
    Write-Host "OUT: $Dir"
}

switch ($Command) {

    'Check' {
        Test-Device
        Get-DeviceInfo | ForEach-Object { Write-Host $_ }
    }

    'Wake' {
        Test-Device
        Write-Host 'Aparelho acordado.'
    }

    'Launch' {
        Test-Device
        Start-App
        Write-Host "$Title iniciado."
    }

    'Stop' {
        Test-Device
        Invoke-Adb shell am force-stop $App 2>&1 | Out-Null
        Write-Host "$Title parado."
    }

    'Install' {
        Test-Device
        if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { Fail 'gh (GitHub CLI) nao encontrado no PATH.' }
        & gh auth status 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail 'gh nao autenticado: rode "gh auth login" uma vez (escopos repo e workflow).' }
        if ($Profiling) { $workflow = $ProfileWorkflow } else { $workflow = $ApkWorkflow }
        if (-not $Run) {
            $Run = (& gh run list -R $Repo --branch $Branch --workflow $workflow --status success --limit 1 --json databaseId --jq '.[0].databaseId' | Out-String).Trim()
        }
        if (-not $Run) { Fail "Nenhuma execucao bem-sucedida de $workflow em $Branch." }
        $tmp = Join-Path ([IO.Path]::GetTempPath()) "$Name-apk-$Run"
        if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
        & gh run download $Run -R $Repo -D $tmp
        if ($LASTEXITCODE -ne 0) { Fail "gh run download falhou para a execucao $Run." }
        $apk = Get-ChildItem -Path $tmp -Recurse -Filter '*.apk' | Select-Object -First 1
        if (-not $apk) { Fail "Nenhum .apk nos artefatos da execucao $Run." }
        $dir = New-OutDir
        Write-Host "Instalando $($apk.Name) (execucao $Run)..."
        $result = (Invoke-Adb install -r $apk.FullName 2>&1 | Out-String).Trim()
        Set-Content -Path (Join-Path $dir 'install.txt') -Value @("run: $Run", "apk: $($apk.Name)", "workflow: $workflow", $result)
        Write-Host $result
        if ($result -notmatch 'Success') {
            Fail 'Instalacao falhou. Com INSTALL_FAILED_UPDATE_INCOMPATIBLE o app instalado tem outra assinatura: NAO desinstale sem avisar o dono (apaga os dados do app).'
        }
        Remove-Item -Recurse -Force $tmp
        Invoke-Adb shell cmd package compile -m speed-profile -f $App 2>&1 | Out-Null
        if (-not $NoLaunch) { Start-App }
        Write-OutPath $dir
    }

    'Logcat' {
        Test-Device
        $dir = New-OutDir
        if ($Seconds -lt 0) { $Seconds = 30 }
        Set-Content -Path (Join-Path $dir 'device.txt') -Value (Get-DeviceInfo)
        if ($Clear) { Invoke-Adb logcat -c 2>&1 | Out-Null }
        $raw = Join-Path $dir 'logcat-raw.txt'
        if ($Seconds -eq 0) {
            Invoke-Adb logcat -b all -d -v threadtime | Set-Content -Path $raw
        } else {
            $a = @()
            if ($Serial) { $a += @('-s', $Serial) }
            $a += @('logcat', '-b', 'all', '-v', 'threadtime')
            Write-Host "Capturando o logcat por $Seconds s..."
            $proc = Start-Process -FilePath 'adb' -ArgumentList $a -RedirectStandardOutput $raw -NoNewWindow -PassThru
            Start-Sleep -Seconds $Seconds
            if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
            Start-Sleep -Milliseconds 800
        }
        # App lines: its tags, its process ids, or its package name
        $tagRegex = " [VDIWEF] ($LogTags)\s*:"
        $appPids = Get-AppPids
        $pidRegex = $null
        if ($appPids.Count -gt 0) { $pidRegex = '^\S+\s+\S+\s+(' + ($appPids -join '|') + ')\s' }
        $all = @(Get-Content -Path $raw -ErrorAction SilentlyContinue)
        $mine = @($all | Where-Object { $_ -match $tagRegex -or ($pidRegex -and $_ -match $pidRegex) -or $_.Contains($App) })
        $bad = @($mine | Where-Object { $_ -match ' [EF] ' -or $_ -match 'FATAL EXCEPTION|Fatal signal|SIGSEGV|SIGABRT|ANR in|backtrace:|Vulkan.*(ERROR|fail)' })
        Set-Content -Path (Join-Path $dir 'logcat-app.txt') -Value $mine
        Set-Content -Path (Join-Path $dir 'logcat-errors.txt') -Value $bad
        Invoke-Adb logcat -b crash -d -v threadtime | Set-Content -Path (Join-Path $dir 'logcat-crash-buffer.txt')
        Write-Host ("linhas: {0} total, {1} do app, {2} erros/crashes" -f $all.Count, $mine.Count, $bad.Count)
        Write-OutPath $dir
    }

    'Crash' {
        Test-Device
        $dir = New-OutDir
        Invoke-Adb logcat -b crash -d -v threadtime | Set-Content -Path (Join-Path $dir 'crash-buffer.txt')
        foreach ($kind in @('data_app_crash', 'data_app_native_crash', 'data_app_anr')) {
            Invoke-Adb shell dumpsys dropbox --print $kind 2>&1 | Select-Object -First 400 | Set-Content -Path (Join-Path $dir "dropbox-$kind.txt")
        }
        Write-Host ('crash buffer: ' + @(Get-Content (Join-Path $dir 'crash-buffer.txt')).Count + ' linhas')
        Write-OutPath $dir
    }

    'Screenshot' {
        Test-Device
        $dir = New-OutDir
        $remote = "/sdcard/$Name-shot.png"
        Invoke-Adb shell screencap -p $remote 2>&1 | Out-Null
        Invoke-Adb pull $remote (Join-Path $dir 'screen.png') 2>&1 | Out-Null
        Invoke-Adb shell rm $remote 2>&1 | Out-Null
        if (-not (Test-Path (Join-Path $dir 'screen.png'))) { Fail 'Nao foi possivel capturar a tela.' }
        Write-OutPath $dir
    }

    'PerfLog' {
        Test-Device
        $dir = New-OutDir
        if ($Seconds -lt 0) { $Seconds = 60 }
        if ($Seconds -gt 0) {
            Invoke-Adb shell rm -f "$Media/perf-log.csv" 2>&1 | Out-Null
            Start-App
            Write-Host "No aparelho: jogue por ~$Seconds s (luta/fase, nao so menus). Coletando..."
            Start-Sleep -Seconds $Seconds
        }
        $csv = Join-Path $dir 'perf-log.csv'
        Invoke-Adb pull "$Media/perf-log.csv" $csv 2>&1 | Out-Null
        if (-not (Test-Path $csv)) { Fail 'Nenhum perf-log encontrado: o jogo rodou alguns segundos?' }
        $rows = @(Import-Csv -Path $csv)
        $summary = @()
        $summary += "rows: $($rows.Count)"
        if ($rows.Count -gt 0) {
            $summary += "game: $($rows[0].game)   device: $($rows[0].device)   renderer: $($rows[0].renderer) x$($rows[0].scale)   core: $($rows[0].emu_core)"
            $summary += "emu_fps avg/min: $(Get-Stat $rows 'emu_fps' 'avg') / $(Get-Stat $rows 'emu_fps' 'min')"
            $summary += "shown_fps avg/min: $(Get-Stat $rows 'shown_fps' 'avg') / $(Get-Stat $rows 'shown_fps' 'min')"
            $summary += "emu_ms_per_frame avg: $(Get-Stat $rows 'emu_ms_per_frame' 'avg')   worst_frame_ms max: $(Get-Stat $rows 'worst_frame_ms' 'max')   slow_frames sum: $(Get-Stat $rows 'slow_frames' 'sum')"
            $summary += "audio_underruns max: $(Get-Stat $rows 'audio_underruns' 'max')   audio_xruns max: $(Get-Stat $rows 'audio_xruns' 'max')   auto_skip_pct avg: $(Get-Stat $rows 'auto_skip_pct' 'avg')"
            $summary += "thermal_status last: $($rows[-1].thermal_status)   headroom last: $($rows[-1].thermal_headroom)"
            $summary += "settings last: $($rows[-1].settings)"
        }
        Set-Content -Path (Join-Path $dir 'summary.txt') -Value $summary
        $summary | ForEach-Object { Write-Host $_ }
        Write-OutPath $dir
    }

    'Scroll' {
        Test-Device
        $dir = New-OutDir
        if ($Seconds -lt 0) { $Seconds = 20 }
        Start-App
        Write-Host "Deixe o $Title na biblioteca (fora de um jogo) e role (segure direita ~10 s e depois esquerda). Medindo $Seconds s..."
        Invoke-Adb shell dumpsys gfxinfo $App reset 2>&1 | Out-Null
        Start-Sleep -Seconds $Seconds
        $file = Join-Path $dir 'gfxinfo.txt'
        Invoke-Adb shell dumpsys gfxinfo $App | Set-Content -Path $file
        Select-String -Path $file -Pattern 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile' |
            ForEach-Object { $_.Line.Trim() } | Tee-Object -FilePath (Join-Path $dir 'summary.txt') | ForEach-Object { Write-Host $_ }
        Write-OutPath $dir
    }

    'Profile' {
        Test-Device
        $dir = New-OutDir
        if ($Seconds -lt 0) { $Seconds = 20 }
        $data = "/data/local/tmp/$Name.data"
        Write-Host "Gravando $Seconds s de CPU (o jogo deve estar rodando, com a build de profiling instalada)..."
        $rec = (Invoke-Adb shell simpleperf record --app $App -e cpu-clock -f 2000 --duration $Seconds -o $data 2>&1 | Out-String)
        if ($rec -match 'error|fail|not found|cannot' -and $rec -notmatch 'Samples recorded') {
            Fail "A gravacao falhou: a build de profiling esta instalada (Install -Profiling) e o jogo rodando? $rec"
        }
        $report = @("=== $Name profile: $Label ===")
        $report += Get-DeviceInfo
        $report += ''
        $report += '=== Per thread ==='
        $report += Invoke-Adb shell simpleperf report -i $data --sort comm --percent-limit 0.5
        $report += ''
        $report += '=== Per function ==='
        $report += Invoke-Adb shell simpleperf report -i $data --sort comm,symbol --percent-limit 0.2
        $report += ''
        $report += "=== Emulation thread ($EmuThread) by address ==="
        $report += Invoke-Adb shell simpleperf report -i $data --comms $EmuThread --sort dso,vaddr_in_file --percent-limit 0.05 2>&1
        Set-Content -Path (Join-Path $dir 'profile.txt') -Value $report
        Invoke-Adb shell rm $data 2>&1 | Out-Null
        Write-OutPath $dir
    }

    'PgoPull' {
        Test-Device
        $dir = New-OutDir
        $local = Join-Path $dir 'pgo'
        Invoke-Adb pull "$Media/pgo" $local 2>&1 | Out-Null
        if (-not (Test-Path $local) -or -not (Get-ChildItem -Path $local -Recurse -Filter '*.profraw')) {
            Fail 'Sem dados de treino: jogue mais de um minuto com a build de treino PGO e tente de novo.'
        }
        Compress-Archive -Force -Path (Join-Path $local '*') -DestinationPath (Join-Path $dir "$Name-pgo.zip")
        Remove-Item -Recurse -Force $local
        Write-OutPath $dir
    }

    'Diag' {
        Test-Device
        if (-not $Key) { Fail "Informe -Key (um de: $($DiagKeys -join ', '), ou all) e -Value 0|1." }
        if ($Key -eq 'all') { $targets = $DiagKeys } elseif ($DiagKeys -contains $Key) { $targets = @($Key) } else { Fail "Chave desconhecida: $Key (validas: $($DiagKeys -join ', '), all)." }
        foreach ($k in $targets) {
            Invoke-Adb shell setprop "debug.$Name.$k" $Value 2>&1 | Out-Null
            Write-Host "debug.$Name.$k = $Value"
        }
        Write-Host 'Vale em ~1 s com o jogo rodando e volta ao normal se o aparelho reiniciar; o perf-log marca diag=...'
    }
}
exit 0
