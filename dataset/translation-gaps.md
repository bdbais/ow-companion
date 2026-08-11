# Translation gaps

**None.** As of version 1.7.6 every one of the fifteen languages carries all 351
translatable strings.

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

## What is still English

Hero, weapon, ability and Stadium item **names**. They come from the dataset, which is built
from the English wiki, so a Korean player gets a Korean interface around English hero names.
Fixing that needs a localised data source rather than a bigger table.
