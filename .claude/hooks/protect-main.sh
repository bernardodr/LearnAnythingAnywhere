#!/bin/bash
# Keeps main the user's (docs/PRODUCTION-PLAN.md, D5). Claude may push other branches and open pull requests, but
# never pushes to main, merges or approves a pull request, or changes the branch rules. GitHub's "Protect main"
# ruleset is the real lock on pushes. Claude's commands run with the user's own GitHub sign-in, so this hook is what
# keeps merging the user's. It catches mistakes, not a determined workaround.
input=$(cat)
command=$(printf '%s' "$input" | jq -r '.tool_input.command // empty')
cwd=$(printf '%s' "$input" | jq -r '.cwd // empty')
[ -z "$command" ] && exit 0
[ -n "$cwd" ] || cwd=.

block() {
  echo "BLOCKED by .claude/hooks/protect-main.sh: $1. main changes only through a pull request that the user reviews and merges. Push a branch and open a pull request, or ask the user to do this." >&2
  exit 2
}

on_main() { # pushing the current branch: is it main, or does it track main?
  [ "$(git -C "$cwd" rev-parse --abbrev-ref HEAD 2>/dev/null)" = main ] && return 0
  case "$(git -C "$cwd" rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null)" in */main) return 0 ;; esac
  return 1
}

check_push() { # the words after "push"
  local remote='' refspecs=0 word
  for word in "$@"; do
    case "$word" in
      --all|--mirror) block "a push of every branch includes main" ;;
      -*) ;;
      *)
        if [ -z "$remote" ]; then remote=$word; continue; fi
        refspecs=$((refspecs + 1))
        if printf '%s' "$word" | grep -qE '^\+?([^:]*:)?(refs/heads/)?main$'; then block "a push to main"; fi
        if [ "${word#+}" = HEAD ] && on_main; then block "a push of HEAD, which is main"; fi
        ;;
    esac
  done
  if [ "$refspecs" -eq 0 ] && on_main; then block "a push of the current branch, which is (or tracks) main"; fi
}

set -f # the words below are split, never globbed
segments=$(printf '%s\n' "$command" | awk '{ gsub(/&&|\|\||;|\|/, "\n"); print }')
while IFS= read -r segment; do
  # shellcheck disable=SC2086
  set -- $segment
  while [ $# -gt 0 ]; do
    case "$1" in
      git)
        shift
        [ "$1" = -C ] && shift 2
        if [ "$1" = push ]; then shift; check_push "$@"; fi
        break ;;
      gh)
        shift
        case "$1 $2" in
          'pr merge') block "merging a pull request" ;;
          'pr review') case " $* " in *' --approve '*|*' -a '*) block "approving a pull request" ;; esac ;;
        esac
        if [ "$1" = api ]; then
          case "$*" in
            */merge|*/merge\ *|*/merges*|*mergePullRequest*|*enablePullRequestAutoMerge*|*APPROVE*)
              block "merging or approving a pull request through the API" ;;
          esac
          case "$*" in
            *rulesets*|*/protection*)
              case " $* " in
                *' -X GET '*|*' --method GET '*|*' -XGET '*) ;;
                *' -X '*|*' --method '*|*' -X'*|*' -f '*|*' -F '*|*' --field '*|*' --raw-field '*|*' --input '*)
                  block "changing the branch rules" ;;
              esac ;;
          esac
        fi
        break ;;
      *) shift ;;
    esac
  done
done <<< "$segments"
exit 0
