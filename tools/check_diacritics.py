"""Find translated strings that lost their diacritics.

Several locales were written through a channel that stripped accents, so German read
"veroffentlicht", Polish "Swiat", Turkish "Dunya". Lint only catches a handful of these
because its typo dictionary is thin outside English, and a native speaker reading
"abilita" instead of "abilità" simply concludes the app was translated by a machine.

The word lists below are deliberately narrow: only forms that are *never* a correct word
in that language on their own. Ambiguous pairs are left out - Italian "meta" is a real
word, "ne" is a real word, so neither is listed even though both have accented twins.
A false positive here would train whoever runs this to ignore the output.
"""

from __future__ import annotations

import pathlib
import re
import sys

# Wrong spelling -> what it should be. One entry per unambiguous word.
STRIPPED: dict[str, dict[str, str]] = {
    "de": {
        "veroffentlicht": "veröffentlicht",
        "fur": "für",
        "uber": "über",
        "grosser": "größer",
        "hoher": "höher",
        "konnen": "können",
        "wahlen": "wählen",
        "zahlt": "zählt",
        "gewahlt": "gewählt",
        "erklart": "erklärt",
        "andern": "ändern",
        "spater": "später",
        "wahrend": "während",
        "naher": "näher",
        "schaden": "Schaden",  # capitalisation, not an accent, but the same channel
    },
    "es": {
        "cuantos": "cuántos",
        "cuanto": "cuánto",
        "region": "región",
        "America": "América",
        "numero": "número",
        "segun": "según",
        "aqui": "aquí",
        "asi": "así",
        "mas": "más",
        "tambien": "también",
        "despues": "después",
        # "solo" is deliberately absent: the RAE dropped the accent, so it is correct
        # as written and flagging it would be noise.
        "publico": "público",
        "critico": "crítico",
        "municion": "munición",
    },
    "fr": {
        "donnees": "données",
        "reduit": "réduit",
        "degats": "dégâts",
        "portee": "portée",
        "precision": "précision",
        "energie": "énergie",
        "systeme": "système",
        "apres": "après",
        "tres": "très",
        "deja": "déjà",
    },
    "it": {
        "perche": "perché",
        "piu": "più",
        "cioe": "cioè",
        "cio": "ciò",
        "gia": "già",
        "puo": "può",
        "abilita": "abilità",
        "qualita": "qualità",
        "citta": "città",
        "unita": "unità",
        "velocita": "velocità",
        "difficolta": "difficoltà",
        "possibilita": "possibilità",
        "attivita": "attività",
        "cosi": "così",
        "pero": "però",
        "poiche": "poiché",
        "finche": "finché",
        "sara": "sarà",
        "verra": "verrà",
        "avra": "avrà",
    },
    "pl": {
        "Swiat": "Świat",
        "swiat": "świat",
        "srednia": "średnia",
        "sredni": "średni",
        "rownymi": "równymi",
        "rowny": "równy",
        "swiatowych": "światowych",
        "wiecej": "więcej",
        "sila": "siła",
        "moze": "może",
        "zasieg": "zasięg",
        "obrazen": "obrażeń",
        "wlaczone": "włączone",
    },
    "pt": {
        "nao": "não",
        "numero": "número",
        "regiao": "região",
        "media": "média",
        "Americas": "Américas",
        "Asia": "Ásia",
        "voce": "você",
        "tambem": "também",
        "pagina": "página",
        "padrao": "padrão",
        "municao": "munição",
        "precisao": "precisão",
        "dano medio": "dano médio",
    },
    "sv": {
        "Varlden": "Världen",
        "varlden": "världen",
        "medelvardet": "medelvärdet",
        "varldssiffra": "världssiffra",
        "ar ": "är ",
        "traffar": "träffar",
        "rackvidd": "räckvidd",
        "hoger": "höger",
        "langre": "längre",
    },
    "tr": {
        "Dunya": "Dünya",
        "dunya": "dünya",
        "ortalamasidir": "ortalamasıdır",
        "esit": "eşit",
        "agirlikli": "ağırlıklı",
        "sayisi": "sayısı",
        "yayinlamiyor": "yayınlamıyor",
        # "hasar" and "mesafe" are correct as written - no accent to lose. They used to sit
        # here mapped to themselves, which the `wrong != right` filter silently dropped, so
        # the list looked longer than the work it did.
        "atis": "atış",
        "sarjor": "şarjör",
        "buyuk": "büyük",
        "kucuk": "küçük",
    },
}

