#!/usr/bin/env bash
# Usage: tools/orchestra-review.sh resolve|verify-range|publish|--policy-check|--selftest|--help
# Exit 0 held; 1 evidence failed; 2 invocation refused.
set -euo pipefail
GH_BIN="${GH_BIN:-gh}"; GIT_BIN="${GIT_BIN:-git}"; MARKER='<!-- orchestra-codex-review -->'
fatal(){ echo "FATAL $*" >&2; return 1; }
sha(){ printf '%s\n' "$1"|grep -qxE '[0-9a-f]{40}'; }
api(){ "$GH_BIN" api "$@"; }

resolve(){
  : "${GH_REPOSITORY:?}" "${RUN_HEAD_SHA:?}" "${GITHUB_OUTPUT:?}"; sha "$RUN_HEAD_SHA"||fatal bad-head
  local f="${RUNNER_TEMP:-/tmp}/open.jsonl" r n p=1; : >"$f"
  while :; do r="${RUNNER_TEMP:-/tmp}/page$p"; api "repos/$GH_REPOSITORY/pulls?state=open&base=main&per_page=100&page=$p">"$r"; jq -e 'type=="array"' "$r">/dev/null; jq -c '.[]' "$r">>"$f"; n=$(jq length "$r"); [ "$n" -eq 100 ]||break; p=$((p+1)); done
  r="${RUNNER_TEMP:-/tmp}/matches"; jq -s --arg h "$RUN_HEAD_SHA" --arg r "$GH_REPOSITORY" '[.[]|select(.state=="open" and .base.ref=="main" and .head.sha==$h and .head.repo.full_name==$r)]' "$f">"$r"
  [ "$(jq length "$r")" -eq 1 ]||fatal not-exactly-one
  local num head base; num=$(jq -r '.[0].number' "$r"); head=$(jq -r '.[0].head.sha' "$r"); base=$(jq -r '.[0].base.sha' "$r")
  printf '%s\n' "$num"|grep -qxE '[1-9][0-9]*'; [ "$head" = "$RUN_HEAD_SHA" ]; sha "$base"
  printf 'pr_number=%s\nreviewed_head=%s\nreviewed_base=%s\n' "$num" "$head" "$base">>"$GITHUB_OUTPUT"
}

verify_range(){
  : "${REVIEW_HEAD:?}" "${REVIEW_BASE:?}" "${SUBJECT_DIRECTORY:?}"; sha "$REVIEW_HEAD"; sha "$REVIEW_BASE"
  "$GIT_BIN" -C "$SUBJECT_DIRECTORY" fetch --no-tags origin "$REVIEW_BASE"
  [ "$("$GIT_BIN" -C "$SUBJECT_DIRECTORY" rev-parse HEAD)" = "$REVIEW_HEAD" ]
  [ "$("$GIT_BIN" -C "$SUBJECT_DIRECTORY" rev-parse "$REVIEW_BASE^{commit}")" = "$REVIEW_BASE" ]
  "$GIT_BIN" -C "$SUBJECT_DIRECTORY" merge-base --is-ancestor "$REVIEW_BASE" "$REVIEW_HEAD"||fatal non-ancestor
}

valid(){ jq -e --arg h "$2" 'type=="object" and (keys|sort)==(["findings","limits","reviewed_head","summary","verdict"]|sort) and .reviewed_head==$h and (.verdict=="READY" or .verdict=="CHANGES_REQUIRED") and (.summary|type=="string" and length>0 and length<=2000) and (.findings|type=="array" and length<=20) and all(.findings[];type=="object" and (keys|sort)==(["line","message","path","severity"]|sort) and (.severity=="BLOCKING" or .severity=="WARNING" or .severity=="NOTE") and (.path|type=="string" and length>0 and length<=500) and (.line|type=="number" and floor==. and .>=1 and .<=10000000) and (.message|type=="string" and length>0 and length<=2000)) and (.limits|type=="array" and length>0 and length<=10) and all(.limits[];type=="string" and length>0 and length<=500)' "$1">/dev/null; }
current(){ local f="${RUNNER_TEMP:-/tmp}/current"; api "repos/$GH_REPOSITORY/pulls/$PR_NUMBER">"$f"; jq -e --arg h "$EXPECTED_HEAD" --arg b "$EXPECTED_BASE" --arg r "$GH_REPOSITORY" '.state=="open" and .base.ref=="main" and .base.sha==$b and .head.sha==$h and .head.repo.full_name==$r' "$f">/dev/null||fatal stale; }

