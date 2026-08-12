# docs/spec/ — the portable shelf

Rules of the world, written so a stranger's implementation can verify itself
against them (D-058). The shelf exists because of one measured fact: sixteen
years after The Matrix Online's servers closed, its emulator scene has restored
the whole explorable world and still cannot restore combat — because the world
was data and the combat was logic inside a closed implementation.

**What a spec is:** a byte grammar, a record kind, a derivation, a line family —
stated implementation-independently, with conformance vectors a foreign
implementation can check itself against.

**What a spec is not:** narrative, rationale, or history. Those belong to the
five canon documents and to the MADR records under `docs/adr/`. A spec says what
is true of the world; it never says why we chose it.

**The price of admission:** `SpecDrift` checks every spec against the running
implementation, so a stale spec fails a build instead of misleading a reader. A
spec nobody can falsify is not a spec — it is a wish with a filename.

| Spec | Covers |
|---|---|
| _(the shelf opens with D-058; the first specs land as their units merge)_ | |
