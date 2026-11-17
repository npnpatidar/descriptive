# Project notes — Paper Practice

> **Status: reconstructed from repository evidence.** `AGENTS.md` requires this
> file, but it did not exist in the repository. The facts below are observed
> from the code and CI workflows, not supplied by the owner. Items marked
> **CONFIRM** need the owner's decision before they are treated as rules.

## Product

- Offline Android study-library app, namespace / applicationId `com.paperpractice`.
- Papers are trees of nested categories containing questions with optional
  images; users import JSON, organise content, generate random practice tests,
  and export HTML / DOCX / PDF / JSON. No internet permission.
- Single Gradle module `:app`; Room database `papers.db`, currently **version 6**
  with exported schemas `3.json`, `4.json`, `5.json`, `6.json` in `app/schemas/`
  and migrations 2→3, 3→4, 4→5, 5→6. There is **no `MIGRATION_1_2` and no schema
  1/2** — a device at v1 cannot upgrade (pre-existing risk).
- The v5→v6 migration (global tag library + question-tag junction) and all new
  DB/repository logic are **not covered by local tests** (only JUnit 4 is on the
  classpath; no Robolectric/Room test dependency, and adding one needs owner
  approval). CI runs the same pure-JVM suite, so it is equally blind.
  **Owner decision (content tags):** accept manual verification — exercise the
  upgrade, tag rename/merge/delete, and tag import/export on a device before
  release. Do not treat the green local/CI suite as migration coverage.

## Workflow (from `AGENTS.md`, authoritative unless owner says otherwise)

- Cut every change from `main` as `feature/<topic>`; `main` receives merges only.
- Open a PR for every branch; draft until the owner says go. Owner merges and
  decides when to cut a release.
- Supervisor writes no product code: implementation goes to implementation
  subagents; a **separate** reviewer subagent reviews every code change.
  Significant work (domain rules, DB schema, backup/export format, defaults)
  gets three specialist reviewers: design/domain, implementation/integration,
  tests/docs.
- Reviews verify against official upstream docs, not local plans; cite URLs.
- Never add a test dependency without asking. No Robolectric/Room test deps
  today — all 11 test classes are pure JVM JUnit 4. CI on x86_64 is authoritative
  for any future Robolectric/SQLite tests.
- Send an ntfy notification on completion, failure, or when owner input is needed.

## Release conventions — source of truth conflict

- `AGENTS.md` says releases tag the merge commit on `main` as `0.0.N` and use
  "the project's own asset names and notes template" described in this file.
- Observed CI (`.github/workflows/release.yml`): triggers on tags `v*`; produces
  `paper-practice-<tag>.apk` plus a `.sha256`, published with `gh release create`.
  Observed CI (`android.yml`): debug artifact named `paper-practice-debug-apk`.
- **CONFIRM:** which tag scheme (`v*` vs `0.0.N`) and which asset names are
  authoritative. No release-notes template exists in the repo; the owner must
  supply it (release notes must be user-facing with a concrete example, per
  `AGENTS.md`).

## Build and test

- No environment script exists in the repo. Use `sh gradlew ...`.
- Gates: `sh gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- JDK 17; SDK platform 37 / build-tools 37.0.0; Gradle wrapper 9.9.1.
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` / `local.properties` for the SDK path.
- ARM64 hosts may need an `aapt2` override; CI uses the bundled one.

## Interchange and storage facts easy to get wrong

- The canonical import/export model is `domain/PaperContent.kt`; the strict
  parser is `domain/PaperJsonParser.kt` (reports exact JSON paths; ignores
  unknown fields; 50 MB file / 1.5 MB image / depth 64 limits).
- JSON paper schema: `paperName`, `categories[]` → `categoryName`, `questions[]`
  (and nested `categories[]`) → `questionText`, `answerText` (present but may be
  blank), optional `questionImages` / `answerImages`.
- Backup wrapper: `{format: "paper-practice-backup", version: 1, exportedAt, papers[]}`;
  it also accepts a plain paper export. Version > 1 is rejected. Keeping version
  1 preserves compatibility with older readers when adding optional fields.
- Image identity/order: images and question positions are persisted; DB writes
  are wrapped in `database.withTransaction` in `PaperRepository`, not in the DAO.
- Name identity for import conflicts is decided in Kotlin via
  `normalize() = trim().lowercase()`, not SQL `lower()` (ASCII-only).