publish(){
  : "${GH_REPOSITORY:?}" "${PR_NUMBER:?}" "${EXPECTED_HEAD:?}" "${EXPECTED_BASE:?}" "${REVIEW_JSON:?}" "${WORKFLOW_RUN_URL:?}"; sha "$EXPECTED_HEAD"; sha "$EXPECTED_BASE"
  local t review comments r n p=1 id payload verdict state desc; t="${RUNNER_TEMP:-/tmp}"; review="$t/review"; comments="$t/comments"; printf %s "$REVIEW_JSON">"$review"; valid "$review" "$EXPECTED_HEAD"||fatal malformed
  verdict=$(jq -r .verdict "$review"); : >"$comments"
  while :; do r="$t/comments$p"; api "repos/$GH_REPOSITORY/issues/$PR_NUMBER/comments?per_page=100&page=$p">"$r"; jq -e 'type=="array"' "$r">/dev/null; jq -c '.[]' "$r">>"$comments"; n=$(jq length "$r"); [ "$n" -eq 100 ]||break; p=$((p+1)); done
  id=$(jq -sr --arg m "$MARKER" '[.[]|select(.user.login=="github-actions[bot]" and (.body|contains($m)))]|sort_by([.created_at,.id])|last|.id//empty' "$comments")
  payload="$t/comment-payload"; jq -n --arg m "$MARKER" --argjson x "$REVIEW_JSON" '{body:($m+"\n## Codex orchestra review — `"+$x.verdict+"`\n\nReviewed head: `"+$x.reviewed_head+"`\n\n"+$x.summary+"\n\n### Findings\n"+([$x.findings[]|"- **"+.severity+"** `"+.path+":"+(.line|tostring)+"` — "+.message]|if length==0 then "- None reported." else join("\n") end)+"\n\n### Limits\n"+([$x.limits[]|"- "+.]|join("\n"))+"\n\n_Automated read-only sensor; never approval or merge authority._")}' >"$payload"
  current
  if [ -n "$id" ]; then printf '%s\n' "$id"|grep -qxE '[1-9][0-9]*'; api --method PATCH "repos/$GH_REPOSITORY/issues/comments/$id" --input "$payload">/dev/null; else api --method POST "repos/$GH_REPOSITORY/issues/$PR_NUMBER/comments" --input "$payload">/dev/null; fi
  if [ "$verdict" = READY ]; then state=success; desc='Codex sensor found no blocking defect'; else state=failure; desc='Codex sensor reported changes required'; fi
  payload="$t/status-payload"; jq -n --arg state "$state" --arg target_url "$WORKFLOW_RUN_URL" --arg description "$desc" '{state:$state,target_url:$target_url,description:$description,context:"orchestra/review"}'>"$payload"
  current; api --method POST "repos/$GH_REPOSITORY/statuses/$EXPECTED_HEAD" --input "$payload">/dev/null
  [ "$verdict" = READY ]||{ echo 'CHANGES_REQUIRED: failure status published' >&2; return 1; }
}

