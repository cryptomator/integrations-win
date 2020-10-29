HEADERS = -I"${JAVA_HOME}\include" -I"${JAVA_HOME}\include\win32"

########

all: install

install:
	gcc -Wall -D_JNI_IMPLEMENTATION_ -Wl,--kill-at $(HEADERS) -shared -otarget/classes/integrations.dll src/main/cpp/*.cpp -lcrypt32