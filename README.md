# Cryptomator Windows Integrations

Windows-specific implementations of [integrations-api](https://github.com/cryptomator/integrations-api).

## Config

This project uses the following JVM properties:
* `cryptomator.integrationsWin.autoStartShellLinkName` - Name of the shell link, which is placed in the Windows startup folder to start application on user login
* `cryptomator.integrationsWin.windowsHelloKeyId` - Identifier for the Windows Hello keypair
* `cryptomator.integrationsWin.keychainPaths` - List of file paths, which are checked for data encrypted with the Windows data protection api

## Building

### Requirements

* JDK 22
* Maven
* MSVC 2022 toolset (e.g. by installing Visual Studio 2022, Workset "Desktop development with C++")
* Make (`choco install make`)

### Build
Open a terminal and run
```
mvn clean verify
```

If building the dll fails with "cl.exe cannot be found", you have to specify the developer command file directory as a property, e.g. `-DdevCommandFileDir=C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\"`.