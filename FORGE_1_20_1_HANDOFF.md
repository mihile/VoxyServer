# VoxyServer 1.20.1 Forge (`1.20.1_forge`)

- **Branch**: `1.20.1_forge` (branched from `backport/1.20.1` @ `84374ed`)
- **Remote (`fork`)**: `https://github.com/mihile/VoxyServer.git`
- **Project Location**: [`forge-1.20.1/`](forge-1.20.1/README.md) (the Fabric 1.20.1 backport remains at the repository root)
- **Artifact / Version**: `VoxyServer-Forge`, `mod_version = 1.2.4+1.20.1-forge.2`, Minecraft `1.20.1`, Forge `47.3.0+`, Java 17
- **Companion Voxy Mod**: `voxy` (`https://github.com/mihile/voxy.git`), branch `1.20.1_forge`

## Build & Test (`forge-1.20.1/`)
```powershell
Set-Location forge-1.20.1
.\gradlew.bat test --no-daemon
.\gradlew.bat build --no-daemon '-Pvoxy_jar=C:\path\to\voxy-0.4.0beta+hybrid.1-mc1.20.1-forge-client.jar'
```
- Without `-Pvoxy_jar`, `build.gradle` defaults to `../../neovoxy/editions/forge-1.20.1-client/build/libs/neo-voxy-0.4.0beta-mc1.20.1-forge-client.jar`.
- Output JARs in `forge-1.20.1/build/libs/`:
  - Server: `VoxyServer-Forge-1.2.4+1.20.1-forge.2-server.jar`
  - Client: `VoxyServer-Forge-1.2.4+1.20.1-forge.2-client.jar`
  - Sources: `VoxyServer-Forge-1.2.4+1.20.1-forge.2-sources.jar`
