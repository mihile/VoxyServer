# Isolated graphical lifecycle verification

This optional test mod is excluded from the release JAR. It uses Voxy port.8
and VoxyServer port.1 to check cache selection, received hashes, actual storage,
renderer engine identity, dimensions, reconnects and shutdown cleanup.
No pixel-based image comparison is performed.

## Fixture

Prepare a disposable Fabric 1.20.1 server at `build/smoke-server` using Fabric
Loader 0.17.2, Fabric API 0.92.6, the linked Voxy port.8 JAR, and the built
VoxyServer port.1 JAR. Name the Voxy fixture file `voxy.jar` and remove other
Voxy versions from that disposable mods folder. Accept the Minecraft EULA
for your test server before starting it.

Use `server-ip=127.0.0.1`, `online-mode=false`, `level-name=smoke-world`,
`level-type=minecraft:flat`, and view/simulation distance 2. Set VoxyServer's
LOD stream radius to 16 and workerThreads to 2. Start the fixture once to
create smoke-world, then stop it cleanly before running these scripts.
The runner clones that world into build/lifecycle-server-b and uses ports
25585 and 25586. It grants its offline LifecycleTest player operator rights
in these fixtures to execute dimension teleport commands.

Do not use production worlds or game folders as fixtures. A graphical Windows
session is required; adapt the JDK 17 executable in run.py to your machine.

## Run

```powershell
.\gradlew.bat build
Copy-Item build/libs/VoxyServer-1.2.4+1.20.1-port.1.jar build/smoke-server/mods/
# First run in development mappings to resolve the launch classpath:
python verification/lifecycle/run.py
# Then verify the installable JAR under Java 17 and production mappings:
.\gradlew.bat -I verification/lifecycle/lifecycle.init.gradle remapLifecycleHarness
python verification/lifecycle/production_setup.py
python verification/lifecycle/run.py --production
```

The init script supplies LWJGL 3.3.1 and Voxy's nested libraries for Loom's
development classpath. The normal release build does not use that script.
production_setup.py reads existing local dependency caches and creates a fresh
timestamped test client folder. Its location is in build/production-client-directory.txt.
Results are in that folder's lifecycle-result.txt and build/lifecycle-*.log.
Check the harness PASS result as well as all process exit codes.
The offline test token is 0; no account credentials are used.
