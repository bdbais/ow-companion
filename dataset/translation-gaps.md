# Translation gaps

**None.** Every one of the fifteen languages carries all 360 translatable strings, plurals
included. It has been that way since 1.7.6, when the count was 351.

| language | missing |
|---|---|
| ar | 0 |
| de | 0 |
| es | 0 |
| fr | 0 |
| it | 0 |
| ja | 0 |
| ko | 0 |
| pl | 0 |
| pt-rBR | 0 |
| ru | 0 |
| sv | 0 |
| tr | 0 |
| uk | 0 |
| zh-rCN | 0 |
| zh-rTW | 0 |

It stayed at 130 missing per language for months because nothing failed when a string was
absent: Android falls back to English at runtime, so a half-translated app looks like a
working app to whoever built it. Android Lint does report it, as `MissingTranslation`
errors, but the report was never read.

## Keeping it at zero

```bash
python tools/make_translations.py
```

Prints a per-language count. Fourteen languages are generated from the table in that script
— editing their XML directly is pointless, because the next run overwrites it. Italian is
hand-written in `values-it/strings.xml` and is the one that can silently fall behind, so the
same script counts it out loud at the end.

```bash
python tools/check_diacritics.py
```

Catches translations that arrived with their accents stripped. Seven languages shipped that
way; see the script for why its word lists are deliberately narrow.

A string that should not be translated at all — the app's name, a Twitch handle, the `t1`
label on the tactics board — is marked `translatable="false"` in `values/strings.xml` rather
than copied into fifteen files.

## Plurals

Three strings count something and are `<plurals>` rather than `<string>`: a build's items,
a duel's shots, and the ultimates left out of a ranking. The generator emits them from its
own table, and each language gets the quantities it actually uses — one form for Japanese,
Korean, Chinese and Turkish, two for most of Europe, four for Russian, Ukrainian and Polish.
Arabic needs six, and its phrase is written so the count governs nothing rather than guessed
at.

Everything else that carries a number carries two of them ("%1$d of %2$d spent"), and an
Android plural agrees with one quantity, so those stay plain strings permanently.

## What is still English

Hero, weapon, ability and Stadium item names used to be, all of them. Blizzard publish the
roster per locale and `tools/fetch_names.py` collects it, so ten of the fifteen languages now
get the game's own words for heroes, abilities, perks and Stadium powers — names and
descriptions both.

What is left:

- **Simplified Chinese, Ukrainian, Swedish, Arabic and Turkish.** Blizzard do not publish the
  game in them, so there is no official source and nothing on this end can invent one.
- **The Stadium armoury**, in every language. Those item names are not in any locale Blizzard
  serve — the per-hero powers are, the generic items are not.

Both are contributed by hand in `dataset/names-contributed.json`, which wins over anything
collected, accepts partial work, and is pointed at from the About screen.
