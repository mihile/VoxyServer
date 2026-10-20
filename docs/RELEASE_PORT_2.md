# VoxyServer 1.2.4 - Minecraft 1.20.1 port.2

Stable Fabric 1.20.1 backport, Java 17 or newer. Install this JAR on both the dedicated server and clients using remote LOD streaming.

- Defer client initialization until the current connection, player, world, interaction manager, and server identity are available.
- Keep stream sessions, acknowledgments, ingest work, and cache namespaces attached to their original connection and world engine across reconnects and dimension changes.
- Limit acknowledged section hashes to 262,144 per player at insertion, with bounded candidate inspection and incremental out-of-range pruning. Pruning continues during manifest gates, retry backoff, and exhausted transmission credit.
- Bound section-version and hash metadata and reduce allocation during deterministic section palette/hash processing.
- Retain bounded manifest/packet sizes and disable the upstream update feed for incompatible Minecraft versions.

Use a compatible [Voxy 1.20.1 backport](https://github.com/mihile/voxy/releases/tag/v0.2.20-1.20.1-port.8), Fabric API, and Fabric Loader 0.17.2 or newer. Clients also need Sodium. Mod Menu and Cloth Config are optional. LWJGL and server natives are bundled; Voxy is a separate dependency.

For generating new distant terrain without saving full chunks, install [Voxy LOD Only 0.9.0](https://github.com/mihile/voxy-lod-only/releases/tag/v0.9.0-1.20.1) on the server only. VoxyServer itself voxelizes loaded chunks and imports existing saved regions.

Release validation: Java 17 target build and 19 passing unit tests covering protocol, connection lifecycle, cache identity, and streaming metadata limits. Earlier isolated graphical/client checks are documented in [the initialization report](CLIENT_INITIALIZATION_FIX.md); they are not a new full-modpack validation of the final release JAR. Existing UNKNOWN caches and legacy hash files are preserved.

Download the ordinary JAR for installation; `-sources.jar` is source code. `SHA256SUMS.txt` lists both assets.
