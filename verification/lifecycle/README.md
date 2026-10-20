# Isolated graphical lifecycle verification

This optional harness is kept outside the mod source sets and release JAR.
It drives real Minecraft connections in a separate game directory, checks
Voxy's actual cache root, persisted section enumeration, hash sidecars and
renderer engine, then tests reconnects, dimensions and connection cancellation.
It also inspects pending manifest/ingest work after shutdown.

The fixture here is a local offline Fabric 1.20.1 server installed in
`build/smoke-server`, with Fabric API, Voxy port.7 and the newly built VoxyServer
JAR in its mods folder. It has `eula=true` and binds only to `127.0.0.1`.
The runner clones its world into `build/lifecycle-server-b` and uses ports
25585 and 25586. Do not point these scripts at a production server or game
directory. The test grants its offline player operator rights in these fixtures
for dimension teleport commands. A graphical Windows session and JDK 17 are
required for the packaged-JAR run; adapt the Java executable in run.py if needed.

For a development run (also produces the dependency classpath used below):

```powershell
.\gradlew.bat build
Copy-Item build/libs/VoxyServer-1.2.4+1.20.1-port.2.jar build/smoke-server/mods/VoxyServer-1.2.4+1.20.1-port.1.jar
python verification/lifecycle/run.py
```

For the Java 17 production-mappings run using the actual remapped mod JAR:

```powershell
.\gradlew.bat -I verification/lifecycle/lifecycle.init.gradle remapLifecycleHarness
python verification/lifecycle/production_setup.py
python verification/lifecycle/run.py --production
```

The init script pins LWJGL 3.3.1 and supplies Voxy's nested library dependencies
for Loom's development classpath. The release build does not use this script.
`production_setup.py` only reads the already resolved local library caches.
Its argument file contains an offline token of `0` and no account credentials.

Packaged-JAR runs use a new timestamped game folder each time; its location is
written to `build/production-client-directory.txt`. Results are in that folder's
`lifecycle-result.txt` and the corresponding `build/lifecycle-*.log` files. A successful run ends with
`LIFECYCLE_TEST PASS`, a client exit code of zero, and clean server exits.
The runner itself reports exit states; read the harness result as well as the
process exit code because Minecraft can exit normally after a failed assertion.
No pixel-based comparison of the rendered scene is performed.

For a shorter deterministic cancellation check in a separate fresh client folder:

```powershell
python verification/lifecycle/production_setup.py --stress
python verification/lifecycle/run.py --production --stress-only
```

The test-only WorkerBarrier holds real incoming ingest tasks during a dimension
transition so that cancellation is observable even on a fast CPU. It is released
after disconnect; executor shutdown interrupts running workers. The production
mod JAR has no barrier or test entrypoint. Fresh folders keep repeat tests useful:
a fully populated cache can suppress new LOD delivery through the normal manifest
protocol. Earlier results and production caches are preserved.
