# Optional OpenF1 replay import

Race Replay Lab can import a real session from the independent OpenF1 service.
This feature is optional and never runs during the normal default launch.

## Application workflow

1. Start Race Replay Lab and select **IMPORT OPENF1**.
2. Wait while the application loads the available session catalog.
3. Select a year, country and completed race or sprint session.
4. Verify the circuit shown in the confirmation dialog.
5. Confirm the potentially large local download.
6. Restart Race Replay Lab after processing completes.

The application captures the current UTC time when it starts. The selector
includes only race-type sessions that OpenF1 reports as non-cancelled and whose
`date_end` is at or before that application-start time. This includes Grands
Prix and Sprints; Practice and Qualifying do not have the common race timeline
required by the replay model. Future and currently running sessions are
excluded. Internally, the exact OpenF1 `session_key` is used so countries with
multiple events cannot select the wrong session.

For Spa, the expected selection is `2026` → `Belgium` → `Race`, provided the
session has finished and is present in the OpenF1 catalog.

## Command-line fallback

The equivalent explicit import on macOS or Linux is:

```bash
./mvnw --batch-mode --no-transfer-progress \
  compile exec:java@openf1-import \
  -Dexec.args="2026 Belgium Race"
```

On Windows PowerShell:

```powershell
mvnw.cmd --batch-mode --no-transfer-progress `
  compile exec:java@openf1-import `
  '-Dexec.args=2026 Belgium Race'
```

The command-line fallback uses the three query values and can be ambiguous for
countries hosting multiple events. Prefer the application selector, which uses
the exact session key. Non-race sessions are rejected before the bulk download
starts.

Clear the active external replay and return to the synthetic default:

```bash
./mvnw --batch-mode --no-transfer-progress \
  compile exec:java@openf1-import \
  -Dexec.args="--clear"
```

## Storage and failure behavior

Raw and processed responses are stored only in the operating system's local
application-data directory. They are excluded from Git. Each import holds an
exclusive cache lock and builds the complete replay in an isolated staging
directory. Timeline generation, metadata enrichment and race-control
normalization must all succeed before the replay is published and its active
pointer is updated atomically.

OpenF1 may return no rows for optional session metadata such as intervals,
pit stops, race-control messages or weather. These responses are stored as
validated empty datasets; missing core driver, lap, location or telemetry data
still fails the transaction.

Cancelling or failing an import removes its staging data and leaves the
previous replay selection unchanged. Staging directories left by an abruptly
terminated process are removed safely when the next import acquires the cache
lock. A completed import uses a new immutable replay directory, so it never
overwrites files currently used by the running application.

## Current limitations

- Availability and completeness depend on the third-party OpenF1 service.
- Large telemetry sessions can require substantial download time and disk
  space.
- A cancelled import is removed and starts its download from the beginning on
  the next attempt; resumable downloads are not implemented yet.
- Successfully published replay versions remain in the application-data
  directory until they are cleaned up manually. Automatic retention cleanup is
  not implemented yet because an older replay may still be open in the running
  application.
- The renderer derives the circuit outline and driver projection from imported
  OpenF1 location coordinates. The generic circuit remains a fallback for
  incomplete data; no official circuit artwork is bundled.
- Downloaded data must not be committed or redistributed without a compatible
  license and all necessary rights.

Review [DATA_SOURCES.md](DATA_SOURCES.md),
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and
[PRIVACY.md](PRIVACY.md) before using external data.
