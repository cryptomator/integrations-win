@echo off
rem Builds the native DLL for one architecture: locates Visual Studio, enters its developer environment and runs nmake.
rem All pinned versions are passed in by the caller (see pom.xml) - this script deliberately has no defaults of its own.
rem
rem Quoting rules in here are deliberate: every assignment uses `set "VAR=..."` and every expansion sits inside double
rem quotes, so that a stray &, | or > in an inherited variable such as TEMP or VSINSTALLDIR stays data instead of
rem becoming a command. The two version arguments are the exception - vcvarsall.bat compares its parameters literally
rem and rejects quoted ones, so they are validated below before they reach its command line unquoted.
setlocal

if "%~1"=="" goto :usage
if "%~2"=="" goto :usage
if "%~3"=="" goto :usage

set "ARCH=%~1"
set "SDK_VERSION=%~2"
set "VS_MIN_VERSION=%~3"

set "VCVARS_ARCH="
if /i "%ARCH%"=="x64" set "VCVARS_ARCH=x64"
if /i "%ARCH%"=="arm64" set "VCVARS_ARCH=x64_arm64"
if not defined VCVARS_ARCH goto :bad_arch

rem Accept digits and dots only; any other character yields a token and jumps to the error label.
for /f "delims=0123456789." %%c in ("%SDK_VERSION%") do goto :bad_sdk_version
for /f "delims=0123456789." %%c in ("%VS_MIN_VERSION%") do goto :bad_vs_version

rem An ambient developer environment (e.g. a Developer Command Prompt) wins over detection.
rem Note: VS_DIR must not be called VSINSTALLDIR - vcvarsall.bat reads that one and expects a trailing backslash.
set "VS_DIR=%VSINSTALLDIR%"
if defined VS_DIR goto :vcvars

set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" goto :no_vswhere

set "VSWHERE_OUT=%TEMP%\integrations-win-vsdir.txt"
"%VSWHERE%" -latest -products * -version "[%VS_MIN_VERSION%,)" -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -requires Microsoft.VisualStudio.Component.VC.Tools.ARM64 -property installationPath > "%VSWHERE_OUT%"
set /p "VS_DIR="<"%VSWHERE_OUT%"
del "%VSWHERE_OUT%"
if not defined VS_DIR goto :no_vs

:vcvars
if not exist "%VS_DIR%\VC\Auxiliary\Build\vcvarsall.bat" goto :no_vcvarsall
call "%VS_DIR%\VC\Auxiliary\Build\vcvarsall.bat" %VCVARS_ARCH% %SDK_VERSION% || exit /b 1
nmake /nologo install ARCH=%ARCH% || exit /b 1
exit /b 0

:bad_arch
echo [build.cmd] unsupported architecture "%ARCH%", expected x64 or arm64 1>&2
exit /b 2

:bad_sdk_version
echo [build.cmd] invalid Windows SDK version "%SDK_VERSION%", expected digits and dots only 1>&2
exit /b 2

:bad_vs_version
echo [build.cmd] invalid Visual Studio version "%VS_MIN_VERSION%", expected digits and dots only 1>&2
exit /b 2

:no_vswhere
echo [build.cmd] vswhere.exe not found at "%VSWHERE%" 1>&2
echo [build.cmd] set VSINSTALLDIR to the Visual Studio installation root to skip detection 1>&2
exit /b 1

:no_vs
echo [build.cmd] no Visual Studio %VS_MIN_VERSION% or newer with both the x64 and the ARM64 C++ toolset found 1>&2
exit /b 1

:no_vcvarsall
echo [build.cmd] no VC\Auxiliary\Build\vcvarsall.bat below "%VS_DIR%" 1>&2
echo [build.cmd] VSINSTALLDIR must point at a Visual Studio installation root 1>&2
exit /b 1

:usage
echo Usage: build.cmd ^<x64^|arm64^> ^<windows-sdk-version^> ^<minimum-visual-studio-version^> 1>&2
echo Example: build.cmd x64 10.0.22621.0 17.0 1>&2
exit /b 2
