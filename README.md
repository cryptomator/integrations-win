# Cryptomator Windows Integrations

Windows-specific implementations of [integrations-api](https://github.com/cryptomator/integrations-api).

## Config

This project uses the following JVM properties:
* `cryptomator.integrationsWin.autoStartShellLinkName` - Name of the shell link, which is placed in the Windows startup folder to start application on user login
* `cryptomator.integrationsWin.keychainPaths` - List of file paths, which are checked for data encrypted with the Windows data protection api

## Building

### Requirements

* JDK 22
* Maven
* MSVC 2022 toolchain (Visual Studio 2022, Workset "Desktop development with C++")

### Build
Start the _Developer PowerShell for Visual Studio 2022_ and run the following commands:
```pwsh
mvn clean verify
```