@echo off
rem Ferramentas de teste no aparelho para os apps da familia GB (GBFrame): fica na pasta platform-tools do adb, com o
rem aparelho em depuracao USB. Um menu so: baixar/instalar build, perf-log, profile de CPU, medir rolagem, dados do
rem PGO, diagnostico A/B, acordar o aparelho e capturar a tela. Os arquivos saem com o nome do jogo; avise no chat.
rem Modelo: GBFrame/tools/emu-test.bat. Copie para o app e ajuste o bloco abaixo.
setlocal EnableExtensions
rem ---- app (ajuste ao copiar) -----------------------------------------------------------------------------------------
set TITLE=GBSat
set NAME=gbsat
set REPO=mindspot7/GBSat
set BRANCH=android-port
set APP=app.gbsat.android
rem Workflow da build de profiling, tag do log do Android com a linha "Game: <nome>" e thread de emulacao (profile)
set PROFILE_WF=profile.yml
set LOGTAG=GBSat
set EMU_THREAD=Thread-3
rem Chaves de diagnostico A/B (propriedades debug.NAME.chave, lidas pelo app a cada segundo; ate 3)
set DIAG1=sh2_plain
set DIAG1_TXT=SH-2 antigo ^(interpretador simples^)
set DIAG2=vdp2_one_fb
set DIAG2_TXT=VDP2 antigo ^(uma imagem so^)
set DIAG3=vdp_nobatch
set DIAG3_TXT=VDP sem lotes ^(comandos e linhas um a um^)
rem ---------------------------------------------------------------------------------------------------------------------
set MEDIA=/sdcard/Android/media/%APP%
cd /d "%~dp0"

where adb >nul 2>nul
if errorlevel 1 (
  echo adb nao encontrado: este arquivo precisa ficar na pasta platform-tools.
  pause & exit /b 1
)

:menu
echo.
echo ===== %TITLE% =====
echo  1. Baixar e instalar a ultima build
echo  2. Teste de desempenho de um jogo ^(perf-log^)
echo  3. Profile de CPU de jogos ^(instala a build de profiling^)
echo  4. Medir a rolagem da biblioteca
echo  5. Puxar os dados do treino PGO
echo  6. Diagnostico A/B ^(com o jogo rodando^)
echo  7. Acordar o aparelho
echo  8. Capturar a tela do jogo ^(PNG^)
echo  0. Sair
choice /c 123456780 /n /m "Escolha: "
if errorlevel 9 exit /b 0
if errorlevel 8 goto captura
if errorlevel 7 goto acordar
if errorlevel 6 goto diag
if errorlevel 5 goto pgo
if errorlevel 4 goto rolagem
if errorlevel 3 goto profile
if errorlevel 2 goto perflog
call :instalar "Android APK"
goto menu

rem ---------------------------------------------------------------------------------------------------------------------
:perflog
call :aparelho || goto menu
adb shell rm -f %MEDIA%/perf-log.csv
adb shell am start -n %APP%/.MainActivity >nul
echo.
echo No aparelho: jogue o jogo por ~1 minuto ^(luta/fase, nao so menus^).
echo Para comparar uma opcao ^(renderer, audio em thread^), troque pelo menu do jogo e jogue mais na mesma cena.
echo Depois volte aqui e aperte uma tecla.
pause >nul
if exist %NAME%-perf-log.tmp del %NAME%-perf-log.tmp
adb pull %MEDIA%/perf-log.csv %NAME%-perf-log.tmp >nul
if not exist %NAME%-perf-log.tmp (
  echo Nenhum log encontrado: o jogo rodou alguns segundos?
  goto menu
)
rem Nome do jogo pela coluna "game" do proprio log ^(o log do Android pode ja nao ter a linha "Game:"^)
set GAME=
for /f "usebackq delims=" %%G in (`powershell -NoProfile -Command "$r = Import-Csv '%NAME%-perf-log.tmp' | Select-Object -First 1; if ($r) { ($r.game -replace '[^A-Za-z0-9]+','_').Trim('_') }"`) do set "GAME=%%G"
if not defined GAME call :nomejogo
call :unico %NAME%-perf-log_%GAME% csv
move /y %NAME%-perf-log.tmp "%OUT%" >nul
echo Pronto: %OUT%
goto menu

