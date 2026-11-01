@echo off
setlocal enableextensions
cd /d "%~dp0"

rem Builds a self-contained graphical Luhx distribution.
rem Requires JDK 21+ (java, jlink, jpackage) and Maven in the PATH.

set "NAME=Luhx"
set "MAIN_CLASS=br.com.pointel.luhx.LuhxGUI"
set "DIST=%~dp0dist"
set "PACKAGE=%DIST%\gui"
set "INPUT=%PACKAGE%\input"
set "RUNTIME=%PACKAGE%\runtime"
set "APP=%PACKAGE%\app"

for %%T in (java jlink jpackage mvn) do (
    where %%T >nul 2>&1 || (echo ERROR: %%T not found in PATH.& exit /b 1)
)

echo [1/7] Cleaning previous graphical-interface intermediates...
for %%D in ("%INPUT%" "%RUNTIME%" "%APP%" "%PACKAGE%\installer") do if exist %%D rmdir /s /q %%D
if exist "%PACKAGE%\modules.txt" del /q "%PACKAGE%\modules.txt"
if exist "%PACKAGE%\%NAME%-win.zip" del /q "%PACKAGE%\%NAME%-win.zip"
if exist "%PACKAGE%\%NAME%-1.0.msi" del /q "%PACKAGE%\%NAME%-1.0.msi"

echo [2/7] Building jar and dependencies with Maven...
call mvn -q -B package -DskipTests -Dmaven.javadoc.skip=true
if errorlevel 1 (echo ERROR: Maven build failed.& exit /b 1)

echo [3/7] Staging jpackage input...
if not exist "%INPUT%\lib" mkdir "%INPUT%\lib"
if errorlevel 1 (echo ERROR: could not create staging directory.& exit /b 1)
copy /y "%DIST%\luhx.jar" "%INPUT%\" >nul || exit /b 1
copy /y "%DIST%\lib\*.jar" "%INPUT%\lib\" >nul || exit /b 1

echo [4/7] Listing JDK modules...
for %%J in (jlink.exe) do set "JDKBIN=%%~dp$PATH:J"
"%JDKBIN%java" --list-modules > "%PACKAGE%\modules.txt"
if errorlevel 1 (echo ERROR: could not list modules.& exit /b 1)

echo [5/7] Creating Java runtime with jlink...
jlink --add-modules ALL-MODULE-PATH --output "%RUNTIME%" --strip-debug --no-header-files --no-man-pages --compress zip-6
if errorlevel 1 (echo ERROR: jlink failed.& exit /b 1)

echo [6/7] Creating graphical application image...
jpackage --type app-image --name %NAME% --input "%INPUT%" --main-jar luhx.jar --main-class %MAIN_CLASS% --runtime-image "%RUNTIME%" --dest "%APP%" --icon "%~dp0design\luhx.ico"
if errorlevel 1 (echo ERROR: jpackage failed.& exit /b 1)

powershell -NoProfile -Command "Compress-Archive -Path '%APP%\%NAME%' -DestinationPath '%PACKAGE%\%NAME%-win.zip' -Force"
if errorlevel 1 (echo ERROR: zip failed.& exit /b 1)

echo [7/7] Creating MSI installer with WiX...
rem jpackage on JDK 21 requires WiX 3.x (candle/light).
where candle >nul 2>&1
if errorlevel 1 if exist "%ProgramFiles(x86)%\WiX Toolset v3.14\bin\candle.exe" set "PATH=%ProgramFiles(x86)%\WiX Toolset v3.14\bin;%PATH%"
where candle >nul 2>&1 || (echo ERROR: WiX 3.x not found.& exit /b 1)
jpackage --type msi --name %NAME% --app-version 1.0 --app-image "%APP%\%NAME%" --dest "%PACKAGE%" --icon "%~dp0design\luhx.ico" --win-menu --win-shortcut --win-dir-chooser --win-per-user-install --vendor pointel --install-dir %NAME% --win-upgrade-uuid 220c7ea6-1f93-4f07-9a62-28041b6a36cc --temp "%PACKAGE%\installer"
if errorlevel 1 (echo ERROR: MSI creation failed.& exit /b 1)

echo.
echo Graphical-interface distribution created:
echo   Application: %APP%\%NAME%\%NAME%.exe
echo   Archive:     %PACKAGE%\%NAME%-win.zip
echo   Installer:   %PACKAGE%\%NAME%-1.0.msi
endlocal
