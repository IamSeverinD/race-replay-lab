# Data sources

## Default demo

The default application experience is generated locally by
`SyntheticReplaySimulation`. Driver codes, team names, telemetry and movement
are fictional and deterministic. The circuit is a generic shape authored for
this project.

No real race data is required or downloaded for the default demo.

## Optional external data

The source tree contains an optional OpenF1 adapter. OpenF1 is an independent
third-party service. Its project is published under CC BY-NC-SA 4.0 at the time
of writing, but users must verify the current provider terms and whether those
terms cover the specific API responses they intend to use.

The OpenF1 project license cannot grant rights held by racing series, teams,
drivers, circuits, photographers or other third parties.

Do not commit downloaded API responses, processed timelines, driver images or
other real-world racing assets to this repository without documented permission
and a compatible redistribution license.

The application session selector reads the OpenF1 sessions endpoint only after
the user presses **IMPORT OPENF1**. It filters out cancelled, future and running
sessions using the `date_end` supplied by OpenF1 and a UTC cutoff captured when
the application starts. Confirming a selection starts the separate local data
download and processing pipeline.

Relevant references:

- <https://openf1.org/>
- <https://github.com/br-g/openf1/blob/main/LICENSE>
- <https://www.formula1.com/en/information/guidelines.html>