policy(){ ruby - "${ORCHESTRA_WORKFLOW_FILE:-.github/workflows/orchestra-review.yml}" <<'RUBY'
require 'yaml'; w=YAML.safe_load(File.read(ARGV[0]),aliases:true); on=w['on']||w[true]
raise 'trigger' unless on=={'workflow_run'=>{'workflows'=>['locks'],'types'=>['completed']}}
r=w.dig('jobs','review'); p=w.dig('jobs','publish'); raise 'dependency' unless p['needs']=='review'
raise 'review perms' unless r['permissions']=={'contents'=>'read','pull-requests'=>'read'}
raise 'publish perms' unless p['permissions']=={'contents'=>'read','pull-requests'=>'read','issues'=>'write','statuses'=>'write'}
[r,p].each{|j| c=j['steps'][0]; raise 'trusted' unless c.dig('with','ref')=='${{ github.workflow_sha }}'&&c.dig('with','path')=='trusted'&&c.dig('with','persist-credentials')==false}
s=r['steps']; q=s.find{|x|x['name']=='check out the exact reviewed head without credentials'}; raise 'subject' unless q.dig('with','path')=='subject'&&q.dig('with','persist-credentials')==false
c=s.last; raise 'pin/last' unless c['uses']=='openai/codex-action@86365089eb2b84e0a8fb0717b304f8bdcb13b20e'; i=c['with']; raise 'policy' unless i['permission-profile']==':read-only'&&i['safety-strategy']=='drop-sudo'&&i['working-directory']=='${{ github.workspace }}'&&i['codex-args']=='["--ephemeral"]'&&!i.key?('allow-users')
puts 'ORCHESTRA POLICY PASS trigger=workflow_run trusted=2 action_last=1 permissions=exact'
RUBY
}

