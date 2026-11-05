# 1.20.1 port.4: native ingest and streaming allocation fixes

Ported from the reviewed Prominence compatibility layer:
- Reuse one cleared voxel section per ingest task and a bounded 32-entry pool of 32768-element index arrays. Ownership ends after the task, including stale/error exits. Outside an ingest scope the decoder returns independent arrays as before. Wire format is unchanged.
- Reuse serialization scratch per worker; returned payload arrays remain independent of that scratch.
- Limit synchronous CHUNK_LOAD ingest to eight chunks per tick, forwarding excess work to the existing bounded retry path.
- Keep ingest engine references until normal shutdown, avoiding repeated idle database teardown/reopen cycles. Shutdown still releases references.
- With Iris active, defer the first renderer creation to Voxy's existing Iris hook to avoid initializing it immediately before Iris replaces the pipeline.

Verified Java 17 remapJar + test, including protocol bounds/round trips and added scratch ownership/reset/error tests. No running mod files or production server were changed. Real join/dimension FPS/MSPT and memory impact remain unmeasured. Pack-specific default-world index warmup remains in the compatibility layer, rather than forcing it on every server.

The compatibility JAR retains its older fingerprinted fixes. Changed versions/classes fail their guards and do not apply the same injection twice. It does not automatically download these separate mod JARs.
