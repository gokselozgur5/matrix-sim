#!/usr/bin/env bash
# tools/attribution.sh — does the graph know who built this? (D-060, #910)
#
# Usage: tools/attribution.sh                 judge HEAD and the configured address
#        tools/attribution.sh --pr N          judge every commit on pull request N
#        tools/attribution.sh --sha SHA       judge one commit
#        tools/attribution.sh --for OWNER/NAME    name the repository (default: origin)
#        tools/attribution.sh --fix-cmd      print the repair for this branch, and nothing else
#
# A REPAIR THAT CHANGES WHEN THE WORK HAPPENED IS NOT A REPAIR (#1012). This tool
# printed `--reset-author` for two hundred commits worth of advice, and that flag
# resets the author DATE along with the identity: run it on a six-commit branch and
# all six collapse onto the instant of the repair, including the ones that needed
# nothing done to them. Yesterday work then claims to have happened tonight — the
# same class of defect this tool exists to catch, a number that looks right and is
# not, and it degrades the very graph the tool serves. `--author=` sets the identity
# and leaves GIT_AUTHOR_DATE alone.
#
# WHAT THIS EXISTS FOR. `totalCommitContributions` — the number D-060's commit leg is
# read from, and the number the profile graph draws — credits a commit only when the
# commit's AUTHOR EMAIL is verified on the account being read. An address that belongs
# to a different account, or to no account, produces a commit that is on `main`, is
# visible to every reader, has the owner's name on it, and does not exist as far as the
# meter is concerned.
#
# That is not a hypothetical. Before #910, every non-merge commit in this repository —
# 258 of them, every unit commit ever shipped — carried an address resolving to the
# owner's OTHER account. The commit leg had therefore never counted a commit anyone
# here built; it counted merge commits, and only the ones GitHub's own UI authored:
#
#     day          verified-address merges   other-address merges   API commit leg
#     2026-08-10            2                        0                    2
#     2026-08-11           47                       15                   47
#     2026-08-12           56                        0                   56
#
# Exact on all three days. The fault was a global `git config`, which is why nobody
# typed it and nobody saw it — and why a note in a document would not have held. This
# is the reader that makes it fail loudly instead.
#
# WHAT IT DOES NOT DO. It does not judge whether the human is the right human; it asks
# only the question the graph asks — does GitHub resolve this commit to the account
# that owns the repository. It cannot see an unpushed commit, because the resolution
# lives on GitHub's side and not in the object.

set -euo pipefail

MODE=head
PR=""
SHA=""
REPO=""
for arg in "$@"; do
  case "$arg" in
    --pr)  MODE=pr;  PR="__next__" ;;
    --sha) MODE=sha; SHA="__next__" ;;
    --for) REPO="__next__" ;;
    --fix-cmd) MODE=fixcmd ;;
    --selftest) MODE=selftest ;;
    -*)    echo "FATAL unknown flag: $arg" >&2; exit 2 ;;
    *)
      if   [ "$PR"   = "__next__" ]; then PR="$arg"
      elif [ "$SHA"  = "__next__" ]; then SHA="$arg"
      elif [ "$REPO" = "__next__" ]; then REPO="$arg"
      else echo "FATAL unexpected argument: $arg" >&2; exit 2; fi ;;
  esac
done
[ "$PR"   = "__next__" ] && { echo "FATAL --pr wants a number after it" >&2; exit 2; }
[ "$SHA"  = "__next__" ] && { echo "FATAL --sha wants a commit after it" >&2; exit 2; }
[ "$REPO" = "__next__" ] && { echo "FATAL --for wants OWNER/NAME after it" >&2; exit 2; }

if [ -z "$REPO" ]; then
  REPO="$(git remote get-url origin 2>/dev/null | sed -E 's#.*github\.com[/:]##; s#\.git$##' || true)"
