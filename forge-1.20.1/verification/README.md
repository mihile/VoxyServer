# Isolated Forge verification

These checks are for disposable local fixtures. The graphical harness connects
to `127.0.0.1:25585`, changes a block, switches dimensions and reconnects; it
must not be pointed at a real world or public server.

Build the two VoxyServer JARs first. Start a separate Forge 47.3.0 server on
loopback port 25585 with the server JAR and the matching Neo Voxy JAR. Use an
offline fixture world and grant the offline player `VoxyTest` operator rights.
Import its existing chunks with `voxyserver import existing all` before the
client test. Accept the Minecraft EULA for a new fixture only if appropriate.

Supply the Embeddium 0.3.31 release JAR as `client/build/deps/embeddium-0.3.31.jar`
or pass its absolute path as `-Pembeddium_jar=...`. Run from the Forge project:

```powershell
$env:JAVA_HOME = $env:JAVA_HOME_17
.\gradlew.bat -p verification/client runClient '-Pembeddium_jar=C:/path/to/embeddium.jar'
python verification/vanilla_probe.py
```

The harness runs the actual distributable mod JARs remapped into the development
namespace. `prepare_mods.py` restores their original nested dependency metadata
after Loom's remapping. It writes only to `verification/client/build/game/`.
Check `verification/client/build/game/forge-verification-result.txt` for `PASS`;
the Gradle process exit code alone is not sufficient. The vanilla protocol
probe should print `VANILLA_PROBE PASS`. Stop the fixture server afterwards.
