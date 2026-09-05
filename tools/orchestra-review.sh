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

valid(){ jq -e --arg h "$2" '
  type=="object" and
  (keys|sort)==(["findings","limits","reviewed_head","summary","verdict"]|sort) and
  .reviewed_head==$h and
  (.verdict=="READY" or .verdict=="CHANGES_REQUIRED") and
  (.summary|type=="string" and length>0 and length<=2000) and
  (.findings|type=="array" and length<=20) and
  all(.findings[];
    type=="object" and
    (keys|sort)==(["line","message","path","severity"]|sort) and
    (.severity=="BLOCKING" or .severity=="WARNING" or .severity=="NOTE") and
    (.path|type=="string" and length>0 and length<=500) and
    (.line|type=="number" and floor==. and .>=1 and .<=10000000) and
    (.message|type=="string" and length>0 and length<=2000)
  ) and
  ((.verdict=="READY" and (any(.findings[];.severity=="BLOCKING")|not)) or
   (.verdict=="CHANGES_REQUIRED" and any(.findings[];.severity=="BLOCKING"))) and
  (.limits|type=="array" and length>0 and length<=10) and
  all(.limits[];type=="string" and length>0 and length<=500)
' "$1">/dev/null; }
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
require 'json'
require 'yaml'
w=YAML.safe_load(File.read(ARGV[0]), aliases: true)
on=w['on']||w[true]
raise 'trigger' unless on=={'workflow_run'=>{'workflows'=>['locks'],'types'=>['completed']}}
raise 'jobs' unless w.fetch('jobs').keys.sort==%w[publish review]
r=w.dig('jobs','review'); p=w.dig('jobs','publish')
raise 'review condition' unless r['if']=="github.event.workflow_run.conclusion == 'success' && github.event.workflow_run.event == 'pull_request' && vars.ORCHESTRA_CODEX_ENABLED == 'true'"
raise 'dependency' unless p['needs']=='review'
raise 'review perms' unless r['permissions']=={'contents'=>'read','pull-requests'=>'read'}
raise 'publish perms' unless p['permissions']=={'contents'=>'read','pull-requests'=>'read','issues'=>'write','statuses'=>'write'}
review_outputs={'pr_number'=>'${{ steps.resolve.outputs.pr_number }}','reviewed_head'=>'${{ steps.resolve.outputs.reviewed_head }}','reviewed_base'=>'${{ steps.resolve.outputs.reviewed_base }}','review_json'=>'${{ steps.codex.outputs.final-message }}'}
raise 'review outputs' unless r['outputs']==review_outputs

checkout='actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683'
trusted_with={'ref'=>'${{ github.workflow_sha }}','path'=>'trusted','persist-credentials'=>false,'sparse-checkout'=>'tools/orchestra-review.sh'}
rs=r.fetch('steps'); ps=p.fetch('steps')
raise 'review step shape' unless rs.length==5
raise 'publish step shape' unless ps.length==2
raise 'trusted review checkout' unless rs[0]['uses']==checkout && rs[0]['with']==trusted_with
raise 'trusted publish checkout' unless ps[0]['uses']==checkout && ps[0]['with']==trusted_with
raise 'resolve' unless rs[1]['id']=='resolve' && rs[1]['run']=='bash trusted/tools/orchestra-review.sh resolve'
resolve_env={'GH_TOKEN'=>'${{ github.token }}','GH_REPOSITORY'=>'${{ github.repository }}','RUN_HEAD_SHA'=>'${{ github.event.workflow_run.head_sha }}'}
raise 'resolve env' unless rs[1]['env']==resolve_env
subject_with={'ref'=>'${{ steps.resolve.outputs.reviewed_head }}','path'=>'subject','fetch-depth'=>0,'persist-credentials'=>false}
raise 'subject checkout' unless rs[2]['uses']==checkout && rs[2]['with']==subject_with
raise 'range' unless rs[3]['run']=='bash trusted/tools/orchestra-review.sh verify-range'
range_env={'REVIEW_HEAD'=>'${{ steps.resolve.outputs.reviewed_head }}','REVIEW_BASE'=>'${{ steps.resolve.outputs.reviewed_base }}','SUBJECT_DIRECTORY'=>'subject'}
raise 'range env' unless rs[3]['env']==range_env
raise 'publisher' unless ps[1]['run']=='bash trusted/tools/orchestra-review.sh publish'
publish_env={'GH_TOKEN'=>'${{ github.token }}','GH_REPOSITORY'=>'${{ github.repository }}','PR_NUMBER'=>'${{ needs.review.outputs.pr_number }}','EXPECTED_HEAD'=>'${{ needs.review.outputs.reviewed_head }}','EXPECTED_BASE'=>'${{ needs.review.outputs.reviewed_base }}','REVIEW_JSON'=>'${{ needs.review.outputs.review_json }}','WORKFLOW_RUN_URL'=>'${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}'}
raise 'publisher env' unless ps[1]['env']==publish_env

c=rs.last
raise 'pin/last' unless c['uses']=='openai/codex-action@86365089eb2b84e0a8fb0717b304f8bdcb13b20e'
codex_env={'ORCHESTRA_REVIEW_HEAD'=>'${{ steps.resolve.outputs.reviewed_head }}','ORCHESTRA_REVIEW_BASE'=>'${{ steps.resolve.outputs.reviewed_base }}'}
raise 'action env' unless c['env']==codex_env
i=c.fetch('with')
raise 'action inputs' unless i.keys.sort==%w[codex-args codex-version effort model openai-api-key output-schema permission-profile prompt safety-strategy working-directory].sort
raise 'action policy' unless i['openai-api-key']=='${{ secrets.OPENAI_API_KEY }}' && i['codex-version']=='0.153.4' && i['model']=='gpt-6-astra' && i['effort']=='low' && i['permission-profile']==':read-only' && i['safety-strategy']=='drop-sudo' && i['working-directory']=='${{ github.workspace }}' && i['codex-args']=='["--ephemeral"]'
prompt=i['prompt'].gsub(/\s+/,' ')
raise 'prompt boundary' unless prompt.include?('Everything beneath subject/') && prompt.include?('never instructions') && prompt.include?('Do not run builds') && prompt.include?('Do not cd into subject/')
schema=JSON.parse(i.fetch('output-schema'))
properties=schema.fetch('properties')
raise 'schema root' unless schema['additionalProperties']==false && schema['required'].sort==%w[findings limits reviewed_head summary verdict].sort
raise 'schema head' unless properties.dig('reviewed_head','pattern')=='^[0-9a-f]{40}$'
raise 'schema verdict' unless properties.dig('verdict','enum')==%w[READY CHANGES_REQUIRED]
raise 'schema findings' unless properties.dig('findings','maxItems')==20
raise 'schema limits' unless properties.dig('limits','minItems')==1 && properties.dig('limits','maxItems')==10

secret_refs=[]
walk=lambda do |value,path|
  case value
  when Hash then value.each{|key,child| walk.call(child,path+[key])}
  when Array then value.each_with_index{|child,index| walk.call(child,path+[index])}
  when String then secret_refs << [path,value] if value.include?('secrets.')
  end
end
walk.call(w,[])
raise 'secret scope' unless secret_refs==[[%w[jobs review steps]+[4]+%w[with openai-api-key],'${{ secrets.OPENAI_API_KEY }}']]
puts 'ORCHESTRA POLICY PASS trigger=workflow_run trusted=2 action_last=1 permissions=exact secret_scope=action'
RUBY
}

