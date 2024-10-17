#HEADERS := -I"src/main/headers" -I"${JAVA_HOME}\include" -I"${JAVA_HOME}\include\win32" -I"C:\Program Files (x86)\Windows Kits\10\Include\10.0.22621.0\cppwinrt" -I"C:\Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.41.34120/include"
HEADERS := /I"src/main/headers" /I"${JAVA_HOME}\include" /I"${JAVA_HOME}\include\win32" /I"C:\Program Files (x86)\Windows Kits\10\Include\10.0.22621.0\cppwinrt" /I"C:\Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.41.34120/include"
SOURCES := $(wildcard src/main/native/*.cpp)

########

all: install

#install:
#	g++ -Wall -D_JNI_IMPLEMENTATION_ -Wl,--kill-at $(HEADERS) -shared -osrc/main/resources/integrations.dll $(SOURCES) -lcrypt32 -lshell32 -lole32 -luuid -luser32 -lwindowsapp

install:
	cl /EHsc /std:c++17 /LD /Fe:src/main/resources/integrations.dll $(HEADERS) $(SOURCES) /link crypt32.lib shell32.lib ole32.lib uuid.lib user32.lib windowsapp.lib