selftest(){
  local t h b common ready changes bad; t=$(mktemp -d); h=$(printf '2%.0s' {1..40}); b=$(printf '1%.0s' {1..40}); mkdir -p "$t/bin"
  # Both transports are executables: production paths are exercised, not reimplemented.
  apply_fake="$t/bin/gh"
  printf '%s\n' '#!/usr/bin/env bash' 'set -euo pipefail' \
    'm=GET; e=; while [ $# -gt 0 ]; do case "$1" in api) shift;; --method) m=$2; shift 2;; --input) shift 2;; *) e=$1; shift;; esac; done' \
    'if [[ $e == *"/pulls?"* ]];then cat "$F_PULLS";elif [[ $e == *"/pulls/"* ]];then n=0;[ ! -f "$F_COUNT" ]||n=$(cat "$F_COUNT");n=$((n+1));echo $n>"$F_COUNT";if [ "${F_STALE_AT:-0}" = "$n" ];then cat "$F_STALE";else cat "$F_CURRENT";fi;elif [[ $e == *"/comments?"* ]];then cat "$F_COMMENTS";else echo "$m $e">>"$F_LOG";[[ ! ($e == *"/issues/"* && ${F_COMMENT_FAIL:-0} = 1) ]];[[ ! ($e == *"/statuses/"* && ${F_STATUS_FAIL:-0} = 1) ]];fi' >"$apply_fake"
  printf '%s\n' '#!/usr/bin/env bash' 'set -eu' 'case "$*" in *" fetch "*)exit 0;;*"rev-parse HEAD"*)echo "$F_HEAD";;*"rev-parse "*"^{commit}"*)echo "$F_BASE";;*"merge-base --is-ancestor"*)[ "${F_ANCESTOR:-1}" = 1 ];;*)exit 2;;esac' >"$t/bin/git"; chmod +x "$t/bin/gh" "$t/bin/git"
  printf '[{"number":7,"state":"open","base":{"ref":"main","sha":"%s"},"head":{"sha":"%s","repo":{"full_name":"o/r"}}}]\n' "$b" "$h">"$t/pulls"; jq '.[0]' "$t/pulls">"$t/fixture-current"; jq '.head.repo.full_name="fork/r"' "$t/fixture-current">"$t/fixture-stale"; echo '[]'>"$t/fixture-comments"; :>"$t/log"
  common=(GH_BIN="$t/bin/gh" GIT_BIN="$t/bin/git" RUNNER_TEMP="$t" GH_REPOSITORY=o/r PR_NUMBER=7 EXPECTED_HEAD="$h" EXPECTED_BASE="$b" WORKFLOW_RUN_URL=https://run F_LOG="$t/log" F_PULLS="$t/pulls" F_CURRENT="$t/fixture-current" F_STALE="$t/fixture-stale" F_COMMENTS="$t/fixture-comments" F_COUNT="$t/count")
  env "${common[@]}" RUN_HEAD_SHA="$h" GITHUB_OUTPUT="$t/out" "$0" resolve
  jq '.head.repo.full_name="fork/r"' "$t/fixture-current"|jq -s .>"$t/fork"; if env "${common[@]}" F_PULLS="$t/fork" RUN_HEAD_SHA="$h" GITHUB_OUTPUT="$t/x" "$0" resolve >/dev/null 2>&1;then fatal fork-pass;fi
  jq -s '.[0]+.[0]' "$t/pulls" "$t/pulls">"$t/two"; if env "${common[@]}" F_PULLS="$t/two" RUN_HEAD_SHA="$h" GITHUB_OUTPUT="$t/x" "$0" resolve >/dev/null 2>&1;then fatal ambiguous-pass;fi
  env "${common[@]}" REVIEW_HEAD="$h" REVIEW_BASE="$b" SUBJECT_DIRECTORY=x F_HEAD="$h" F_BASE="$b" "$0" verify-range
  if env "${common[@]}" REVIEW_HEAD="$h" REVIEW_BASE="$b" SUBJECT_DIRECTORY=x F_HEAD="$h" F_BASE="$b" F_ANCESTOR=0 "$0" verify-range >/dev/null 2>&1;then fatal ancestry-pass;fi
  ready='{"reviewed_head":"'$h'","verdict":"READY","summary":"ok","findings":[],"limits":["static"]}'; changes=${ready/READY/CHANGES_REQUIRED}; bad=${ready/$h/0000000000000000000000000000000000000000}
  env "${common[@]}" REVIEW_JSON="$ready" "$0" publish; jq -e '.state=="success"' "$t/status-payload">/dev/null
  :>"$t/log"; if env "${common[@]}" REVIEW_JSON="$changes" "$0" publish >/dev/null 2>&1;then fatal changes-pass;fi; jq -e '.state=="failure"' "$t/status-payload">/dev/null
  :>"$t/log"; if env "${common[@]}" REVIEW_JSON="$bad" "$0" publish >/dev/null 2>&1;then fatal schema-pass;fi; [ ! -s "$t/log" ]
  printf '%s\n' '{"id":99,"created_at":"2026-02-01","user":{"login":"attacker"},"body":"<!-- orchestra-codex-review -->"}' '{"id":8,"created_at":"2026-02-02","user":{"login":"github-actions[bot]"},"body":"<!-- orchestra-codex-review -->"}' '{"id":9,"created_at":"2026-02-03","user":{"login":"github-actions[bot]"},"body":"<!-- orchestra-codex-review -->"}'|jq -s .>"$t/fixture-comments"
  :>"$t/log"; env "${common[@]}" REVIEW_JSON="$ready" "$0" publish; grep -qx 'PATCH repos/o/r/issues/comments/9' "$t/log"; grep -q '/statuses/' "$t/log"
  :>"$t/log"; if env "${common[@]}" REVIEW_JSON="$ready" F_COMMENT_FAIL=1 "$0" publish >/dev/null 2>&1;then fatal comment-failure-pass;fi; ! grep -q '/statuses/' "$t/log"
  :>"$t/log"; if env "${common[@]}" REVIEW_JSON="$ready" F_STATUS_FAIL=1 "$0" publish >/dev/null 2>&1;then fatal status-failure-pass;fi
  rm -f "$t/count"; :>"$t/log"; if env "${common[@]}" REVIEW_JSON="$ready" F_STALE_AT=1 "$0" publish >/dev/null 2>&1;then fatal stale-comment-pass;fi; [ ! -s "$t/log" ]
  rm -f "$t/count"; :>"$t/log"; if env "${common[@]}" REVIEW_JSON="$ready" F_STALE_AT=2 "$0" publish >/dev/null 2>&1;then fatal stale-status-pass;fi; ! grep -q '/statuses/' "$t/log"
  policy; rm -rf "$t"; echo 'ORCHESTRA REVIEW SELFTEST VERDICT PASS cases=12 red_paths=8 fake_transport=2'
}

case "${1:-}" in
  resolve) resolve;;
  verify-range) verify_range;;
  publish) publish;;
  --policy-check) policy;;
  --selftest) selftest;;
  --help) sed -n '2,3p' "$0";;
  -h) sed -n '2,3p' "$0";;
  *) echo 'Usage: orchestra-review.sh resolve|verify-range|publish|--policy-check|--selftest|--help' >&2; exit 2;;
esac
