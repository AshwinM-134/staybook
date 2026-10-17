#!/usr/bin/env bash
# One rule list for the commit-msg hook and the pr-title job.
# Subject: type: summary
#      or: type(scope): summary
#      or: either form with ! before the colon for a breaking change
# Git's own subjects pass as written: Merge ..., Revert "...",
# fixup! ..., and squash! ...

set -euo pipefail

types='feat|fix|chore|ci|docs|test|refactor'
scopes='search|catalog|pricing|reviews|booking|inventory|payment|identity|ci|deps|docs'

read_subject() {
  local line
  while IFS= read -r line || [[ -n "${line}" ]]; do
    if [[ "${line}" =~ ^# ]]; then
      continue
    fi
    if [[ -z "${line}" ]]; then
      continue
    fi
    printf '%s\n' "${line}"
    return 0
  done
  return 1
}

subject=''
if [[ "${1:-}" == "--subject" ]]; then
  subject="${2:-}"
elif [[ $# -ge 1 ]]; then
  subject="$(read_subject < "$1")" || subject=''
else
  subject="$(read_subject)" || subject=''
fi

conventional="^(${types})(\\((${scopes})\\))?!?: [^[:space:]].*$"
git_generated='^(Merge .+|Revert ".+"|(fixup|squash)! .+)$'
if [[ ! "${subject}" =~ ${conventional} && ! "${subject}" =~ ${git_generated} ]]; then
  echo "Commit subject is not a conventional commit." >&2
  echo "Use: type: summary" >&2
  echo " or: type(scope): summary" >&2
  echo " or: type!: summary for a breaking change" >&2
  echo "types: feat, fix, chore, ci, docs, test, refactor" >&2
  echo "scopes (optional): search, catalog, pricing, reviews, booking, inventory, payment, identity, ci, deps, docs" >&2
  echo "Git subjects also pass: Merge ..., Revert \"...\", fixup! ..., squash! ..." >&2
  echo "got: ${subject}" >&2
  exit 1
fi
