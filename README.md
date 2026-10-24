# VoxyServer 1.20.1 backport

a fabric server side mod that voxelizes chunks into LODs using [voxy](https://github.com/MCRcortex/voxy) and streams them to connected clients. players with voxy installed will receive LOD data from the server automatically, no client side world scanning/loading needed.

**Target: Minecraft 1.20.1 | Fabric | Java 17**

`port.3` fixes client hash-sidecar replacement failures and keeps optional
metadata warnings in the log instead of repeatedly displaying paths on the HUD.
See the [port.3 notes](docs/RELEASE_PORT_3.md) for behavior and validation limits.

Backported from the upstream `26.3` branch, commit
`def7398c6bb20dba652fbcbd3a5adcc5947e586a` (VoxyServer 1.2.4).
The reference Voxy build is `0.2.20-beta+1.20.1-port.7`, from the local
`voxy 1.20.1` project.

This port uses the 1.20.1 Fabric channel API and Mojang mappings. Client cache
manifests are split into at most 1,024 entries to stay below the 32,767-byte
serverbound limit. LOD batches are split by encoded size as well as section
count to stay below the 1 MiB clientbound limit. The newer upstream update
feed is disabled for this backport because it targets other Minecraft versions.

`port.2` defers Voxy port.7's early login initialization until the current
connection, player, world, interaction manager and server identity are ready
on the client thread. Stream work and acknowledgments stay bound to their
original connection and world engine. Hash manifests now use the same server
namespace as the actual Voxy cache, in addition to the dimension's world ID.
Existing UNKNOWN caches and legacy hash files are preserved. See the
[initialization fix report](docs/CLIENT_INITIALIZATION_FIX.md) for the cause,
changed files, verification and offline cache handling.

## how it works

when chunks load on the server, they get voxelized into voxy's LOD format and stored in a per world database. when a player with voxy connects, the server streams LOD sections to them in a spiral outward from their position. as the player moves, new sections are sent automatically.

block changes (building, explosions, etc) are detected and the affected LOD sections are revoxelized and pushed to clients automatically.

existing worlds can also be backfilled from their region files with an admin command, so already explored terrain can be voxelized without needing players to revisit it chunk by chunk.

players without voxy are unaffected.

## building

Build your Fabric 1.20.1 Voxy backport first, then copy its **remapped** JAR to
`libs/voxy.jar`. Voxy is a separate dependency and is not bundled in this mod.

PowerShell example for the local reference project:

```powershell
New-Item -ItemType Directory -Path libs -Force
Copy-Item '../voxy 1.20.1/build/libs/voxy-0.2.20-beta+1.20.1-port.7-a15344d6.jar' 'libs/voxy.jar'
.\gradlew.bat build
```

Alternatively, pass the path directly:

```powershell
.\gradlew.bat build '-Pvoxy_jar=C:/path/to/voxy-1.20.1.jar'
```

The Gradle/Loom build runs on JDK 21 or newer; the resulting mod targets Java 17.
The installable JAR is `build/libs/VoxyServer-1.2.4+1.20.1-port.2.jar`.
The `-sources.jar` is for source code, not installation.

## installation

On a **dedicated Fabric 1.20.1 server**, install:

- This VoxyServer JAR
- Your Voxy 1.20.1 backport JAR
- Fabric API for 1.20.1

Sodium is required by Voxy on the client. It is not needed on the dedicated
server. VoxyServer bundles LWJGL 3.3.1 for the server, including Windows, Linux
x64 and Linux ARM64 natives; Voxy provides its own storage libraries.

On a **Fabric 1.20.1 client**, install the same VoxyServer JAR, the Voxy backport,
Fabric API, and Sodium 0.5.13 for 1.20.1. ModMenu 7.2.2 and Cloth Config 11.1.136
are optional and enable the client settings screen. Vanilla clients can still
join; they receive no LOD packets.

Both sides need this 1.20.1 port for remote LOD streaming. It remains disabled
in singleplayer, following the upstream behavior.

## validation

- `gradlew build`: Java 17 compilation, remapped JAR, and 19 tests covering
  protocol, connection lifecycle, cache identity, and bounded streaming metadata.
- Protocol tests cover control messages, all relevant palette bit widths,
  maximum cache manifests, dense batch splitting, vanilla packet envelopes,
  and malformed/trailing payloads.
- The packaged mod was started with the reference Voxy and Fabric API on a
  dedicated Minecraft 1.20.1 server under Java 17. Storage creation, live block
  updates, region import, import checkpoint reuse, and clean shutdown passed.
- A local protocol test client completed the handshake, decoded 254 LOD packets
  containing 3,481 sections, and acknowledged them without errors. Import
  completion also established a fresh streaming session.
- An isolated graphical client also ran the packaged port.2 JAR on Java 17:
  first login, reconnects, dimension round trips, two servers with the same
  world ID, and rapid disconnects passed. Received hashes and stored LOD
  sections matched; the renderer used the correct engine and uploaded geometry.
- The full Prominence modpack and its shader/resource-pack combinations have
  not been run with this fix. These checks do not establish the cause of any
  visual glitches or FPS issues.

## config

config file is generated at `config/voxyserver.json` on first run.

| option | default | description |
|--------|---------|-------------|
| `lodStreamRadius` | `256` | radius in chunks to stream LODs around each player |
| `maxSectionsPerTickPerPlayer` | `100` | max LOD sections sent per player per tick cycle |
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
