---
name: strings
description: Add or change Android string resources in OW Companion. Use whenever a new UI string is needed, a label changes, or a translation is added. Encodes the apostrophe rule that has broken this build six times.
---

# Adding a string

## The rule that keeps breaking

An unescaped apostrophe in a string resource **stops the resources compiling**. Not a
warning — the build fails with `Invalid unicode escape sequence`.

```xml
<string name="x">Blizzard's notes</string>   <!-- build fails -->
<string name="x">Blizzard\'s notes</string>  <!-- correct -->
```

It has broken this build six times: five in Italian, once in English on the one string that
happened to contain one. It applies to **every** locale, not just the ones full of them.

Straight quotes `'` need escaping. Typographic ones `’` do not, but the project uses
straight quotes, so escape.

## Do not write these files through the shell

Heredocs and inline `python -c` mangle backslashes on the way through bash: `\\'` arrives as
`'` and the build fails anyway. This has happened repeatedly.

Write string resources with the **Edit or Write tool**, or with a Python script saved to a
file and then run. Never with `sed`, `-c`, or a heredoc.

## Where a string belongs

`app/src/main/res/values/strings.xml` is the source of truth and is not generated.
`values-<lang>/strings.xml` holds the translations; a key missing from a language falls back
to English at runtime, so a partial translation is a normal state rather than a broken one.

Hero, weapon, ability and Stadium item **names are not translated** — they come from the
dataset, which is built from the English wiki. Ordinary words that arrive from the dataset,
like `Weapon`, `Common` or a role, *are* translated: map them through the `vocab_*` keys
rather than showing the raw value.

A string reachable in only one language — anything behind the seven-tap panel — is marked
`translatable="false"` rather than translated into fourteen languages that can never show it.

## Formatting

`%1$s`, `%2$d` and so on. A literal per-cent sign is `%%` **only in a string that takes
arguments**; in a string with none, `%%` is displayed literally as two characters. That
shipped once, reading "0%% monitor distance" on screen.

## Accents

Write the accented characters. Whatever channel a translation arrives through, check it did
not arrive stripped: German read "veroffentlicht", Polish "Swiat", Turkish "Dunya" and
Italian "abilita" for months, across seven languages, because nobody looked.

```bash
python tools/check_diacritics.py
```

It knows only words that are never correct unaccented in that language, so a hit is always a
real defect. Add to its lists rather than widening them with ambiguous pairs — Italian
"meta" and "ne" are real words and are deliberately absent.

## After changing anything

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug --console=plain -q 2>&1 | grep -E "^e:|Error:" | head -3
```

Android Lint catches things this skill cannot, including a percent sign that looks like a
format specifier. It is worth a run whenever several strings change:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:lintDebug --console=plain -q
```

Ignore the `MissingTranslation` errors — that is the known gap below, not a regression.

And to see what is still untranslated:

```bash
python -c "
import re,pathlib
RES=pathlib.Path('app/src/main/res')
def names(f):
    t=(RES/f/'strings.xml').read_text(encoding='utf-8')
    return {m.group(1) for m in re.finditer(r'<string name=\"([^\"]+)\"[^>]*>',t) if 'translatable=\"false\"' not in m.group(0)}
en=names('values')
for d in sorted(p.name for p in RES.iterdir() if p.name.startswith('values-')):
    print(d.replace('values-',''), len(en-names(d)), 'missing')
"
```

The current gap is recorded in `dataset/translation-gaps.md`: Italian complete, the other
fourteen missing about 130 each.
