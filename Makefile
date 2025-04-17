# Note: make apparently thinks, that options specified with "/" are absolute paths and resolves them. see also https://stackoverflow.com/questions/17012419/d9024-make-unrecognized-source-file-type
ARCH ?= x64
WIN_SDK_VERSION ?= 10.0.22621.0
MSVC_VERSION ?= 14.41.34120
HEADERS := -I"src\main\headers" \
	-I"${JAVA_HOME}\include" \
	-I"${JAVA_HOME}\include\win32" \
	-I"C:\Program Files (x86)\Windows Kits\10\Include\$(WIN_SDK_VERSION)\cppwinrt"
SOURCES := $(wildcard src/main/native/*.cpp)
OUT_DIR := target/$(ARCH)
OUT_DLL := src/main/resources/integrations-$(ARCH).dll
OUT_LIB := $(OUT_DIR)/integrations.lib

########

all: install

install:
	@if not exist "$(OUT_DIR)" mkdir "$(OUT_DIR)"
	cl -EHsc -std:c++17 -LD -W4 -guard:cf \
		-Fe"$(OUT_DLL)" \
		-Fo"$(OUT_DIR)/" \
		$(HEADERS) $(SOURCES) \
		-link -NXCOMPAT -DYNAMICBASE \
		-implib:$(OUT_LIB) \
		crypt32.lib shell32.lib ole32.lib uuid.lib user32.lib Advapi32.lib windowsapp.lib