fi
[[ "$REPO" =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] || {
  echo "FATAL cannot tell which repository this is; pass --for OWNER/NAME" >&2; exit 2; }

# The account the commits must resolve to is the repository's owner, read from the API
# rather than split off the path — an organisation-owned fork would make the path lie.
OWNER="$(gh api "repos/$REPO" --jq '.owner.login' 2>/dev/null || true)"
[ -n "$OWNER" ] || { echo "FATAL cannot read the owner of $REPO (token? network?)" >&2; exit 3; }

# The address the repair should write, built rather than left as a placeholder
# (#1012). GitHub's noreply form is <id>+<login>@users.noreply.github.com and is
# verified on the account by construction, so it is the one address this tool can
# name without asking anyone which of theirs they have verified.
OWNER_ID="$(gh api "users/$OWNER" --jq '.id' 2>/dev/null || true)"
if [ -n "$OWNER_ID" ]; then
  OWNER_ADDR="${OWNER_ID}+${OWNER}@users.noreply.github.com"
else
  OWNER_ADDR="<an address verified on $OWNER>"
fi

# The display name is cosmetic — GitHub resolves on the address, not on this — so it
# takes the configured name first, then the last author, then the login. Read rather
# than hardcoded: this repository's history already carries two spellings.
OWNER_NAME="$(git config user.name 2>/dev/null || true)"
[ -n "$OWNER_NAME" ] || OWNER_NAME="$(git log -1 --format=%an 2>/dev/null || true)"
[ -n "$OWNER_NAME" ] || OWNER_NAME="$OWNER"

# The repair, in one place, so the printed advice and --fix-cmd cannot disagree.
fix_command() {
  printf 'git rebase -x '\''git commit --amend --no-edit --author="%s <%s>"'\'' %s\n' \
    "$OWNER_NAME" "$OWNER_ADDR" "${1:-<base>}"
}

if [ "$MODE" = fixcmd ]; then
  fix_command "$(git merge-base HEAD origin/main 2>/dev/null || echo '<base>')"
  exit 0
fi

# --selftest EXECUTES the advice, which is the only way this tool's central claim can be
# checked (#1164, from #1160's rule and #1012's defect).
#
# The claim is not "the repair works". It is "the repair does not destroy the evidence" —
# `--reset-author` collapses every author date it touches onto the instant of the repair,
# and this tool exists because history is evidence. That is a statement about DATES, and no
# amount of reading the command tells you what it does to them. It has to be run.
#
# It runs in a scratch clone, on commits nothing will ever push, with three commits so the
# case that matters is reachable: the repair walks a range, and the commit that needed
# NOTHING done to it is the one `--reset-author` used to damage.
if [ "$MODE" = selftest ]; then
  work="$(mktemp -d "${TMPDIR:-/tmp}/attribution-selftest.XXXXXX")"
  trap 'rm -rf "$work"' EXIT
  root="$(git rev-parse --show-toplevel)"
  git clone -q --no-hardlinks "$root" "$work/repo" 2>/dev/null || {
    echo "ATTRIBUTION SELFTEST VERDICT FAIL cases=0 failed=0  (no clone; a suite of nothing is not a pass)"
    exit 1; }
  cd "$work/repo" || exit 1
  git config user.name selftest
  base="$(git rev-parse HEAD)"
  right="right@invalid"
  wrong="wrong@invalid"
  pass=0; fail=0
  check() {                     # check <name> <want> <got>
    if [ "$2" = "$3" ]; then
      pass=$((pass + 1)); printf 'ATTRIBUTION case=%-28s want=%-22s got=%-22s OK\n' "$1" "$2" "$3"
    else
      fail=$((fail + 1)); printf 'ATTRIBUTION case=%-28s want=%-22s got=%-22s BROKEN\n' "$1" "$2" "$3"
    fi
  }

  # Three commits with author dates a day apart, so a repair that resets them is visible as
  # a change and not as a rounding error. The middle one carries the wrong address.
  for n in 1 2 3; do
    printf 'selftest %s\n' "$n" >> SELFTEST_SCRATCH
    git add SELFTEST_SCRATCH
    addr="$right"; [ "$n" = 2 ] && addr="$wrong"
    GIT_AUTHOR_EMAIL="$addr" GIT_COMMITTER_EMAIL="$right" \
    GIT_AUTHOR_DATE="2020-0$n-0${n}T12:00:00+00:00" \
      git commit -q -m "selftest commit $n"
  done
  before_dates="$(git log --format=%ad --date=short "$base..HEAD" | tr '\n' ' ')"
  wrong_count="$(git log --format=%ae "$base..HEAD" | grep -c "^$wrong$" || true)"
  check finds-the-wrong-address 1 "$wrong_count"

  # The repair this tool advises, run verbatim on the range it names.
  fix="$(OWNER_ADDR="$right" OWNER_NAME=selftest; \
         printf "git rebase -x 'git commit --amend --no-edit --author=\"selftest <%s>\"' %s" "$right" "$base")"
  eval "$fix" >/dev/null 2>&1 || true
  after_dates="$(git log --format=%ad --date=short "$base..HEAD" | tr '\n' ' ')"
  after_wrong="$(git log --format=%ae "$base..HEAD" | grep -c "^$wrong$" || true)"
  check repair-fixes-the-address 0 "$after_wrong"
  check repair-keeps-author-dates "$before_dates" "$after_dates"

  # And the advice this tool used to print, run on the same fixture. Without this row the
  # suite proves the current repair works and says nothing about WHY it is written the way
  # it is — the reader has to take #1012 on trust. With it, the difference between the two
  # commands is a measurement: three distinct dates become one.
  git reset -q --hard "$base" && git clean -qfd
  for n in 1 2 3; do
    printf 'selftest %s\n' "$n" >> SELFTEST_SCRATCH
    git add SELFTEST_SCRATCH
    addr="$right"; [ "$n" = 2 ] && addr="$wrong"
    GIT_AUTHOR_EMAIL="$addr" GIT_COMMITTER_EMAIL="$right" \
    GIT_AUTHOR_DATE="2020-0$n-0${n}T12:00:00+00:00" \
      git commit -q -m "selftest commit $n"
  done
  git -c user.email="$right" rebase -x \
    'git -c user.email='"$right"' commit --amend --no-edit --reset-author' "$base" >/dev/null 2>&1 || true
  reset_dates="$(git log --format=%ad --date=short "$base..HEAD" | sort -u | tr '\n' ' ')"
  distinct="$(printf '%s' "$reset_dates" | wc -w | tr -d ' ')"
  check reset-author-destroys-dates 1 "$distinct"

  cd "$root" || exit 1
  printf 'ATTRIBUTION SELFTEST VERDICT %s cases=%d failed=%d\n' \
    "$([ "$fail" = 0 ] && printf PASS || printf FAIL)" "$((pass + fail))" "$fail"
  [ "$fail" = 0 ]
  exit $?
fi

# One row per commit. `.author.login` is GitHub's own resolution of the author address
# to an account, which is exactly the resolution the contribution count uses — asking
# anything else here would be measuring a different question and calling it this one.
rows() {
  case "$MODE" in
    pr)  gh api --paginate "repos/$REPO/pulls/$PR/commits" \
           --jq '.[] | [.sha, (.author.login // "-"), .commit.author.email] | @tsv' ;;
    sha) gh api "repos/$REPO/commits/$SHA" \
           --jq '[.sha, (.author.login // "-"), .commit.author.email] | @tsv' ;;
    head)
      head_sha="$(git rev-parse HEAD)"
      gh api "repos/$REPO/commits/$head_sha" \
        --jq '[.sha, (.author.login // "-"), .commit.author.email] | @tsv' ;;
  esac
}