rem ---------------------------------------------------------------------------------------------------------------------
:profile
call :instalar "Profiling build" || goto menu
:profile_proximo
echo.
echo Abra o jogo no aparelho e jogue; aperte uma tecla aqui para gravar 20 segundos.
pause >nul
echo Gravando 20 s... continue jogando.
adb shell simpleperf record --app %APP% -e cpu-clock -f 2000 --duration 20 -o /data/local/tmp/%NAME%.data
if errorlevel 1 (
  echo A gravacao falhou: a build de profiling esta instalada e o jogo rodando?
  goto menu
)
call :nomejogo
call :unico %NAME%-profile_%GAME% txt
echo Montando o relatorio...
> "%OUT%" echo === %NAME% profile: %GAME% ===
>> "%OUT%" adb shell getprop ro.product.model
>> "%OUT%" adb shell getprop ro.build.version.release
>> "%OUT%" echo.
>> "%OUT%" echo === Per thread ===
>> "%OUT%" adb shell simpleperf report -i /data/local/tmp/%NAME%.data --sort comm --percent-limit 0.5
>> "%OUT%" echo.
>> "%OUT%" echo === Per function ===
>> "%OUT%" adb shell simpleperf report -i /data/local/tmp/%NAME%.data --sort comm,symbol --percent-limit 0.2
>> "%OUT%" echo.
>> "%OUT%" echo === Emulation thread by address ===
>> "%OUT%" adb shell simpleperf report -i /data/local/tmp/%NAME%.data --comms %EMU_THREAD% --sort dso,vaddr_in_file --percent-limit 0.05 2^>^&1
adb shell rm /data/local/tmp/%NAME%.data
echo Pronto: %OUT%
choice /c SN /m "Gravar outro jogo"
if not errorlevel 2 goto profile_proximo
echo Para voltar a build normal, use a opcao 1.
goto menu

rem ---------------------------------------------------------------------------------------------------------------------
:rolagem
call :aparelho || goto menu
adb shell am start -n %APP%/.MainActivity >nul
echo.
echo Deixe o %TITLE% na biblioteca ^(fora de um jogo^). Ao apertar uma tecla, segure direita ~10 s e depois esquerda,
echo sem tocar na tela; aperte outra tecla quando terminar.
pause >nul
adb shell dumpsys gfxinfo %APP% reset >nul
echo Rolando... aperte uma tecla quando terminar.
pause >nul
adb shell dumpsys gfxinfo %APP% > %NAME%-rolagem.txt
findstr /c:"Total frames rendered" /c:"Janky frames" /c:"50th percentile" /c:"99th percentile" %NAME%-rolagem.txt
echo Pronto: %NAME%-rolagem.txt
goto menu

rem ---------------------------------------------------------------------------------------------------------------------
:pgo
call :aparelho || goto menu
if exist %NAME%-pgo rmdir /s /q %NAME%-pgo
adb pull %MEDIA%/pgo %NAME%-pgo >nul
if not exist %NAME%-pgo\*.profraw (
  echo Sem dados de treino: jogue mais de um minuto com a build de treino PGO e tente de novo.
  goto menu
)
powershell -NoProfile -Command "Compress-Archive -Force -Path %NAME%-pgo\* -DestinationPath %NAME%-pgo.zip"
rmdir /s /q %NAME%-pgo
echo Pronto: %NAME%-pgo.zip
goto menu

rem ---------------------------------------------------------------------------------------------------------------------
:captura
call :aparelho || goto menu
echo.
echo Deixe o jogo na cena desejada ^(com o menu do jogo fechado^) e aperte uma tecla para capturar.
pause >nul
call :nomejogo
call :unico %NAME%-tela_%GAME% png
adb exec-out screencap -p > "%OUT%"
echo Pronto: %OUT%
choice /c SN /m "Capturar outra"
if not errorlevel 2 goto captura
goto menu

rem ---------------------------------------------------------------------------------------------------------------------
:acordar
call :aparelho || goto menu
echo Aparelho acordado.
goto menu

rem ---------------------------------------------------------------------------------------------------------------------
:diag
call :aparelho || goto menu
echo.
echo Troca com o jogo rodando ^(vale em ~1 s, volta ao normal se o aparelho reiniciar^). O perf-log marca diag=... .
echo  1. Tudo normal
if defined DIAG1 echo  2. %DIAG1_TXT%
if defined DIAG2 echo  3. %DIAG2_TXT%
if defined DIAG3 echo  4. %DIAG3_TXT%
echo  5. Todas as chaves ligadas
choice /c 12345 /n /m "Escolha: "
set D1=0
set D2=0
set D3=0
if errorlevel 5 (set D1=1& set D2=1& set D3=1& goto diag_set)
if errorlevel 4 (set D3=1& goto diag_set)
if errorlevel 3 (set D2=1& goto diag_set)
if errorlevel 2 (set D1=1& goto diag_set)
:diag_set
if defined DIAG1 adb shell setprop debug.%NAME%.%DIAG1% %D1%
if defined DIAG2 adb shell setprop debug.%NAME%.%DIAG2% %D2%
if defined DIAG3 adb shell setprop debug.%NAME%.%DIAG3% %D3%
echo %DIAG1%=%D1%  %DIAG2%=%D2%  %DIAG3%=%D3%
goto menu

rem =====================================================================================================================
rem Sub-rotinas

