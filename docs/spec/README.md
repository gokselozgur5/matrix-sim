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

**The price of admission:** a spec is checked against the running implementation
by a probe that reads it, so a stale spec fails a build instead of misleading a
reader. A spec nobody can falsify is not a spec — it is a wish with a filename.

**The shelf is empty, and the reader does not exist yet.** This paragraph used
to name `SpecDrift` in the present tense; there is no such probe, and there is
nothing on the shelf for it to read (#1243):

```sh
ls docs/spec/                              # README.md, and nothing else
grep -rn 'SpecDrift' probes/ tools/ src/   # nothing
```

That promise has cost nothing so far, because no spec has relied on it. **The
first spec pays it.** A spec lands with the probe that reads it, in the same
pull request, or it does not land — which is this shelf's own rule applied to
its first inhabitant rather than deferred past it.

The order matters because this tree spent one day finding the same shape four
times elsewhere: a workflow that could not parse for eleven runs (#1203), a
fourteen-case suite no lane executed (#1212), a catalog row claiming a judge
that was never a step (#1210), and the determinism pass that had never run at
all (#1233). Every one was a reader promised and not built. This is the fifth,
caught before it could mislead anybody, and the repair is to date the promise
rather than to keep it undated.

| Spec | Covers |
|---|---|
| _(the shelf opens with D-058; the first specs land as their units merge)_ | |
