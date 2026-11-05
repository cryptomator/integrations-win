# Cryptomator Windows Integrations

Windows-specific implementations of [integrations-api](https://github.com/cryptomator/integrations-api).

## Config

This project uses the following JVM properties:
* `cryptomator.integrationsWin.autoStartShellLinkName` - Name of the shell link, which is placed in the Windows startup folder to start application on user login
* `cryptomator.integrationsWin.windowsHelloKeyId` - Identifier for the Windows Hello keypair
* `cryptomator.integrationsWin.windowsHelloKeychainPaths` - Locations of the file-based windowsHello keychain
* `cryptomator.integrationsWin.keychainPaths` - List of file paths, which are checked for data encrypted with the Windows data protection api
* `cryptomator.integrationsWin.updateDurableIdentityEku` - OID of an extended key usage, which the code signing certificate of a downloaded installer must contain. With Azure Artifact Signing, this is the durable identity of the signing account, which stays the same across certificate renewals (e.g. `1.3.6.1.4.1.311.97.986645502.209118791.449271898.355477593` for Skymatic GmbH). Required by `InstallerUpdateMechanism`.
* `cryptomator.integrationsWin.updateRootCaThumbprint` - SHA-1 thumbprint (40 hex characters, without spaces) of the root certificate, at which the certificate chain of a downloaded installer's signature must end (e.g. `F40042E2E5F7E8EF8189FED15519AECE42C3BFA2` for Microsoft Identity Verification Root Certificate Authority 2020). Required by `InstallerUpdateMechanism`.

`InstallerUpdateMechanism` only updates if both of the `update…` properties are set.

## Building

### Requirements

* JDK 25
* Visual Studio 2022 or newer, workload "Desktop development with C++", including both the x64 and the ARM64 C++ toolset (component "MSVC C++ ARM64/ARM64EC build tools")
* Windows SDK 10.0.22621.0

### Build
Open a terminal and run
```
./mvnw clean verify
```

The native DLLs are built by `build.cmd`. The Windows SDK version and the minimum Visual Studio version are specified in `pom.xml`. To build a single architecture by hand, run from the repository root:
```
build.cmd x64 10.0.22621.0 17.0
```

If Visual Studio is installed where `vswhere` does not find it, set the environment variable `VSINSTALLDIR` to the installation root (the directory containing `VC\Auxiliary\Build\vcvarsall.bat`); detection is then skipped.