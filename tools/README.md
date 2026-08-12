# tools/ — process instruments

Scripts that execute the process the documents promise. Nothing here is part
of the daemon build (D-009 still holds: the SIMULATION needs only `javac`);
these are the shop's own jigs, kept under D-030's rule that crew tooling is
part of the shop.

| Tool | What it does |
|---|---|
| `release.sh` | Cuts a phase release from `main` — refuses unless compile, `--selftest`, and `--bench` are green at cut time, then stamps their live output into the notes under your prose. Usage: `tools/release.sh vX.Y.Z "Title" notes.md` with notes written from `RELEASE_NOTES_TEMPLATE.md`. |
| `subissue.sh` | Cuts a child issue and hangs it on its parent in one motion — inherits the parent's milestone, refuses a closed parent (a tree does not grow from a closed branch). Usage: `tools/subissue.sh <parent> "<title>" body.md [--label L] [--milestone M]`. |
| `issuetree.sh` | Prints an issue's tree root to leaves: parents carry their child count, leaves carry a dot. Usage: `tools/issuetree.sh <issue> [max-depth]`. |
| `attribution.sh` | Asks the only question the contribution graph asks: does GitHub resolve this commit's author to the account that owns the repository? Answers per commit with a denominator, and prints the `git config` that fixes a FAIL. Usage: `tools/attribution.sh` (HEAD) · `--pr N` · `--sha SHA`. Wired into CI on every pull request. |
| `balance.sh` | Reads a day's contribution mix from the same API the profile graph uses and judges it against D-060's four quarters — names the lagging leg and the count that clears it. Speaks both `date(1)` dialects, so it runs on the operator's macOS box and on `ubuntu-latest`. The API roots at `viewer`, so every run opens with a `SUBJECT` line naming the login it read and exits 6 unless that login owns the repository being judged — a token for the wrong account used to print a confident `verdict=EMPTY`. `contributionsCollection` is an *account* statistic, so the default verdict answers "was the account balanced" and not "was matrix-sim balanced" — every line carries `scope=`, `--repo` asks the narrower question, and a `SCOPE` line prints both readings and their delta so a green verdict can never quietly mean somewhere else. That line also carries `merge=`, the repository's own enabled merge strategies, because the merge button is a term of the law and not a detail of it (D-061) — a run whose token cannot read those settings says `merge=unreadable` rather than guessing. The default stays `account` on the record, not in the tool (D-060 errata, #821). Usage: `tools/balance.sh [--account\|--repo] [--for OWNER/NAME] [--week\|--month\|--days N] [YYYY-MM-DD]`; the window flags judge a rolling span ending on the named day and print each of its days judged alone; `tools/balance.sh --datecheck` judges the day arithmetic alone, with no token and no network. |

House rules:

- A tool never mutates `src/` or `docs/`; it builds, verifies, tags, publishes.
- A tool's failure mode is loud and early (`set -euo pipefail`, explicit FATAL
  lines) — a half-cut release is worse than none.
- Evidence is produced at run time, never pasted from memory. If a lock cannot
  be reproduced when the tool runs, the tool must refuse.
