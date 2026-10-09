# VoxyServer for Minecraft 1.20.1

Unofficial Fabric backport of [PooSmacker/VoxyServer](https://github.com/PooSmacker/VoxyServer),
based on the upstream `26.3` branch at `def7398c6bb20dba652fbcbd3a5adcc5947e586a` (1.2.4).
The server voxelizes chunks into Voxy LODs and streams them to connected clients.
Live block updates and resumable import of existing region files are supported.

**Minecraft 1.20.1 · Fabric · Java 17 · VoxyServer 1.2.4+1.20.1-port.1**

## Download and installation

Get the installable JAR from [this fork's releases](https://github.com/mihile/VoxyServer/releases).
The `-sources.jar` is for source code, not installation.

Install the following on both the client and dedicated server:

- `VoxyServer-1.2.4+1.20.1-port.1.jar`
- [Voxy 0.2.20-beta for 1.20.1, port.8](https://github.com/mihile/voxy/releases/tag/v0.2.20-1.20.1-port.8)
- Fabric API for Minecraft 1.20.1 (tested with 0.92.6)

The client also needs Sodium 0.5.13 for 1.20.1. ModMenu 7.2.2 and
Cloth Config 11.1.136 are optional and enable the client settings screen.
The mod bundles LWJGL 3.3.1 for dedicated servers, including Windows,
Linux x64 and Linux ARM64 natives; Voxy supplies its storage libraries.
Voxy itself is a separate dependency and is not included in this JAR.
Vanilla clients can join without receiving LOD packets. Remote streaming
is disabled in singleplayer, following upstream behavior.

### Voxy login compatibility

Use the linked Voxy port.8 build. Voxy port.7 starts its client session too
early on the Netty thread and can select `.voxy/saves/UNKNOWN` as its cache root.
The fix is in Voxy port.8: session initialization now follows vanilla's client
thread dispatch and game-mode setup, before the renderer is created.
This VoxyServer port.1 release uses that Voxy fix and has no additional
session-start override or deferred-initialization compatibility mixins.

Existing UNKNOWN caches are not migrated or deleted. New connections use
Voxy's normal per-address cache directory, with dimension-specific world IDs.
Hash sidecars retain upstream's world-ID-keyed layout. They are not a separate
server UUID scheme, so do not infer unique server identity from a world ID alone.

## Porting changes

- Targets Java 17 and Minecraft 1.20.1 with Mojang mappings.
- Uses Fabric's 1.20.1 channel API in place of the newer custom-payload API.
- Splits cache manifests into at most 1,024 entries for the 32,767-byte
  serverbound limit, and splits LOD batches by encoded size for the 1 MiB
  clientbound limit.
- Adapts chunk, palette, height, registry, identifier and region-file APIs.
- Includes server-side LWJGL natives. The upstream update feed for newer
  Minecraft versions is disabled for this backport.

## Build

Run Gradle/Loom on JDK 21. Output classes target Java 17.
Download the linked Voxy port.8 JAR and put it at `libs/voxy.jar`:

```powershell
New-Item -ItemType Directory -Path libs -Force
Copy-Item C:/path/to/voxy-0.2.20-beta+1.20.1-port.8-33c0dff7.jar libs/voxy.jar
.\gradlew.bat build
```

Alternatively pass `-Pvoxy_jar=C:/path/to/voxy.jar`.
The installable output is `build/libs/VoxyServer-1.2.4+1.20.1-port.1.jar`.

## Verification

The release was rebuilt against Voxy port.8 and passed six protocol tests.
An isolated Java 17 graphical client running the actual remapped JAR passed
first login, repeated reconnects, Nether round trips, two servers with a cloned
world, rapid disconnects and cleanup. Stored LODs, hash records and the renderer's
world engine were inspected. The running Prominence client/server were also
checked read-only with Voxy port.8 and VoxyServer port.1.

See [validation results](docs/VALIDATION_1_20_1.md) and the optional
[graphical verification harness](verification/lifecycle/README.md).
These checks do not establish a cause for visual glitches or FPS problems.

## Credits and license

VoxyServer by dripps / PooSmacker, Voxy by MCRcortex, and the authors of
the Voxy 1.20.1 backport. This fork preserves the upstream GPL-3.0 license.
It is an unofficial Minecraft-version backport, not the upstream 26.3 release.

## config

config file is generated at `config/voxyserver.json` on first run.

| option | default | description |
|--------|---------|-------------|
| `lodStreamRadius` | `256` | radius in chunks to stream LODs around each player |
| `maxSectionsPerTickPerPlayer` | `10` | max LOD sections sent per player per tick cycle |
| `sectionsPerPacket` | `50` | max LOD sections bundled into a single network packet |
| `tickInterval` | `5` | server ticks between each streaming cycle |
| `workerThreads` | `3` | number of worker threads for voxy to use |
| `generateOnChunkLoad` | `true` | voxelize chunks as they load on the server |
| `dirtyTrackingEnabled` | `true` | revoxelize and push LODs when blocks change |
| `dirtyTrackingInterval` | `40` | ticks between dirty chunk flushes (40 = 2 seconds) |
| `debugTrackingEnabled` | `false` | output internal server stats to the console periodically |
| `debugTrackingInterval` | `200` | ticks between dumping tracking stats (200 = 10 seconds) |

### example config

```json
{
  "lodStreamRadius": 256,
  "maxSectionsPerTickPerPlayer": 100,
  "sectionsPerPacket": 50,
  "generateOnChunkLoad": true,
  "tickInterval": 5,
  "workerThreads": 3,
  "dirtyTrackingEnabled": true,
  "dirtyTrackingInterval": 40,
  "debugTrackingEnabled": false,
  "debugTrackingInterval": 200
}
```

higher `lodStreamRadius` means more LOD coverage but more storage and bandwidth. `maxSectionsPerTickPerPlayer` controls how fast LODs are sent to new players or when moving into unexplored areas. `sectionsPerPacket` controls how many sections are packed into each network packet (higher = fewer packets but larger each). lower `tickInterval` = more frequent streaming checks.

### client config

if the client has [ModMenu](https://modrinth.com/mod/modmenu) and [Cloth Config](https://modrinth.com/mod/cloth-config) installed, players can open the voxyserver config screen from the mods list to adjust their personal streaming preferences:

| option | description |
|--------|-------------|
| **Enable LOD Streaming** | toggle whether the server sends LOD data to you |
| **LOD Stream Radius** | personal LOD radius in blocks, 0 = use server default |
| **Max Sections Per Tick** | personal rate limit for sections sent per tick, 0 = use server default |

these values are capped at the server's configured maximums and sent to the server automatically when saved.

client preferences are stored in `config/voxyserver-client.json` with a default profile plus per server overrides keyed by their `host:port`. if a server has no saved override yet, the default profile is used. per server overrides can only be edited while connected to that server.

## commands

server admins can import existing generated chunks into voxy storage with the following commands:

- `/voxyserver import existing all`  
  imports all loaded dimensions that have a `region` folder
- `/voxyserver import existing current`  
  imports the current dimension of the executing player
- `/voxyserver import existing dimension <dimension>`  
  imports a specific loaded dimension, for example `minecraft:overworld`
- `/voxyserver import existing status`  
  shows the current import status
- `/voxyserver import existing cancel`  
  cancels the active import job

imports run sequentially and read existing `region/*.mca` files directly, which is much faster than waiting for chunks to be loaded normally. imported dimensions are refreshed for connected voxy clients automatically once the import completes.

imports can be resumed if eg server crashes

add `force` to rebuild the selected dimensions regardless of previous progress:

- `/voxyserver import existing all force`
- `/voxyserver import existing current force`
- `/voxyserver import existing dimension minecraft:overworld force`

checkpoints are inside each dimension's LOD `storage/import-checkpoints/` directory. deleting the lod storage also deletes its import progress.

## storage

LOD data is stored per world at `<world>/voxyserver/`. this can be safely deleted to regenerate all LOD data.

## license

This mod is licensed under GNU GPLv3.