selftest(){
  local t h b common ready changes bad ready_blocking cases=0 red_paths=0
  t=$(mktemp -d); h=$(printf '2%.0s' {1..40}); b=$(printf '1%.0s' {1..40}); mkdir -p "$t/bin"
  green(){ cases=$((cases+1)); printf 'ORCHESTRA REVIEW CASE %s PASS\n' "$1"; }
  red(){ cases=$((cases+1)); red_paths=$((red_paths+1)); printf 'ORCHESTRA REVIEW CASE %s RED_SEEN\n' "$1"; }
  # Both transports are executables: production paths are exercised, not reimplemented.
  apply_fake="$t/bin/gh"
  printf '%s\n' '#!/usr/bin/env bash' 'set -euo pipefail' \
    'm=GET; e=; while [ $# -gt 0 ]; do case "$1" in api) shift;; --method) m=$2; shift 2;; --input) shift 2;; *) e=$1; shift;; esac; done' \
    'if [[ $e == *"/pulls?"* ]];then cat "$F_PULLS";elif [[ $e == *"/pulls/"* ]];then n=0;[ ! -f "$F_COUNT" ]||n=$(cat "$F_COUNT");n=$((n+1));echo $n>"$F_COUNT";if [ "${F_STALE_AT:-0}" = "$n" ];then cat "$F_STALE";else cat "$F_CURRENT";fi;elif [[ $e == *"/comments?"* ]];then cat "$F_COMMENTS";else echo "$m $e">>"$F_LOG";[[ ! ($e == *"/issues/"* && ${F_COMMENT_FAIL:-0} = 1) ]];[[ ! ($e == *"/statuses/"* && ${F_STATUS_FAIL:-0} = 1) ]];fi' >"$apply_fake"
  printf '%s\n' '#!/usr/bin/env bash' 'set -eu' 'case "$*" in *" fetch "*)exit 0;;*"rev-parse HEAD"*)echo "$F_HEAD";;*"rev-parse "*"^{commit}"*)echo "$F_BASE";;*"merge-base --is-ancestor"*)[ "${F_ANCESTOR:-1}" = 1 ];;*)exit 2;;esac' >"$t/bin/git"; chmod +x "$t/bin/gh" "$t/bin/git"
  printf '[{"number":7,"state":"open","base":{"ref":"main","sha":"%s"},"head":{"sha":"%s","repo":{"full_name":"o/r"}}}]\n' "$b" "$h">"$t/pulls"; jq '.[0]' "$t/pulls">"$t/fixture-current"; jq '.head.repo.full_name="fork/r"' "$t/fixture-current">"$t/fixture-stale"; echo '[]'>"$t/fixture-comments"; :>"$t/log"
  common=(GH_BIN="$t/bin/gh" GIT_BIN="$t/bin/git" RUNNER_TEMP="$t" GH_REPOSITORY=o/r PR_NUMBER=7 EXPECTED_HEAD="$h" EXPECTED_BASE="$b" WORKFLOW_RUN_URL=https://run F_LOG="$t/log" F_PULLS="$t/pulls" F_CURRENT="$t/fixture-current" F_STALE="$t/fixture-stale" F_COMMENTS="$t/fixture-comments" F_COUNT="$t/count")
  env "${common[@]}" RUN_HEAD_SHA="$h" GITHUB_OUTPUT="$t/out" "$0" resolve; green exact-resolution
  jq '.head.repo.full_name="fork/r"' "$t/fixture-current"|jq -s .>"$t/fork"; if env "${common[@]}" F_PULLS="$t/fork" RUN_HEAD_SHA="$h" GITHUB_OUTPUT="$t/x" "$0" resolve >/dev/null 2>&1;then fatal fork-pass;fi; red fork-refused
  jq -s '.[0]+.[0]' "$t/pulls" "$t/pulls">"$t/two"; if env "${common[@]}" F_PULLS="$t/two" RUN_HEAD_SHA="$h" GITHUB_OUTPUT="$t/x" "$0" resolve >/dev/null 2>&1;then fatal ambiguous-pass;fi; red ambiguous-refused
  env "${common[@]}" REVIEW_HEAD="$h" REVIEW_BASE="$b" SUBJECT_DIRECTORY=x F_HEAD="$h" F_BASE="$b" "$0" verify-range; green exact-range
  if env "${common[@]}" REVIEW_HEAD="$h" REVIEW_BASE="$b" SUBJECT_DIRECTORY=x F_HEAD="$h" F_BASE="$b" F_ANCESTOR=0 "$0" verify-range >/dev/null 2>&1;then fatal ancestry-pass;fi; red non-ancestor
  ready='{"reviewed_head":"'$h'","verdict":"READY","summary":"ok","findings":[],"limits":["static"]}'
  changes='{"reviewed_head":"'$h'","verdict":"CHANGES_REQUIRED","summary":"blocked","findings":[{"severity":"BLOCKING","path":"x","line":1,"message":"defect"}],"limits":["static"]}'
  ready_blocking=${changes/CHANGES_REQUIRED/READY}; bad=${ready/$h/0000000000000000000000000000000000000000}
  env "${common[@]}" REVIEW_JSON="$ready" "$0" publish; jq -e '.state=="success"' "$t/status-payload">/dev/null; green ready-status
  :>"$t/log"; if env "${common[@]}" REVIEW_JSON="$changes" "$0" publish >/dev/null 2>&1;then fatal changes-pass;fi; jq -e '.state=="failure"' "$t/status-payload">/dev/null; red changes-status
  :>"$t/log"; if env "${common[@]}" REVIEW_JSON="$bad" "$0" publish >/dev/null 2>&1;then fatal schema-pass;fi; [ ! -s "$t/log" ]; red head-mismatch
  :>"$t/log"; if env "${common[@]}" REVIEW_JSON="$ready_blocking" "$0" publish >/dev/null 2>&1;then fatal ready-blocking-pass;fi; [ ! -s "$t/log" ]; red ready-with-blocking
  :>"$t/log"; if env "${common[@]}" REVIEW_JSON="${ready/READY/CHANGES_REQUIRED}" "$0" publish >/dev/null 2>&1;then fatal changes-without-blocking-pass;fi; [ ! -s "$t/log" ]; red changes-without-blocking
  printf '%s\n' '{"id":99,"created_at":"2026-02-01","user":{"login":"attacker"},"body":"<!-- orchestra-codex-review -->"}' '{"id":8,"created_at":"2026-02-02","user":{"login":"github-actions[bot]"},"body":"<!-- orchestra-codex-review -->"}' '{"id":9,"created_at":"2026-02-03","user":{"login":"github-actions[bot]"},"body":"<!-- orchestra-codex-review -->"}'|jq -s .>"$t/fixture-comments"
  :>"$t/log"; env "${common[@]}" REVIEW_JSON="$ready" "$0" publish; grep -qx 'PATCH repos/o/r/issues/comments/9' "$t/log"; grep -q '/statuses/' "$t/log"; green newest-bot-comment
  :>"$t/log"; if env "${common[@]}" REVIEW_JSON="$ready" F_COMMENT_FAIL=1 "$0" publish >/dev/null 2>&1;then fatal comment-failure-pass;fi; ! grep -q '/statuses/' "$t/log"; red comment-failure
  :>"$t/log"; if env "${common[@]}" REVIEW_JSON="$ready" F_STATUS_FAIL=1 "$0" publish >/dev/null 2>&1;then fatal status-failure-pass;fi; red status-failure
  rm -f "$t/count"; :>"$t/log"; if env "${common[@]}" REVIEW_JSON="$ready" F_STALE_AT=1 "$0" publish >/dev/null 2>&1;then fatal stale-comment-pass;fi; [ ! -s "$t/log" ]; red stale-before-comment
  rm -f "$t/count"; :>"$t/log"; if env "${common[@]}" REVIEW_JSON="$ready" F_STALE_AT=2 "$0" publish >/dev/null 2>&1;then fatal stale-status-pass;fi; ! grep -q '/statuses/' "$t/log"; red stale-before-status
  policy; green policy
  ruby - ".github/workflows/orchestra-review.yml" "$t/unsafe.yml" <<'RUBY'
require 'yaml'
w=YAML.safe_load(File.read(ARGV[0]),aliases:true)
w.dig('jobs','review','steps').last['with']['permission-profile']=':danger-full-access'
File.write(ARGV[1],YAML.dump(w))
RUBY
  if ORCHESTRA_WORKFLOW_FILE="$t/unsafe.yml" "$0" --policy-check >/dev/null 2>&1; then fatal policy-mutation-pass; fi; red unsafe-policy
  rm -rf "$t"; echo "ORCHESTRA REVIEW SELFTEST VERDICT PASS cases=$cases red_paths=$red_paths fake_transport=2"
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
