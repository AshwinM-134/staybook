# Contributing

## How we work

- Ash writes all of the application code, including the schema in `V1__init.sql`.
- Work goes in through pull requests, reviewed before merge.

## Commit messages

Commits use Conventional Commits. The subject is `type: summary` or
`type(scope): summary`. A `!` before the colon marks a breaking
change, as in `feat!: summary`.

The commit-msg hook also passes the subjects Git writes itself. Any
line that starts with `Merge` and a space passes, including
`Merge branch`, `Merge remote-tracking branch 'origin/main'`, and
`Merge pull request #1`. `Revert "…"`, `fixup! …`, and `squash! …`
pass as well. A pull request title does not get these exceptions.

Types, and only these: `feat`, `fix`, `chore`, `ci`, `docs`, `test`,
`refactor`.

Scope is optional. When set, it is one of: `search`, `catalog`,
`pricing`, `reviews`, `booking`, `inventory`, `payment`, `identity`,
`ci`, `deps`, `docs`.

## Install the commit-msg hook

Install the commit-msg hook with one command. It checks the subject
before the commit exists:

```bash
git config core.hooksPath .githooks
```

The hook and the `pr-title` job use one rule list,
`scripts/lint-commit-message.sh`. To check a pull request title before
you open the pull request, run:

```bash
./scripts/lint-commit-message.sh --pr-title "docs: fix a typo"
```

## Pull request titles

Squash merge only. The pull request title becomes the commit on
`main`, so the title must be `type: summary` or `type(scope): summary`.
The `pr-title` check fails a title that starts with `Merge`,
`Revert "`, `fixup!`, or `squash!`. To undo a change, title the pull
request with a listed type, as in `fix: revert the booking timeout`.

## Review and merge

Every change reaches `main` through a pull request. The
`protect-main` ruleset on `main` enforces the rules in these steps:

1. Open a pull request against `main`. The ruleset blocks direct
   pushes and force-pushes to `main`.
2. Wait for `build`, `java-quality`, `megalinter`, `pr-title`, and
   `security` to pass. `exit-gate` is not required.
3. Ask for a review. The ruleset blocks the merge until every review
   thread is resolved. A new push dismisses earlier approvals.
4. If `main` has moved, update the branch. The ruleset requires the
   branch to be up to date with `main` before the merge.
5. Squash merge. The ruleset allows no other merge method.

## CI

`.github/workflows/ci.yml` runs on every pull request and on every push
to `main`. `.github/workflows/pr-title.yml` runs when a pull request is
opened, edited, synchronized, or reopened. The title check is not part
of `ci.yml`.

- `build` — Java 21, one `./mvnw verify`: tests against Testcontainers
  Postgres, then PMD, Checkstyle (Google style), SpotBugs, Spotless
  (`google-java-format`), and JaCoCo. Excludes `level0`, `level2`, and
  `level3`. A test failure or a lint violation fails the job. Format
  with `./mvnw spotless:apply`. On a pull request it posts the JaCoCo
  report as a comment. There is no coverage threshold, and a failed
  comment does not fail the job.
- `java-quality` — runs after `build` and passes only if that verify
  passed. It does not run Maven again. The `main` ruleset requires
  this check name.
- `megalinter` — YAML, Markdown, Dockerfile, Compose (as YAML), SQL,
  shell, and GitHub Actions. Gitleaks 8.30.1 runs in this job after
  `sha256sum` checks the downloaded tarball. Java linters are off here
  because Maven already runs them.
- `security` — CodeQL for Java. The job sets up Temurin 21, compiles
  with Maven, and uploads the results.
- `pr-title` — the pull request title follows the commit rules in
  [Commit messages](#commit-messages), without the exceptions for
  `Merge`, `Revert`, `fixup!`, and `squash!` subjects. Squash merge
  makes the title the commit on `main`, so the job does not lint the
  branch's commits. Editing the title runs this check again.
- `exit-gate` — enabled levels only. Today that is `level0`. The job
  reports pass or fail and does not block the workflow
  (`continue-on-error`). It stays red until the contract is
  implemented.

Dependabot opens weekly updates for Maven, the images in
`docker-compose.yml`, and GitHub Actions. Their titles start with
`chore(deps):`, so they pass `pr-title`.

The exit-gate command in the workflow is:

```bash
./mvnw verify -Dgroups=level0 -DexcludedGroups=
```

To turn a level on, add its tag to `-Dgroups`. For example,
`-Dgroups=level0,level2` also runs level 2.

### Exit-gate tags

`level0` runs in CI and is expected to fail until the HTTP contract in
the [README](README.md#http-contract) and `V1__init.sql` are in place.
`level2` and `level3` are written and switched off. See CI for how to
turn a level on.
