---
name: release
description: Cut and publish an OW Companion release. Use whenever the user asks to publish, release, or ship a version, or says "pubblica". Encodes the exact order that stops a stale APK reaching GitHub.
---

# Releasing OW Companion

Six steps, in this order. The order is the point: publishing has twice gone out with a
stale binary because the release ran while the build had failed.

## 1. Measure, then bump

```bash
python tools/count_tokens.py
```

Then raise both `versionCode` and `versionName` in `app/build.gradle.kts`. They move
together — the update banner compares `versionName` against the newest GitHub tag, so a tag
without a bump makes every installed copy show the banner forever.

## 2. Build and check, and read the result

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew \
  assembleDebug lintVitalRelease testDebugUnitTest --console=plain -q 2>&1 \
  | grep -E "^e:|Error:" | head -5
```

**Stop here if anything printed.** Do not chain the release onto this command with `&&` in
a way that lets a failure fall through — that is exactly how v1.7.0 shipped the 1.6.0
binary. Confirm the APK timestamp is newer than the edits before going on.

Test count should be 60 or more; a sudden drop means a source set failed to compile rather
than that tests passed.

## 3. Commit

Write the message with a heredoc, never with `-m "..."`. Apostrophes in `-m` break the
shell and the commit silently does not happen:

```bash
git add -A && git commit -q -F - <<'MSG'
Subject in the imperative, under 60 characters

What changed and why it was wrong before.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

## 4. Push before releasing

```bash
git push -q origin master
```

If this is rejected, the weekly dataset workflow has committed ahead of you:
`git pull --rebase -q origin master` then push again. Releasing before the push succeeds
tags a commit nobody else has.

## 5. Release

```bash
cp app/build/outputs/apk/debug/app-debug.apk "$SCRATCH/ow-companion-vX.Y.Z.apk"
gh release create vX.Y.Z "$SCRATCH/ow-companion-vX.Y.Z.apk" --title "vX.Y.Z — short phrase" --notes "..."
```

Notes are for players, not for the changelog: say what is different and why it was worth
doing. Name whoever reported the bug.

Releases ship the **debug** APK. There is no keystore, and a release build would be
unsigned; the debug key also keeps the app upgradable in place for everyone who already
has it.

## 6. Mirror, and prove it

```bash
cp "$SCRATCH/ow-companion-vX.Y.Z.apk" "G:/googledrive/AI/ow-companion.apk"
robocopy "G:\AI\ow_companion" "G:\googledrive\AI\ow_companion" /MIR \
  /XD .git build .gradle .idea maps __pycache__ /XF *.apk /NFL /NDL /NJH /NP
```

Robocopy exits non-zero on success — anything under 8 is fine.

Then confirm the published asset and the local build are the same file:

```bash
gh release view vX.Y.Z --json assets --jq '.assets[0].size'
ls -la app/build/outputs/apk/debug/app-debug.apk
```

Only the standing rule matters more than the rest: **a full copy of the project always
lives under `G:\googledrive\AI`**, and only the latest APK is kept there.
