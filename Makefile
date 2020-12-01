HEADERS = -I"src/main/headers" -I"${JAVA_HOME}\include" -I"${JAVA_HOME}\include\win32"

########

all: install

install:
	g++ -Wall -D_JNI_IMPLEMENTATION_ -Wl,--kill-at $(HEADERS) -shared -osrc/main/resources/integrations.dll src/main/native/*.cpp -lcrypt32 -lshell32 -lole32 -luuid