# Race Replay Lab

Race Replay Lab is a modular JavaFX desktop application for exploring
motorsport replay concepts. The default experience is deterministic and uses
only fictional drivers, teams, telemetry and a generic circuit.

The repository does not bundle real race data, driver images, team assets,
official logos or circuit geometry.

## Current status

Version 0.1.0 is the first public preview. The application and tests are usable,
but native installers and their signing workflows are not yet part of the
release.

## Requirements

- JDK 25 (LTS)
- Internet access for the first Maven Wrapper run

A global Maven installation is not required. The repository pins Maven through
the included wrapper.

```bash
java --version
./mvnw --version
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

## Run the application

macOS or Linux:

```bash
./mvnw javafx:run
```

Windows:

```powershell
mvnw.cmd javafx:run
```

Convenience launchers are also available:

- macOS: `run.command`
- Windows: `run.bat`

The application starts with synthetic demo data. It does not download external
data during the normal default launch.

## Build and test

Run the complete local verification:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

This executes Checkstyle, compilation, JUnit tests, JAR packaging and the
JaCoCo report. The coverage report is written to
`target/site/jacoco/index.html`.

Create a self-contained runtime image for the current operating system:

```bash
./mvnw javafx:jlink
```

## Project structure

```text
src/main/java/       Application, replay model and optional data adapters
src/test/java/       Automated tests with fictional fixtures
config/checkstyle/   Checkstyle configuration
.mvn/wrapper/        Pinned Maven distribution and checksum
.github/workflows/   Continuous integration
```

## Optional external data adapter

The codebase contains an optional adapter for the third-party OpenF1 API. It is
not used by the default synthetic demo and no API responses are committed to
this repository.

Use **IMPORT OPENF1** in the application to open the session selector. The
catalog contains only non-cancelled sessions reported by OpenF1 whose
`date_end` is not later than the instant the application was opened. Choose the
year, country and session, review the exact circuit, and confirm the download.

The selected data is downloaded and processed in the background. It becomes
the active replay after the application is restarted. A failed or cancelled
import does not replace the previous replay selection.

For example, a completed Spa session can be selected as `2026`, `Belgium`,
`Race` when OpenF1 provides that session. See
[OPENF1_IMPORT.md](OPENF1_IMPORT.md) for the UI workflow, command-line fallback
and current limitations.

Users who choose to fetch or process external data are responsible for checking
the provider terms and all applicable rights before use or redistribution. See
[DATA_SOURCES.md](DATA_SOURCES.md) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Local application data

Application data is stored outside the repository:

- macOS: `~/Library/Application Support/Race Replay Lab/cache/openf1`
- Windows: `%LOCALAPPDATA%\Race Replay Lab\cache\openf1`
- Linux: `$XDG_DATA_HOME/race-replay-lab/cache/openf1` or
  `~/.local/share/race-replay-lab/cache/openf1`

Local raw data, caches, builds and installers are excluded by `.gitignore`.

## Legal status

Race Replay Lab is an independent, unofficial software project. It is not
affiliated with, endorsed by or sponsored by any racing series, promoter, team,
driver, circuit or data provider. Third-party names are used only where needed
to identify an optional interoperability provider.

The project name, fictional demo data and generic circuit are not intended to
represent a real championship or event. See [TRADEMARKS.md](TRADEMARKS.md).

## Contributing and security

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Please
report suspected vulnerabilities according to [SECURITY.md](SECURITY.md), not
through a public issue.

## License

The original source code is licensed under the Apache License 2.0. This license
does not grant rights to third-party APIs, API responses, trademarks or other
external material. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