# An unpushed commit is not a misattributed one and must not be reported as though it
# were. The resolution this tool reads lives on GitHub's side, so a commit GitHub has
# never seen has no answer — and "no answer" said plainly beats a 404 followed by a
# confident zero.
if [ "$MODE" = head ]; then
  probe_sha="$(git rev-parse HEAD)"
  gh api "repos/$REPO/commits/$probe_sha" --jq '.sha' >/dev/null 2>&1 || {
    echo "FATAL $REPO has never seen ${probe_sha:0:7} — this commit is local only." >&2
    echo "      attribution is GitHub's resolution of the author address, so it cannot be" >&2
    echo "      read before a push. Push the branch, then rerun (or use --pr N / --sha)." >&2
    exit 3
  }
fi

TOTAL=0
WRONG=0
while IFS=$'\t' read -r sha login email; do
  [ -n "${sha:-}" ] || continue
  TOTAL=$((TOTAL + 1))
  if [ "$login" = "$OWNER" ]; then
    printf 'ATTRIBUTION %s login=%s email=%s OK\n' "${sha:0:7}" "$login" "$email"
  else
    WRONG=$((WRONG + 1))
    printf 'ATTRIBUTION %s login=%s email=%s WRONG (wanted %s)\n' \
      "${sha:0:7}" "$login" "$email" "$OWNER"
  fi
done < <(rows)

# A judged line states its denominator, so a run that saw nothing cannot report that
# nothing was wrong. A zero-commit read is a FAIL and not a quiet PASS: this tool is
# asked "is the work attributed", and "there was no work" does not answer it.
if (( TOTAL == 0 )); then
  printf 'ATTRIBUTION VERDICT FAIL owner=%s commits=0 misattributed=0  (nothing was read; a silent zero is not a pass)\n' "$OWNER"
  exit 4
fi

if (( WRONG == 0 )); then
  printf 'ATTRIBUTION VERDICT PASS owner=%s commits=%d misattributed=0\n' "$OWNER" "$TOTAL"
  exit 0
fi

printf 'ATTRIBUTION VERDICT FAIL owner=%s commits=%d misattributed=%d\n' "$OWNER" "$TOTAL" "$WRONG"
printf '  fix: git config user.email %s, then rewrite the branch:\n' "$OWNER_ADDR"
printf '       '; fix_command "$(git merge-base HEAD origin/main 2>/dev/null || echo '<base>')"
printf '       (the same line, for a script: tools/attribution.sh --fix-cmd)\n'
exit 5