# Locale folder -> which word list applies.
FOLDERS = {
    "values-de": "de",
    "values-es": "es",
    "values-fr": "fr",
    "values-it": "it",
    "values-pl": "pl",
    "values-pt-rBR": "pt",
    "values-sv": "sv",
    "values-tr": "tr",
}

STRING = re.compile(r'<string name="([^"]+)">(.*?)</string>', re.S)


def appears(wrong: str, right: str, body: str) -> bool:
    """Whether the stripped spelling is in this string.

    The search used to be exact, so it found "abilita" mid-sentence and walked straight past
    "Abilita potenziate" at the start of one. Three Italian strings sat wrong behind a
    checker reporting them clean.

    The fix is not `IGNORECASE`, which was the first thing tried and was worse: Python folds
    dotless "ı" onto "i", so every correct Turkish word containing one - "sayısı",
    "yayınlamıyor" - came back as a defect. A checker that cries wolf is one nobody reads,
    which this file's own opening paragraph says.

    Only the first letter may differ in case, because a capital in the middle of a word is
    not a sentence starting. And an entry that is itself about case - German capitalises its
    nouns, so `schaden -> Schaden` - stays exact, or it flags the correct spelling every
    time. Those are told apart by the pair: same letters, different case, means case is the
    point.
    """
    if wrong.lower() == right.lower():
        return re.search(rf"\b{re.escape(wrong)}\b", body) is not None

    head, tail = wrong[0], wrong[1:]
    either = f"[{re.escape(head.lower())}{re.escape(head.upper())}]"
    return re.search(rf"\b{either}{re.escape(tail)}\b", body) is not None


# What `appears` has to get right, checked on every run because it costs nothing and the
# alternative is finding out from a native speaker. The Turkish line is the one that matters:
# it is correctly spelled, and the obvious implementation of this function flags it.
SELF_CHECK = [
    ("abilita", "abilità", "Abilita potenziate", True),
    ("abilita", "abilità", "le abilita del gioco", True),
    ("abilita", "abilità", "Abilità potenziate", False),
    ("sayisi", "sayısı", "bölge oyuncu sayısı yayınlamıyor", False),
    ("schaden", "Schaden", "Der Schaden ist hoch", False),
    ("schaden", "Schaden", "der schaden ist hoch", True),
]


def self_check() -> None:
    for wrong, right, body, expected in SELF_CHECK:
        if appears(wrong, right, body) != expected:
            raise AssertionError(
                f"appears({wrong!r}, {right!r}, {body!r}) should be {expected}"
            )


def main() -> int:
    # The point of this script is accented characters, so it cannot print through the
    # Windows console's cp1252 default.
    sys.stdout.reconfigure(encoding="utf-8")
    self_check()
    res = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res"
    found = 0
    for folder, lang in sorted(FOLDERS.items()):
        path = res / folder / "strings.xml"
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8")
        words = STRIPPED[lang]
        for line_no, line in enumerate(text.splitlines(), start=1):
            match = STRING.search(line)
            if not match:
                continue
            name, body = match.groups()
            hits = [
                (wrong, right)
                for wrong, right in words.items()
                if wrong != right and appears(wrong, right, body)
            ]
            if hits:
                found += len(hits)
                joined = ", ".join(f"{w} -> {r}" for w, r in hits)
                print(f"{folder}/strings.xml:{line_no}  {name}: {joined}")
    print(f"\n{found} stripped spellings")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
