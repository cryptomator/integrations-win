# Note: make apparently thinks, that options specified with "/" are absolute paths and resolves them. see also https://stackoverflow.com/questions/17012419/d9024-make-unrecognized-source-file-type
WIN_SDK_VERSION ?= 10.0.22621.0
MSVC_VERSION ?= 14.41.34120
HEADERS := -I"src\main\headers" \
	-I"${JAVA_HOME}\include" \
	-I"${JAVA_HOME}\include\win32" \
	-I"C:\Program Files (x86)\Windows Kits\10\Include\$(WIN_SDK_VERSION)\cppwinrt" \
	-I"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\$(MSVC_VERSION)\include"
SOURCES := $(wildcard src/main/native/*.cpp)

########

all: install

install:
	cl -EHsc -std:c++17 -LD -W4 -guard:cf \
		-Fe"src/main/resources/integrations.dll" \
		-Fo"target/" \
		$(HEADERS) $(SOURCES) \
		-link -NXCOMPAT -DYNAMICBASE \
		-implib:target/integrations.lib \
		crypt32.lib shell32.lib ole32.lib uuid.lib user32.lib Advapi32.lib