rem Confere se o aparelho esta conectado e autorizado, e acorda a tela ^(destrava se nao houver senha/padrao^)
:aparelho
adb get-state >nul 2>&1
if errorlevel 1 (
  echo Aparelho nao encontrado: conecte por USB e aceite a depuracao no aparelho.
  exit /b 1
)
adb shell input keyevent KEYCODE_WAKEUP >nul 2>&1
adb shell wm dismiss-keyguard >nul 2>&1
exit /b 0

rem Baixa a ultima build verde do workflow %1 ("Android APK" ou "Profiling build"), instala, apaga o .apk e compila o app
:instalar
set "WF=%~1"
call :aparelho || exit /b 1
where gh >nul 2>nul
if errorlevel 1 (
  echo GitHub CLI nao encontrado. Instalando com o winget; depois feche esta janela e abra de novo.
  winget install --id GitHub.cli -e --accept-source-agreements --accept-package-agreements
  pause & exit 1
)
gh auth status >nul 2>nul || gh auth login --hostname github.com --git-protocol https --web
if not "%WF%"=="Profiling build" goto instalar_baixar
rem A build de profiling precisa ser de depois da normal ^(o Android recusa versao mais antiga^)
set NORMAL=
set PROF=
for /f "delims=" %%A in ('gh run list -R %REPO% --branch %BRANCH% --workflow "Android APK" --status success --limit 1 --json createdAt --jq ".[0].createdAt"') do set NORMAL=%%A
for /f "delims=" %%B in ('gh run list -R %REPO% --branch %BRANCH% --workflow "Profiling build" --status success --limit 1 --json createdAt --jq ".[0].createdAt"') do set PROF=%%B
if not "%PROF%" LSS "%NORMAL%" goto instalar_baixar
echo == Gerando uma build de profiling nova ^(uns 5 minutos^)...
gh workflow run %PROFILE_WF% -R %REPO% --ref %BRANCH%
timeout /t 15 /nobreak >nul
set NEWRUN=
for /f "delims=" %%C in ('gh run list -R %REPO% --workflow "Profiling build" --limit 1 --json databaseId --jq ".[0].databaseId"') do set NEWRUN=%%C
gh run watch %NEWRUN% -R %REPO% --exit-status --interval 20 >nul
if errorlevel 1 (
  echo A build de profiling falhou.
  exit /b 1
)
:instalar_baixar
set RUN=
for /f "delims=" %%I in ('gh run list -R %REPO% --branch %BRANCH% --workflow "%WF%" --status success --limit 1 --json databaseId --jq ".[0].databaseId"') do set RUN=%%I
if not defined RUN (
  echo Nenhuma build verde encontrada.
  exit /b 1
)
set "TMPDL=%TEMP%\%NAME%-dl-%RANDOM%"
echo == Baixando ^(run %RUN%^)...
gh run download %RUN% -R %REPO% -D "%TMPDL%"
if errorlevel 1 (
  echo Falhou o download.
  exit /b 1
)
set APK=
for /r "%TMPDL%" %%F in (*.apk) do if not defined APK set "APK=%%F"
if not defined APK (
  echo O download nao tem .apk.
  rmdir /s /q "%TMPDL%"
  exit /b 1
)
if "%WF%"=="Profiling build" copy /y "%APK%" %NAME%-profiling.apk >nul
echo == Instalando...
adb install -r "%APK%"
set ERR=%ERRORLEVEL%
rmdir /s /q "%TMPDL%"
if not "%ERR%"=="0" (
  echo Falhou a instalacao. Se for INSTALL_FAILED_UPDATE_INCOMPATIBLE, exporte os dados no app e desinstale-o antes.
  exit /b 1
)
echo == Compilando o app no aparelho ^(~30 s^)...
adb shell cmd package compile -m speed-profile -f %APP% >nul
adb shell am start -n %APP%/.MainActivity >nul
echo Instalado.
exit /b 0

rem Nome do ultimo jogo aberto (linha "Game:" que o app grava no log do Android), so letras, numeros e "_"
:nomejogo
set GAME=
for /f "usebackq delims=" %%G in (`adb shell "logcat -d -s %LOGTAG%:I 2>/dev/null | grep 'Game: ' | tail -n 1 | sed 's/.*Game: //' | tr -cs 'A-Za-z0-9' '_' | cut -c1-32 | sed 's/^_*//;s/_*$//'"`) do set "GAME=%%G"
if not defined GAME set GAME=jogo
exit /b 0

rem Nome de arquivo livre: %1.%2, senao %1_2.%2, %1_3.%2...
:unico
set "OUT=%~1.%~2"
set N=2
:unico_loop
if not exist "%OUT%" exit /b 0
set "OUT=%~1_%N%.%~2"
set /a N+=1
goto unico_loop
