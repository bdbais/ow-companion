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
  assembleRelease testDebugUnitTest --console=plain -q 2>&1 \
  | grep -E "^e:|Error:" | head -5
```

`assembleRelease` runs `lintVitalRelease` itself, so it does not need naming separately.
It also takes about two and a half minutes rather than one: R8 and resource shrinking are
the reason the APK is half the size, and they are not free.

**Stop here if anything printed.** Do not chain the release onto this command with `&&` in
a way that lets a failure fall through — that is exactly how v1.7.0 shipped the 1.6.0
binary. Confirm the APK timestamp is newer than the edits before going on.

Test count should be 60 or more; a sudden drop means a source set failed to compile rather
than that tests passed.

Two ways to break this build that look like something else:

- **Never pass `--rerun-tasks`.** It leaves resource linking broken, and the failure that
  follows blames a missing AppCompat theme, which sends you looking at `themes.xml` where
  nothing is wrong. `./gradlew :app:clean` does not clear it; a plain build does.
- **A `composeCompiler { }` block silently empties the dependency graph.** Adding one to get
  recomposition metrics resolved `debugRuntimeClasspath` down to kotlin-stdlib alone, so
  every AndroidX resource vanished with the same misleading AppCompat error. Confirm with
  `./gradlew :app:dependencies --configuration debugRuntimeClasspath` before believing any
  theory about resources.

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
cp app/build/outputs/apk/release/app-release.apk "$SCRATCH/ow-companion-vX.Y.Z.apk"
gh release create vX.Y.Z "$SCRATCH/ow-companion-vX.Y.Z.apk" --title "vX.Y.Z — short phrase" --notes "..."
```

Notes are for players, not for the changelog: say what is different and why it was worth
doing. Name whoever reported the bug.

Some things in this app are meant to be come across rather than read about. They never
appear in release notes, in a commit subject, or in any file under `docs`. A release that
contains one is described by everything else it contains.

Check the APK is the signed release and not the debug one before it goes anywhere:

```bash
aapt dump badging "$SCRATCH/ow-companion-vX.Y.Z.apk" | grep -c application-debuggable
```

Zero, and roughly 16 MB rather than 33. Everything up to v1.10.3 shipped the **debug** APK,
because there was no keystore and an unsigned release APK will not install — the two builds
had the same SHA256, and nobody noticed for eleven releases. Twice the download, and
`debuggable` lets anyone with adb read the app's data.

Never fall back to the debug APK to get a release out. If signing fails, the fix is the
keystore, not the other binary.

**The signing path itself is proven.** It had never once run in this project — that is why
eleven releases shipped debug — so on 2026-08-14 it was exercised end to end with a
throwaway key, which was then deleted along with the APK it signed. With a real password in
`keystore.properties` the build produces `app-release.apk` (not `-unsigned`) at **17.1 MB**,
`application-debuggable` count **0**, `apksigner verify` reporting one signer, and it
installs and runs on a clean device with R8 and resource shrinking active. So a failure here
is a wrong password or a wrong `storeFile`, not a broken configuration.

`storeFile` is resolved by `rootProject.file()`: keep it relative to the repository root
(`ow-companion.jks`) or absolute with forward slashes. A `../` path silently resolves to a
drive root and fails with "Keystore file 'G:\' not found", which reads like a missing file
rather than a bad path.

### The one release that changes the signing key

Android will not install an APK over one signed with a different key. It refuses with
"App not installed" and no reason, which looks exactly like a corrupt download.

So the switch takes two releases, and only the second is signed with the new key:

1. **v1.10.4 — still the debug APK.** It installs over what everybody has, and it carries
   the code that reads the marker below. Its own notes say nothing special.
2. **The next one — the signed release APK**, with this line anywhere in its notes:

   ```
   <!-- reinstall -->
   ```

   An HTML comment, so GitHub renders the notes without it. `ReleaseChecker` looks for it
   and the banner turns into "this one has to be installed by hand" plus a dialog that
   explains the uninstall and warns that saved boards are lost. Say the same thing in plain
   words at the top of the notes as well, for anyone who never installed v1.10.4.

Marker on any release after that and everyone gets warned for nothing, so it belongs only
on a release whose key actually changed.

**Where this stands: step 2 has not happened yet.** v1.10.4 carried the reader as planned,
but v1.11.0 shipped debug too — a deliberate call on 2026-08-14, because the keystore
password was not available and the choice was between a debuggable APK and no release.
Federico picked the release, knowing the cost: 35 MB instead of 17, and `debuggable` means
anyone with adb can read the app's data.

So the transition is still owed, and the marker still belongs on **the first release built
with a real password in `keystore.properties`** — whichever number that turns out to be.
Until then every release keeps installing over the last one, which is the one upside of
having stayed on the debug key.

## 6. Mirror, and prove it

```bash
cp "$SCRATCH/ow-companion-vX.Y.Z.apk" "G:/googledrive/AI/ow-companion.apk"
robocopy "G:\AI\ow_companion" "G:\googledrive\AI\ow_companion" /MIR \
  /XD .git build .gradle .idea maps __pycache__ \
  /XF *.apk keystore.properties /NFL /NDL /NJH /NP
```

The key itself (`*.jks`) **is** mirrored, on purpose: it is the one file that cannot be
rebuilt, and losing it ends updates for the app forever. `keystore.properties` is excluded
just as deliberately — a key and its password sitting together in a synced folder means one
leaked folder is a complete compromise, and the password is the half that lives in a
password manager instead.

Robocopy exits non-zero on success — anything under 8 is fine.

Then confirm the published asset and the local build are the same file:

```bash
gh release view vX.Y.Z --json assets --jq '.assets[0].size'
ls -la app/build/outputs/apk/release/app-release.apk
```

Only the standing rule matters more than the rest: **a full copy of the project always
lives under `G:\googledrive\AI`**, and only the latest APK is kept there.
