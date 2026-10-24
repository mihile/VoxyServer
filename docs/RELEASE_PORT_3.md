# Minecraft 1.20.1 port.3: hash sidecar persistence

Includes all port.2 protocol, lifecycle and bounded metadata improvements.
Install the new build on both the server and clients using remote LODs.

The client periodically persists its acknowledged section hashes. Voxy's
ERROR logger also displays error text on the player's HUD, which exposed long
filesystem paths whenever Windows rejected an atomic `.tmp` replacement.
The failure was observed as `AccessDeniedException`; the external cause of
that denial (for example a transient file handle) has not been established.

- Write a complete sidecar through a unique temporary file in its destination
  directory. Close the writer before replacement.
- Fall back from unsupported/denied atomic replacement to ordinary replace.
  Retry failed moves at most three times with 25/50ms worker-thread delays.
- On failure, retain the loaded state's dirty marker for the periodic retry.
  Log a bounded WARN instead of sending filesystem paths to the HUD. Log
  recovery once writing succeeds. Other Voxy errors keep their existing HUD
  behavior.
- Clean up this write's temporary file, preserve the binary sidecar format,
  and leave actual LOD storage, server/world cache namespaces, and legacy
  cache files unchanged. The existing bounded loaded-world eviction remains;
  failed metadata evicted from memory can be rebuilt by a later stream.

Validation: Java 17 target build and 26 passing unit tests. Seven new file-I/O
tests cover replacement, Windows atomic-access-denied fallback, unsupported
atomic moves, transient and permanent failures, failed serialization,
temporary-file cleanup and interruption. Existing protocol, connection and
metadata-limit tests also pass. A new in-game Windows/driver/session test was
not performed. This is a prerelease pending that validation.
