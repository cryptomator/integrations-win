# Cryptomator Windows Integrations

Windows-specific implementations of [integrations-api](https://github.com/cryptomator/integrations-api).

## Config

This project uses the following JVM properties:
* `cryptomator.integrationsWin.autoStartShellLinkName` - Name of the shell link, which is placed in the Windows startup folder to start application on user login

## Building

This project uses JNI, hence you'll nedd Java as well as GCC build tools:

* JDK 17
* Maven
* MinGW GCC compiler (`choco install mingw --version=10.2.0`)
* Make (`choco install make`)