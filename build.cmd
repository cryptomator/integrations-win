@echo off
rem Builds the native DLL for one architecture: locates Visual Studio, enters its developer environment and runs nmake.
rem All pinned versions are passed in by the caller (see pom.xml) - this script deliberately has no defaults of its own.
setlocal

if "%~3"=="" goto :usage

set ARCH=%~1
set SDK_VERSION=%~2
set VS_MIN_VERSION=%~3

if /i "%ARCH%"=="x64" set VCVARS_ARCH=x64
if /i "%ARCH%"=="arm64" set VCVARS_ARCH=x64_arm64
if not defined VCVARS_ARCH (
	echo [build.cmd] unsupported architecture "%ARCH%", expected x64 or arm64 1>&2
	exit /b 2
)

rem An ambient developer environment (e.g. a Developer Command Prompt) wins over detection.
rem Note: VS_DIR must not be called VSINSTALLDIR - vcvarsall.bat reads that one and expects a trailing backslash.
set VS_DIR=%VSINSTALLDIR%
if defined VS_DIR goto :vcvars

set VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe
if not exist "%VSWHERE%" (
	echo [build.cmd] vswhere.exe not found at "%VSWHERE%" 1>&2
	echo [build.cmd] set VSINSTALLDIR to the Visual Studio installation root to skip detection 1>&2
	exit /b 1
)

set VSWHERE_OUT=%TEMP%\integrations-win-vsdir.txt
"%VSWHERE%" -latest -products * -version "[%VS_MIN_VERSION%,)" -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -requires Microsoft.VisualStudio.Component.VC.Tools.ARM64 -property installationPath > "%VSWHERE_OUT%"
set /p VS_DIR=<"%VSWHERE_OUT%"
del "%VSWHERE_OUT%"
if not defined VS_DIR (
	echo [build.cmd] no Visual Studio %VS_MIN_VERSION% or newer with both the x64 and the ARM64 C++ toolset found 1>&2
	exit /b 1
)

:vcvars
call "%VS_DIR%\VC\Auxiliary\Build\vcvarsall.bat" %VCVARS_ARCH% %SDK_VERSION% || exit /b 1
nmake /nologo install ARCH=%ARCH% || exit /b 1
exit /b 0

:usage
echo Usage: build.cmd ^<x64^|arm64^> ^<windows-sdk-version^> ^<minimum-visual-studio-version^> 1>&2
echo Example: build.cmd x64 10.0.22621.0 17.0 1>&2
exit /b 2
