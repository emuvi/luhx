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

echo [1/6] Cleaning previous intermediates...
for %%D in ("%INPUT%" "%RUNTIME%" "%APP%") do if exist %%D rmdir /s /q %%D
if exist "%DIST%\modules.txt" del /q "%DIST%\modules.txt"
if exist "%DIST%\%NAME%-windows.zip" del /q "%DIST%\%NAME%-windows.zip"

echo [2/6] Building jar and dependencies with Maven...
call mvn -q -B package -DskipTests -Dmaven.javadoc.skip=true
if errorlevel 1 (echo ERROR: Maven build failed.& exit /b 1)

echo [3/6] Staging jpackage input...
mkdir "%INPUT%\lib" || exit /b 1
copy /y "%DIST%\%NAME%.jar" "%INPUT%\" >nul || exit /b 1
copy /y "%DIST%\lib\*.jar" "%INPUT%\lib\" >nul || exit /b 1

echo [4/6] Detecting required Java modules with jdeps...
set "MODULES="
for /f "delims=" %%M in ('jdeps --multi-release 21 --ignore-missing-deps --print-module-deps --class-path "%INPUT%\lib\*" "%INPUT%\%NAME%.jar"') do set "MODULES=%%M"
if not defined MODULES (echo ERROR: jdeps failed.& exit /b 1)
rem Extra modules commonly needed at runtime by the libraries (reflection, charsets, crypto, jar handling).
set "MODULES=%MODULES%,java.sql,java.naming,java.xml,java.desktop,java.management,jdk.unsupported,jdk.charsets,jdk.crypto.ec,jdk.zipfs"
echo %MODULES%> "%DIST%\modules.txt"
echo Modules: %MODULES%

echo [5/6] Creating trimmed Java runtime with jlink...
jlink --add-modules %MODULES% --output "%RUNTIME%" --strip-debug --no-header-files --no-man-pages --compress zip-6
if errorlevel 1 (echo ERROR: jlink failed.& exit /b 1)

echo [6/6] Creating application image with jpackage...
jpackage --type app-image --name %NAME% --input "%INPUT%" --main-jar %NAME%.jar --runtime-image "%RUNTIME%" --dest "%APP%" --win-console
if errorlevel 1 (echo ERROR: jpackage failed.& exit /b 1)

powershell -NoProfile -Command "Compress-Archive -Path '%APP%\%NAME%' -DestinationPath '%DIST%\%NAME%-windows.zip' -Force"
if errorlevel 1 (echo ERROR: zip failed.& exit /b 1)

echo.
echo Done.
echo   Application: %APP%\%NAME%\%NAME%.exe
echo   Archive:     %DIST%\%NAME%-windows.zip
endlocal
