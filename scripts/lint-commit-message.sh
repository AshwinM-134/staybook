#!/usr/bin/env bash
# One rule list for the commit-msg hook and the pr-title job.
# Subject: type: summary
#      or: type(scope): summary
#      or: either form with ! before the colon for a breaking change
# The commit-msg hook also passes Git's own subjects as written:
# Merge ..., Revert "...", fixup! ..., and squash! ...
# --pr-title does not. Squash merge makes the title the commit on main.

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

pr_title=false
subject=''
if [[ "${1:-}" == "--pr-title" ]]; then
  pr_title=true
  subject="${2:-}"
elif [[ $# -ge 1 ]]; then
  subject="$(read_subject < "$1")" || subject=''
else
  subject="$(read_subject)" || subject=''
fi

conventional="^(${types})(\\((${scopes})\\))?!?: [^[:space:]].*$"
git_generated='^(Merge .+|Revert ".+"|(fixup|squash)! .+)$'
if [[ "${subject}" =~ ${conventional} ]]; then
  exit 0
fi
if [[ "${pr_title}" == false && "${subject}" =~ ${git_generated} ]]; then
  exit 0
fi

if [[ "${pr_title}" == true ]]; then
  echo "Pull request title is not a conventional commit." >&2
else
  echo "Commit subject is not a conventional commit." >&2
fi
echo "Use: type: summary" >&2
echo " or: type(scope): summary" >&2
echo " or: type!: summary for a breaking change" >&2
echo "types: feat, fix, chore, ci, docs, test, refactor" >&2
echo "scopes (optional): search, catalog, pricing, reviews, booking, inventory, payment, identity, ci, deps, docs" >&2
if [[ "${pr_title}" == true ]]; then
  echo "Merge ..., Revert \"...\", fixup! ..., and squash! ... titles fail. The title becomes the commit on main." >&2
else
  echo "Git subjects also pass: Merge ..., Revert \"...\", fixup! ..., squash! ..." >&2
fi
echo "got: ${subject}" >&2
exit 1
