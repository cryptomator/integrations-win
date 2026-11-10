# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

The changelog starts with version 1.5.1.
Changes to prior versions can be found on the [GitHub release page](https://github.com/cryptomator/integrations-win/releases).

## [Unreleased](https://github.com/cryptomator/integrations-win/compare/1.7.0...HEAD)

No changes yet.


## [1.7.0](https://github.com/cryptomator/integrations-win/releases/tag/1.7.0) - 2026-11-10

### Added
* Implementation for the [update API](https://github.com/cryptomator/integrations-api/blob/develop/src/main/java/org/cryptomator/integrations/update/UpdateMechanism.java): `InstallerUpdateMechanism`, which downloads, verifies and runs the EXE or MSI installer matching the current installation. Requires the expected signer of the installer to be configured via the JVM properties `cryptomator.integrationsWin.updateDurableIdentityEku` and `cryptomator.integrationsWin.updateRootCaThumbprint`. The x64 installer is used on all architectures ([#164](https://github.com/cryptomator/integrations-win/pull/164))

### Changed
* Refactored native build: no GNU Make or hard-coded Visual Studio path needed, `build.cmd` locates Visual Studio via `vswhere` and uses `nmake` ([#169](https://github.com/cryptomator/integrations-win/pull/169))
* Update dependencies
  - `org.cryptomator:integrations-api` from 1.9.1 to 1.10.1
  - `com.fasterxml.jackson.core:jackson-databind` from 2.21.7 to 2.21.8


## [1.6.2](https://github.com/cryptomator/integrations-win/releases/1.6.2) - 2026-09-29
### Changed
* Update dependencies
  - `org.cryptomator:integrations-api` from 1.9.0 to 1.9.1
  - `com.fasterxml.jackson.core:jackson-databind` from 2.21.4 to 2.21.7
  - `org.slf4j:slf4j-api` from 2.0.18 to 2.0.20

## [1.6.1](https://github.com/cryptomator/integrations-win/releases/1.6.1) - 2026-06-23
### Changed
* Update dependencies
  - `org.cryptomator:integrations-api` from 1.7.0 to 1.9.0
  - `com.fasterxml.jackson.core:jackson-databind` from 2.21.0 to 2.21.4
  - `org.slf4j:slf4j-*` from 2.0.17 to 2.0.18


## [1.6.0](https://github.com/cryptomator/integrations-win/releases/1.6.0) - 2026-02-17
### Added
* Maven wrapper ([#131](https://github.com/cryptomator/integrations-win/pull/131))

### Changed
* Pin CI actions ([#132](https://github.com/cryptomator/integrations-win/pull/132))
* Switch signing certificate and process to Azure ([#132](https://github.com/cryptomator/integrations-win/pull/132))
* **[Breaking]** Switch compile target to JDK 25 (class file format 69) ([#136](https://github.com/cryptomator/integrations-win/pull/136))
* Update dependencies
  - `com.fasterxml.jackson.core:jackson-databind` from 2.20.0 to 2.20.1
  - `com.fasterxml.jackson.core:jackson-annotations` from 2.20 to 2.21


## [1.5.1](https://github.com/cryptomator/integrations-win/releases/1.5.1) - 2025-09-17
### Changed

* Update `com.fasterxml.jackson.core:jackson-databind` from 2.19.0 to 2.20.0
* Update `com.fasterxml.jackson.core:jackson-annotations` from 2.19.0 to 2.20
* Update `org.cryptomator:integrations-api` from 1.6.0 to 1.7.0

