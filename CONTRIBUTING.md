# Contributing

Thank you for helping improve Race Replay Lab.

## Before opening a pull request

1. Create a focused branch from the current `main` branch.
2. Keep changes small and explain their user-visible effect.
3. Add or update tests for behavior changes.
4. Run `./mvnw --batch-mode --no-transfer-progress clean verify`.
5. Confirm that no generated build output, credentials or real race data is
   included.

## Data and intellectual property

Contributions must not include downloaded API responses, official telemetry,
driver images, logos, team liveries, protected circuit geometry or other
third-party assets unless redistribution rights are documented and accepted by
the maintainer.

Prefer fictional fixtures and synthetic data in tests and examples.

## Commit and review expectations

- Write clear commit messages.
- Avoid unrelated formatting changes.
- Keep GitHub Actions pinned to full commit SHAs.
- Address review feedback with new commits until the pull request is approved.

By contributing, you confirm that you have the right to submit the contribution
under the project's Apache License 2.0.
