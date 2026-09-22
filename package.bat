@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

rem ---- config ----
set APP_NAME=Vergvoke
set APP_VERSION=1.0
set MAIN_CLASS=caliniya.vergvoke.DesktopLauncher
set MAX_HEAP=-Xmx2G
set ICON=
rem set ICON=%~dp0assets\icon.ico
rem ------------------

rem 双击运行时窗口不自动关掉
echo %cmdcmdline% | find /i "%~nx0" >nul && set PAUSEON=1

set LOG=%~dp0build\package.log
mkdir "%~dp0build" 2>nul

rem ---- locate jpackage ----
set JPK=
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set JPK=%JAVA_HOME%\bin\jpackage.exe
if not defined JPK for /f "delims=" %%i in ('where jpackage 2^>nul') do if not defined JPK set JPK=%%i
if not defined JPK (
  echo [x] jpackage not found.
  echo     put a JDK 17/21 in JAVA_HOME, or add its bin folder to PATH.
  goto fail
)
echo [0/3] jpackage : "%JPK%"
"%JPK%" --version

set ICON_ARG=
if defined ICON set ICON_ARG=--icon "%ICON%"

echo [1/3] building fat jar (gradlew :desktop:dist) ...
call gradlew.bat :desktop:dist --console=plain >>"%LOG%" 2>&1
if errorlevel 1 echo [x] gradle dist failed, see log. & goto fail

set JAR=%~dp0desktop\build\output\%APP_NAME%-%APP_VERSION%.jar
if not exist "%JAR%" echo [x] jar not found: %JAR% & goto fail

set OUT=%~dp0build\package
set IN=%OUT%\input
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%IN%" || goto fail
copy /y "%JAR%" "%IN%\" >nul || goto fail

echo [2/3] running jpackage (app-image) ... (takes ~30s)
"%JPK%" --type app-image --name %APP_NAME% --app-version %APP_VERSION% ^
  --input "%IN%" --main-jar "%APP_NAME%-%APP_VERSION%.jar" --main-class %MAIN_CLASS% ^
  --dest "%OUT%" --java-options "%MAX_HEAP%" --win-console %ICON_ARG% >"%LOG%" 2>&1
if errorlevel 1 echo [x] jpackage failed. & goto fail

echo [3/3] done.
echo.
echo   exe : %OUT%\%APP_NAME%\%APP_NAME%.exe
echo   data: %OUT%\%APP_NAME%\data   (created on first run)
echo   ship: zip the whole folder %OUT%\%APP_NAME%
goto end

:fail
echo.
echo ---- error log (last lines) ----
type "%LOG%" 2>nul
echo --------------------------------
echo Common causes:
echo   * jpackage from a JRE (no jpackage.exe)  -^> use a JDK 17/21
echo   * --type exe/msi without WiX Toolset 3.x  -^> use app-image (this script does)
echo   * jar name mismatch: expected %APP_NAME%-%APP_VERSION%.jar in desktop\build\output
echo Full log: %LOG%
echo.

:end
if defined PAUSEON pause
exit /b 0
