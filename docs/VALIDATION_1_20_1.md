# Minecraft 1.20.1 port.1 validation

Date: 2026-10-09 (KST).

## Tested versions

- VoxyServer 1.2.4+1.20.1-port.1, rebuilt from the original port.1 sources.
- Voxy 0.2.20-beta+1.20.1-port.8-33c0dff7 (commit `33c0dff796b9d5f8e27538066258a42b6ee55fd8`).
- Minecraft 1.20.1, Fabric Loader 0.17.2, Fabric API 0.92.6+1.20.1.
- Sodium 0.5.13, Java 17.0.18, Windows / NVIDIA RTX 3080.

## Build and automated tests

`gradlew build` passed. Six JUnit tests cover control-message round trips,
palette bit widths, maximum-sized manifest packets, dense LOD packet splitting,
vanilla packet envelopes and malformed/trailing data.
The graphical harness is outside the release source sets and is not bundled.

## Production-mappings graphical test

The test used the remapped installable VoxyServer JAR, not a development-only
classpath, with two isolated local servers and a fresh client game directory.

| Check | Result |
| --- | --- |
| First login | Voxy instance and RemoteIngestService initialized on Render thread |
| Login errors | Network handle null: 0; Server info null: 0 |
| Cache selection | Per-address roots for 127.0.0.1:25585 and 127.0.0.1:25586; no UNKNOWN root |
| Overworld | 1,728 hash records and 1,728 persisted level-0 sections |
| Nether | 200 hash records and 200 persisted level-0 sections |
| Renderer | Current world's WorldEngine, baked models, uploaded geometry and frame execution confirmed |
| Dimension changes | Nether and return to Overworld passed |
| Reconnects | Correct cache reused, renderer recreated, LOD storage remained usable |
| Second server | Same cloned world ID, separate Voxy storage root; fresh cache populated |
| Rapid disconnect | Session and ingest service ended; following reconnect passed |
| Cleanup | No instance/session, inbound queue, reserved batch, or Remote Ingest worker remained |
| Shutdown | Harness PASS; client and both servers exited with code 0 |

The hash sidecar layout remains keyed by world ID, as in port.1. The second-server
check verifies separate Voxy storage roots; it does not claim a new independent
hash namespace or unique server UUID. Long-running adversarial connection timing
and arbitrary mod/shader combinations were not exhaustively tested.

## Read-only check of the running modpack

The installed client and server had matching Voxy port.8 and VoxyServer port.1
JAR hashes. The actual client log showed Render-thread initialization and an
IrisVoxyRenderPipeline / MDICSectionRenderer. The server accepted protocol 2.
The client used the normal localhost_25563 cache and its hash sidecar contained
8,395 records at inspection time. Counts may change while the game runs.
No Network handle null occurred in that current client log.

The active game/server JARs, settings, logs and caches were not changed during
verification. Their old UNKNOWN cache was retained. No conclusion about visual
glitches or FPS causality is drawn from the initialization logs.
