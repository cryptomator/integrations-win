HEADERS := -I"src/main/headers" -I"${JAVA_HOME}\include" -I"${JAVA_HOME}\include\win32"
SOURCES := $(wildcard src/main/native/*.cpp)

########

all: install

install:
	g++ -Wall -D_JNI_IMPLEMENTATION_ -Wl,--kill-at $(HEADERS) -shared -osrc/main/resources/integrations.dll $(SOURCES) -lcrypt32 -lshell32 -lole32 -luuid