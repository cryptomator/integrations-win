# NMAKE makefile. Run it from a Visual Studio developer prompt (vcvars64.bat or vcvarsamd64_arm64.bat), e.g. `nmake /nologo install ARCH=arm64`
ARCH = x64
HEADERS = /I"src\main\headers" \
	/I"$(JAVA_HOME)\include" \
	/I"$(JAVA_HOME)\include\win32"
OUT_DIR = target\$(ARCH)
OUT_DLL = src\main\resources\integrations-$(ARCH).dll
OUT_LIB = $(OUT_DIR)\integrations.lib

########

all: install

install:
	if not exist "$(OUT_DIR)" mkdir "$(OUT_DIR)"
	cl /nologo /EHsc /std:c++17 /LD /W4 /guard:cf \
		/Fe"$(OUT_DLL)" \
		/Fo"$(OUT_DIR)/" \
		$(HEADERS) src\main\native\*.cpp \
		/link /NXCOMPAT /DYNAMICBASE \
		/IMPLIB:"$(OUT_LIB)" \
		crypt32.lib shell32.lib ole32.lib uuid.lib user32.lib Advapi32.lib windowsapp.lib
