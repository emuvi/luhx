@echo off
setlocal enableextensions
cd /d "%~dp0"

rem Builds a self-contained Luhx distribution (jlink runtime + jpackage app-image).
rem Everything, including intermediate steps, is kept inside the dist folder.
rem Requires JDK 21+ (jdeps, jlink, jpackage) and Maven in the PATH.

set "NAME=luhx"
set "DIST=%~dp0dist"
set "INPUT=%DIST%\input"
set "RUNTIME=%DIST%\runtime"
set "APP=%DIST%\app"

for %%T in (java jdeps jlink jpackage mvn) do (
    where %%T >nul 2>&1 || (echo ERROR: %%T not found in PATH.& exit /b 1)
)

echo [1/7] Cleaning previous intermediates...
for %%D in ("%INPUT%" "%RUNTIME%" "%APP%" "%DIST%\installer" "%DIST%\installer-temp") do if exist %%D rmdir /s /q %%D
if exist "%DIST%\modules.txt" del /q "%DIST%\modules.txt"
if exist "%DIST%\%NAME%-win.zip" del /q "%DIST%\%NAME%-win.zip"

echo [2/7] Building jar and dependencies with Maven...
call mvn -q -B package -DskipTests -Dmaven.javadoc.skip=true
if errorlevel 1 (echo ERROR: Maven build failed.& exit /b 1)

echo [3/7] Staging jpackage input...
mkdir "%INPUT%\lib" || exit /b 1
copy /y "%DIST%\%NAME%.jar" "%INPUT%\" >nul || exit /b 1
copy /y "%DIST%\lib\*.jar" "%INPUT%\lib\" >nul || exit /b 1

echo [4/7] Listing all modules of the JDK...
for %%J in (jlink.exe) do set "JDKBIN=%%~dp$PATH:J"
"%JDKBIN%java" --list-modules > "%DIST%\modules.txt"
if errorlevel 1 (echo ERROR: could not list modules.& exit /b 1)

echo [5/7] Creating full Java runtime with jlink...
jlink --add-modules ALL-MODULE-PATH --output "%RUNTIME%" --strip-debug --no-header-files --no-man-pages --compress zip-6
if errorlevel 1 (echo ERROR: jlink failed.& exit /b 1)

echo [6/7] Creating application image with jpackage...
jpackage --type app-image --name %NAME% --input "%INPUT%" --main-jar %NAME%.jar --runtime-image "%RUNTIME%" --dest "%APP%" --win-console --icon "%~dp0design\luhx.ico"
if errorlevel 1 (echo ERROR: jpackage failed.& exit /b 1)

powershell -NoProfile -Command "Compress-Archive -Path '%APP%\%NAME%' -DestinationPath '%DIST%\%NAME%-win.zip' -Force"
if errorlevel 1 (echo ERROR: zip failed.& exit /b 1)

echo [7/7] Creating MSI installer with WiX...
rem jpackage on JDK 21 requires WiX 3.x (candle/light).
where candle >nul 2>&1
if errorlevel 1 if exist "%ProgramFiles(x86)%\WiX Toolset v3.14\bin\candle.exe" set "PATH=%ProgramFiles(x86)%\WiX Toolset v3.14\bin;%PATH%"
where candle >nul 2>&1 || (echo ERROR: WiX 3.x not found.& exit /b 1)
if exist "%DIST%\installer" rmdir /s /q "%DIST%\installer"
jpackage --type msi --name %NAME% --app-version 1.0 --app-image "%APP%\%NAME%" --dest "%DIST%\installer" --icon "%~dp0design\luhx.ico" --win-menu --win-shortcut --win-dir-chooser --win-per-user-install --vendor pointel --install-dir "pointel\%NAME%" --win-upgrade-uuid 5b0c3f0e-8f6a-4c1d-9a57-3e2d7c1a4b90 --temp "%DIST%\installer-temp"
if errorlevel 1 (echo ERROR: MSI creation failed.& exit /b 1)

echo.
echo Done.
echo   Application: %APP%\%NAME%\%NAME%.exe
echo   Archive:     %DIST%\%NAME%-win.zip
echo   Installer:   %DIST%\installer\%NAME%-1.0.msi
endlocal
