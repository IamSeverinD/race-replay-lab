# Changelog

All notable changes to Race Replay Lab are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Cross-platform runtime-image packaging in CI and for release tags.
- SHA-256 checksum files and signed GitHub build-provenance attestations.

### Changed

- Updated JavaFX, JUnit, Checkstyle, Maven Surefire and GitHub Actions.
- Runtime archive names now derive from the actual operating system and
  architecture.

## [0.1.0] - 2026-08-09

### Added

- Deterministic JavaFX replay experience with fictional demo data.
- Responsive playback controls, telemetry, timing and race-control views.
- Optional OpenF1 session discovery and transactional background imports.
- Cross-platform CI for Linux, macOS and Windows on JDK 25.
- Publication-safety checks, legal notices and contributor documentation.

### Security

- External API data and application caches stay outside the repository.
- Failed or cancelled imports cannot replace the active replay.

[Unreleased]: https://github.com/IamSeverinD/race-replay-lab/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/IamSeverinD/race-replay-lab/releases/tag/v0.1.0
