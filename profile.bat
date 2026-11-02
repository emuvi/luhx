@echo off
setlocal enabledelayedexpansion
rem Profiles Luhx with JDK tools: JFR (CPU/alloc/locks/IO), GC log and heap dump on OOM.
rem Usage: profile.bat <heap> <luhx args...>
rem Example: profile.bat 512m --index C:\Docs

cd /d "%~dp0"
rem Needs JDK 11+ (JFR); prefer JAVA_HOME over an older java on PATH.
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
if "%~2"=="" (
    echo Usage: profile.bat ^<heap, e.g. 512m^> ^<luhx args, e.g. --index C:\Docs^>
    exit /b 1
)
set "HEAP=%~1"
shift

set "OUT=%~dp0profile"
if not exist "%OUT%" mkdir "%OUT%"
del /q "%OUT%\*" >nul 2>&1

if not exist target\classes call mvn -q -o compile || exit /b 1
if not exist "%OUT%\cp.txt" call mvn -q -o dependency:build-classpath -Dmdep.outputFile="%OUT%\cp.txt" || exit /b 1
for /f "usebackq delims=" %%a in ("%OUT%\cp.txt") do set "CP=%%a"

echo Running with heap %HEAP%. Output in %OUT%
rem profile=profile enables the detailed JFR settings; 10ms sampling of Java stacks.
java -Xmx%HEAP% ^
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath="%OUT%\oom.hprof" ^
  "-Xlog:gc*:file=profile\gc.log:time,uptime" ^
  "-XX:StartFlightRecording=filename=%OUT%\run.jfr,settings=profile,dumponexit=true" ^
  -cp "target\classes;%CP%" br.com.pointel.luhx.Luhx %1 %2 %3 %4 %5 %6 %7 %8 %9
set "RC=%ERRORLEVEL%"

if not exist "%OUT%\run.jfr" (
    echo No JFR file produced.
    exit /b %RC%
)

echo.
echo ===== Hot methods (CPU samples, top frames) =====
jfr print --events jdk.ExecutionSample --stack-depth 1 "%OUT%\run.jfr" > "%OUT%\samples.txt"
powershell -NoProfile -Command "$m=@{}; Get-Content '%OUT%\samples.txt' | ForEach-Object { if($_ -match '^\s+([\w\.\$<>]+)\(.*line:') {$k=$Matches[1]; $m[$k]++} }; $m.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 25 | ForEach-Object { '{0,6}  {1}' -f $_.Value,$_.Key }"

echo.
echo ===== Top allocation sites (memory pressure) =====
jfr print --events jdk.ObjectAllocationSample --stack-depth 1 "%OUT%\run.jfr" > "%OUT%\alloc.txt"
powershell -NoProfile -Command "$m=@{}; Get-Content '%OUT%\alloc.txt' | ForEach-Object { if($_ -match '^\s+([\w\.\$<>]+)\(.*line:') {$k=$Matches[1]; $m[$k]++} }; $m.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 15 | ForEach-Object { '{0,6}  {1}' -f $_.Value,$_.Key }"

echo.
echo ===== GC pauses (longest) =====
jfr print --events jdk.GarbageCollection "%OUT%\run.jfr" > "%OUT%\gcevents.txt"
findstr /i "longestPause sumOfPauses" "%OUT%\gcevents.txt" | sort /r | more +0

echo.
echo Files in %OUT%:
echo   run.jfr     - open in JDK Mission Control or IntelliJ/VS Code JFR viewer
echo   gc.log      - GC activity over time
echo   oom.hprof   - heap dump if OutOfMemoryError happened (open in Eclipse MAT / VisualVM)
echo   samples.txt, alloc.txt - raw event dumps
echo.
echo Other JDK tools while it runs (use the PID from "jps -l"):
echo   jcmd PID Thread.print          thread dump (see where workers are blocked)
echo   jcmd PID GC.class_histogram    what objects fill the heap
echo   jcmd PID GC.heap_dump file.hprof
echo   jstat -gcutil PID 1000         live GC/heap usage each second
echo   jfr summary "%OUT%\run.jfr"    event overview
exit /b %RC%
