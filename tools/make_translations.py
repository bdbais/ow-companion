"""Generates the localised string resources.

    python tools/make_translations.py

Keeping every language in one table rather than in fourteen XML files makes it possible to
see a string in every language at once, which is how you notice that one of them says
something different from the rest.

English lives in `values/strings.xml` and is the source of truth: it is not generated here.
A key missing from a language falls back to English at runtime, so a partial translation is
a normal state rather than a broken one.

Hero, weapon, ability and item names are **not** translated: they come from the dataset,
which is built from the English wiki. A Korean player gets a Korean interface around English
hero names. Fixing that needs a localised data source, not a bigger table here.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

# Android resource qualifier -> the key used in the table below.
LOCALES = {
    "es": "es",
    "pt-rBR": "pt",
    "fr": "fr",
    "de": "de",
    "ja": "ja",
    "ko": "ko",
    "zh-rCN": "zhCN",
    "zh-rTW": "zhTW",
    "ru": "ru",
    "uk": "uk",
    "sv": "sv",
    "ar": "ar",
    "pl": "pl",
    "tr": "tr",
}


def t(**kwargs) -> dict:
    return kwargs


# Terms deliberately left in English across every language, because that is what players
# call them in voice chat regardless of their client language.
TRANSLATIONS: dict[str, dict] = {
    # --- navigation -----------------------------------------------------------------
    "tab_chart": t(
        es="Gráfico", pt="Gráfico", fr="Graphique", de="Diagramm", ja="グラフ", ko="차트",
        zhCN="图表", zhTW="圖表", ru="График", uk="Графік", sv="Diagram", ar="الرسم",
        pl="Wykres", tr="Grafik",
    ),
    "tab_rankings": t(
        es="Rankings", pt="Rankings", fr="Rangs", de="Ränge", ja="順位",
        ko="순위", zhCN="排行", zhTW="排行", ru="Рейтинг", uk="Рейтинг", sv="Ranking",
        ar="الترتيب", pl="Rankingi", tr="Sıralama",
    ),
    "tab_wiki": t(
        es="Héroes", pt="Heróis", fr="Héros", de="Helden", ja="ヒーロー", ko="영웅",
        zhCN="英雄", zhTW="英雄", ru="Герои", uk="Герої", sv="Hjältar", ar="الأبطال",
        pl="Bohater.", tr="Kahraman",
    ),
    "tab_custom": t(
        es="Taller", pt="Oficina", fr="Labo", de="Labor", ja="ラボ", ko="실험실",
        zhCN="实验室", zhTW="實驗室", ru="Лаб", uk="Лаб", sv="Labb", ar="المختبر",
        pl="Lab", tr="Lab",
    ),
    "tab_stadium": t(
        es="Stadium", pt="Stadium", fr="Stadium", de="Stadium", ja="闘技場",
        ko="스타디움", zhCN="竞技场", zhTW="競技場", ru="Арена", uk="Арена",
        sv="Stadium", ar="الملعب", pl="Stadion", tr="Stadyum",
    ),
    "tab_about": t(
        es="Info", pt="Sobre", fr="Infos", de="Info", ja="情報", ko="정보",
        zhCN="关于", zhTW="關於", ru="Инфо", uk="Інфо", sv="Om",
        ar="حول", pl="Info", tr="Hakkında",
    ),

    # --- damage chart ---------------------------------------------------------------
    "chart_distance": t(
        es="Distancia %1$d m", pt="Distância %1$d m", fr="Distance %1$d m",
        de="Entfernung %1$d m", ja="距離 %1$d m", ko="거리 %1$d m", zhCN="距离 %1$d 米",
        zhTW="距離 %1$d 公尺", ru="Дистанция %1$d м", uk="Дистанція %1$d м",
        sv="Avstånd %1$d m", ar="المسافة %1$d م", pl="Dystans %1$d m", tr="Mesafe %1$d m",
    ),
    "chart_summary": t(
        es="Mira %1$s, %2$s m  ·  %3$d armas  ·  %4$d ms",
        pt="Mira %1$s, %2$s m  ·  %3$d armas  ·  %4$d ms",
        fr="Visée %1$s, %2$s m  ·  %3$d armes  ·  %4$d ms",
        de="Ziel %1$s, %2$s m  ·  %3$d Waffen  ·  %4$d ms",
        ja="照準 %1$s, %2$s m  ·  %3$d 武器  ·  %4$d ms",
        ko="조준 %1$s, %2$s m  ·  무기 %3$d개  ·  %4$d ms",
        zhCN="瞄准 %1$s, %2$s 米  ·  %3$d 件武器  ·  %4$d 毫秒",
        zhTW="瞄準 %1$s, %2$s 公尺  ·  %3$d 件武器  ·  %4$d 毫秒",
        ru="Прицел %1$s, %2$s м  ·  оружия: %3$d  ·  %4$d мс",
        uk="Приціл %1$s, %2$s м  ·  зброї: %3$d  ·  %4$d мс",
        sv="Sikte %1$s, %2$s m  ·  %3$d vapen  ·  %4$d ms",
        ar="التصويب %1$s، %2$s م  ·  %3$d سلاح  ·  %4$d مللي ثانية",
        pl="Celownik %1$s, %2$s m  ·  %3$d broni  ·  %4$d ms",
        tr="Nişan %1$s, %2$s m  ·  %3$d silah  ·  %4$d ms",
    ),
    "chart_time_axis": t(
        es="tiempo (s)", pt="tempo (s)", fr="temps (s)", de="Zeit (s)", ja="時間 (秒)",
        ko="시간 (초)", zhCN="时间 (秒)", zhTW="時間 (秒)", ru="время (с)", uk="час (с)",
        sv="tid (s)", ar="الزمن (ث)", pl="czas (s)", tr="süre (sn)",
    ),
    "chart_collapse": t(
        es="Contraer los controles", pt="Recolher os controles", fr="Replier les commandes",
        de="Bedienelemente einklappen", ja="コントロールを折りたたむ", ko="설정 접기",
        zhCN="收起控件", zhTW="收起控制項", ru="Свернуть настройки", uk="Згорнути налаштування",
        sv="Fäll ihop kontrollerna", ar="طيّ عناصر التحكم", pl="Zwiń ustawienia",
        tr="Kontrolleri gizle",
    ),
    "chart_expand": t(
        es="Mostrar los controles", pt="Mostrar os controles", fr="Afficher les commandes",
        de="Bedienelemente anzeigen", ja="コントロールを表示", ko="설정 펼치기",
        zhCN="展开控件", zhTW="展開控制項", ru="Показать настройки", uk="Показати налаштування",
        sv="Visa kontrollerna", ar="إظهار عناصر التحكم", pl="Pokaż ustawienia",
        tr="Kontrolleri göster",
    ),
    "chart_energy": t(
        es="Carga del arma %1$d%%", pt="Carga da arma %1$d%%", fr="Charge de l\\'arme %1$d%%",
        de="Waffenaufladung %1$d%%", ja="武器チャージ %1$d%%", ko="무기 충전 %1$d%%",
        zhCN="武器充能 %1$d%%", zhTW="武器充能 %1$d%%", ru="Заряд оружия %1$d%%",
        uk="Заряд зброї %1$d%%", sv="Vapenladdning %1$d%%", ar="شحن السلاح %1$d%%",
        pl="Ładunek broni %1$d%%", tr="Silah şarjı %1$d%%",
    ),
    "chart_section_sort": t(
        es="Ordenar por", pt="Ordenar por", fr="Trier par", de="Sortieren nach",
        ja="並び替え", ko="정렬 기준", zhCN="排序方式", zhTW="排序方式", ru="Сортировка",
        uk="Сортування", sv="Sortera efter", ar="ترتيب حسب", pl="Sortuj według",
        tr="Sıralama ölçütü",
    ),
    "chart_section_modifiers": t(
        es="Modificadores", pt="Modificadores", fr="Modificateurs", de="Modifikatoren",
        ja="効果", ko="효과", zhCN="增益效果", zhTW="增益效果", ru="Модификаторы",
        uk="Модифікатори", sv="Modifierare", ar="المُعدِّلات", pl="Modyfikatory",
        tr="Değiştiriciler",
    ),
    "chart_section_role": t(
        es="Rol", pt="Função", fr="Rôle", de="Rolle", ja="ロール", ko="역할",
        zhCN="定位", zhTW="定位", ru="Роль", uk="Роль", sv="Roll", ar="الدور",
        pl="Rola", tr="Rol",
    ),
    "chart_section_weapon_type": t(
        es="Tipo de arma", pt="Tipo de arma", fr="Type d\\'arme", de="Waffentyp",
        ja="武器タイプ", ko="무기 유형", zhCN="武器类型", zhTW="武器類型", ru="Тип оружия",
        uk="Тип зброї", sv="Vapentyp", ar="نوع السلاح", pl="Typ broni", tr="Silah türü",
    ),
    "chart_summary_active": t(
        es="%1$d activos", pt="%1$d ativos", fr="%1$d actifs", de="%1$d aktiv",
        ja="%1$d 個オン", ko="%1$d개 켜짐", zhCN="%1$d 项开启", zhTW="%1$d 項開啟",
        ru="активно: %1$d", uk="активно: %1$d", sv="%1$d på", ar="%1$d مُفعّل",
        pl="%1$d wł.", tr="%1$d açık",
    ),
    "chart_summary_hidden": t(
        es="%1$d ocultos", pt="%1$d ocultos", fr="%1$d masqués", de="%1$d ausgeblendet",
        ja="%1$d 個非表示", ko="%1$d개 숨김", zhCN="隐藏 %1$d 项", zhTW="隱藏 %1$d 項",
        ru="скрыто: %1$d", uk="приховано: %1$d", sv="%1$d dolda", ar="%1$d مخفي",
        pl="%1$d ukrytych", tr="%1$d gizli",
    ),

    # --- sort orders ----------------------------------------------------------------
    "sort_dps": t(
        es="DPS", pt="DPS", fr="DPS", de="DPS", ja="DPS", ko="DPS", zhCN="DPS",
        zhTW="DPS", ru="Урон/с", uk="Шкода/с", sv="DPS", ar="ضرر/ث", pl="DPS", tr="DPS",
    ),
    "sort_dps_no_reload": t(
        es="DPS sin recarga", pt="DPS sem recarga", fr="DPS sans recharge",
        de="DPS ohne Nachladen", ja="DPS（リロード除く）", ko="DPS (재장전 제외)",
        zhCN="DPS（不含换弹）", zhTW="DPS（不含換彈）", ru="Урон/с без перезарядки",
        uk="Шкода/с без перезарядки", sv="DPS utan omladdning", ar="ضرر/ث بدون إعادة تذخير",
        pl="DPS bez przeładowania", tr="DPS, şarjörsüz",
    ),
    "sort_accuracy": t(
        es="Precisión", pt="Precisão", fr="Précision", de="Genauigkeit", ja="命中率",
        ko="명중률", zhCN="命中率", zhTW="命中率", ru="Точность", uk="Точність",
        sv="Träffsäkerhet", ar="الدقة", pl="Celność", tr="İsabet",
    ),
    "sort_crit_accuracy": t(
        es="Precisión crítica", pt="Precisão crítica", fr="Précision critique",
        de="Krit-Genauigkeit", ja="クリティカル率", ko="치명타 명중률", zhCN="爆头率",
        zhTW="爆頭率", ru="Точность криты", uk="Точність критів", sv="Kritisk träffsäkerhet",
        ar="دقة الضربات الحرجة", pl="Celność krytyczna", tr="Kritik isabet",
    ),
    "sort_time_to_kill": t(
        es="Tiempo de muerte", pt="Tempo até a morte", fr="Temps pour tuer",
        de="Zeit bis Kill", ja="キルタイム", ko="처치 시간", zhCN="击杀时间",
        zhTW="擊殺時間", ru="Время до убийства", uk="Час до вбивства", sv="Tid till kill",
        ar="زمن القتل", pl="Czas do zabicia", tr="Öldürme süresi",
    ),
    "sort_hero": t(
        es="Héroe", pt="Herói", fr="Héros", de="Held", ja="ヒーロー", ko="영웅",
        zhCN="英雄", zhTW="英雄", ru="Герой", uk="Герой", sv="Hjälte", ar="البطل",
        pl="Bohater", tr="Kahraman",
    ),

    # --- roles and weapon types -----------------------------------------------------
    "role_tank": t(
        es="Tanque", pt="Tanque", fr="Tank", de="Tank", ja="タンク", ko="돌격",
        zhCN="重装", zhTW="重裝", ru="Танк", uk="Танк", sv="Tank", ar="دبابة",
        pl="Tank", tr="Tank",
    ),
    "role_damage": t(
        es="Daño", pt="Dano", fr="Dégâts", de="Schaden", ja="ダメージ", ko="공격",
        zhCN="输出", zhTW="輸出", ru="Урон", uk="Шкода", sv="Skada", ar="ضرر",
        pl="Obrażenia", tr="Hasar",
    ),
    "role_support": t(
        es="Apoyo", pt="Suporte", fr="Soutien", de="Unterstützung", ja="サポート",
        ko="지원", zhCN="辅助", zhTW="輔助", ru="Поддержка", uk="Підтримка", sv="Support",
        ar="دعم", pl="Wsparcie", tr="Destek",
    ),
    "weapon_hitscan": t(
        es="Hitscan", pt="Hitscan", fr="Hitscan", de="Hitscan", ja="ヒットスキャン",
        ko="히트스캔", zhCN="即时命中", zhTW="即時命中", ru="Мгновенное попадание",
        uk="Миттєве влучання", sv="Hitscan", ar="إصابة فورية", pl="Hitscan", tr="Anlık isabet",
    ),
    "weapon_projectile": t(
        es="Proyectil", pt="Projétil", fr="Projectile", de="Projektil", ja="投射物",
        ko="투사체", zhCN="弹道", zhTW="彈道", ru="Снаряд", uk="Снаряд", sv="Projektil",
        ar="مقذوف", pl="Pocisk", tr="Mermi",
    ),
    "weapon_shotgun": t(
        es="Escopeta", pt="Escopeta", fr="Fusil à pompe", de="Schrotflinte",
        ja="ショットガン", ko="산탄총", zhCN="霰弹枪", zhTW="霰彈槍", ru="Дробовик",
        uk="Дробовик", sv="Hagelgevär", ar="بندقية", pl="Strzelba", tr="Pompalı",
    ),
    "weapon_beam": t(
        es="Rayo", pt="Feixe", fr="Rayon", de="Strahl", ja="ビーム", ko="광선",
        zhCN="射线", zhTW="射線", ru="Луч", uk="Промінь", sv="Stråle", ar="شعاع",
        pl="Wiązka", tr="Işın",
    ),
    "weapon_melee": t(
        es="Cuerpo a cuerpo", pt="Corpo a corpo", fr="Corps à corps", de="Nahkampf",
        ja="近接", ko="근접", zhCN="近战", zhTW="近戰", ru="Ближний бой",
        uk="Ближній бій", sv="Närstrid", ar="اشتباك", pl="Walka wręcz", tr="Yakın dövüş",
    ),

    # --- modifiers ------------------------------------------------------------------
    # Ability names follow Blizzard's own localisation where these are well known.
    "modifier_armor": t(
        es="Armadura", pt="Armadura", fr="Armure", de="Rüstung", ja="アーマー", ko="방어구",
        zhCN="护甲", zhTW="護甲", ru="Броня", uk="Броня", sv="Rustning", ar="درع",
        pl="Pancerz", tr="Zırh",
    ),
    "modifier_damage_boost": t(
        es="Amplificar daño", pt="Ampliar dano", fr="Amplification de dégâts",
        de="Schadensverstärkung", ja="ダメージブースト", ko="공격력 강화", zhCN="伤害增强",
        zhTW="傷害增強", ru="Усиление урона", uk="Підсилення шкоди", sv="Skadeboost",
        ar="تعزيز الضرر", pl="Wzmocnienie obrażeń", tr="Hasar artışı",
    ),
    "modifier_discord": t(
        es="Discordia", pt="Discórdia", fr="Discorde", de="Disharmonie", ja="不和",
        ko="불화", zhCN="混乱", zhTW="混亂", ru="Раздор", uk="Розбрат", sv="Disharmoni",
        ar="الشقاق", pl="Niezgoda", tr="Uyumsuzluk",
    ),
    "modifier_nano_damage": t(
        es="Nano (daño)", pt="Nano (dano)", fr="Nano (dégâts)", de="Nano (Schaden)",
        ja="ナノ（攻撃）", ko="나노 (공격)", zhCN="纳米（伤害）", zhTW="奈米（傷害）",
        ru="Нано (урон)", uk="Нано (шкода)", sv="Nano (skada)", ar="نانو (ضرر)",
        pl="Nano (obrażenia)", tr="Nano (hasar)",
    ),
    "modifier_nano_defence": t(
        es="Nano (defensa)", pt="Nano (defesa)", fr="Nano (défense)", de="Nano (Verteidigung)",
        ja="ナノ（防御）", ko="나노 (방어)", zhCN="纳米（防御）", zhTW="奈米（防禦）",
        ru="Нано (защита)", uk="Нано (захист)", sv="Nano (försvar)", ar="نانو (دفاع)",
        pl="Nano (obrona)", tr="Nano (savunma)",
    ),
    "modifier_supercharger": t(
        es="Supercargador", pt="Supercarregador", fr="Surchargeur", de="Superlader",
        ja="スーパーチャージャー", ko="증폭기", zhCN="增效器", zhTW="增效器",
        ru="Ускоритель", uk="Прискорювач", sv="Superladdare", ar="المُعزِّز",
        pl="Wzmacniacz", tr="Süper şarj",
    ),
    "modifier_amplification_matrix": t(
        es="Matriz amplif.", pt="Matriz amplif.", fr="Matrice d\\'amplif.",
        de="Verstärkungsmatrix", ja="増幅マトリクス", ko="증폭 매트릭스", zhCN="增幅矩阵",
        zhTW="增幅矩陣", ru="Усиливающая матрица", uk="Підсилювальна матриця",
        sv="Förstärkningsmatris", ar="مصفوفة التضخيم", pl="Matryca wzmacniająca",
        tr="Güçlendirme matrisi",
    ),
    "modifier_fortify": t(
        es="Fortificar", pt="Fortificar", fr="Fortification", de="Verstärkung",
        ja="要塞化", ko="강화", zhCN="强化", zhTW="強化", ru="Укрепление",
        uk="Укріплення", sv="Förstärk", ar="التحصين", pl="Umocnienie", tr="Takviye",
    ),
    "modifier_breather": t(
        es="Respiro", pt="Respirar", fr="Bouffée d\\'air", de="Verschnaufpause",
        ja="ひと息", ko="숨 돌리기", zhCN="喘口气", zhTW="喘口氣", ru="Передышка",
        uk="Перепочинок", sv="Andningspaus", ar="التقاط الأنفاس", pl="Zaczerpnij tchu",
        tr="Nefeslen",
    ),
    "modifier_kitsune_rush": t(
        es="Carrera del kitsune", pt="Corrida da kitsune", fr="Ruée du kitsune",
        de="Kitsune-Ansturm", ja="狐の疾走", ko="여우의 정령", zhCN="狐之疾走",
        zhTW="狐之疾走", ru="Бег кицунэ", uk="Біг кіцуне", sv="Kitsune-rusning",
        ar="اندفاع كيتسوني", pl="Pęd kitsune", tr="Kitsune koşusu",
    ),

    # --- rankings -------------------------------------------------------------------
    "rank_weapons": t(
        es="Armas", pt="Armas", fr="Armes", de="Waffen", ja="武器", ko="무기",
        zhCN="武器", zhTW="武器", ru="Оружие", uk="Зброя", sv="Vapen", ar="الأسلحة",
        pl="Broń", tr="Silahlar",
    ),
    "rank_ultimates": t(
        es="Definitivas", pt="Supremas", fr="Ultimes", de="Ultis", ja="アルティメット",
        ko="궁극기", zhCN="终极技能", zhTW="終極技能", ru="Суперспособности",
        uk="Суперздібності", sv="Ultimates", ar="القدرات القصوى", pl="Ultimate",
        tr="Ultimate",
    ),
    "rank_healing": t(
        es="Curación", pt="Cura", fr="Soins", de="Heilung", ja="回復", ko="치유",
        zhCN="治疗", zhTW="治療", ru="Лечение", uk="Лікування", sv="Läkning",
        ar="الشفاء", pl="Leczenie", tr="İyileştirme",
    ),
    "leaderboard_buffs": t(
        es="Mejoras permitidas", pt="Bônus permitidos", fr="Bonus autorisés",
        de="Erlaubte Buffs", ja="許可するバフ", ko="허용할 버프", zhCN="允许的增益",
        zhTW="允許的增益", ru="Разрешённые усиления", uk="Дозволені підсилення",
        sv="Tillåtna buffar", ar="التعزيزات المسموحة", pl="Dozwolone wzmocnienia",
        tr="İzin verilen buff\\'lar",
    ),
    "leaderboard_searching": t(
        es="Probando cada arma a cada distancia",
        pt="Testando cada arma em cada distância",
        fr="Chaque arme testée à chaque distance",
        de="Jede Waffe auf jeder Entfernung",
        ja="すべての武器をすべての距離で試行中",
        ko="모든 무기를 모든 거리에서 시험 중",
        zhCN="正在测试每件武器的每个距离",
        zhTW="正在測試每件武器的每個距離",
        ru="Проверяем каждое оружие на каждой дистанции",
        uk="Перевіряємо кожну зброю на кожній дистанції",
        sv="Testar varje vapen på varje avstånd",
        ar="تجربة كل سلاح على كل مسافة",
        pl="Sprawdzam każdą broń na każdym dystansie",
        tr="Her silah her mesafede deneniyor",
    ),
    "leaderboard_head": t(
        es="apuntando a la cabeza", pt="mirando na cabeça", fr="visée tête",
        de="auf den Kopf gezielt", ja="ヘッドショット狙い", ko="머리를 조준",
        zhCN="瞄准头部", zhTW="瞄準頭部", ru="в голову", uk="у голову",
        sv="siktar mot huvudet", ar="التصويب على الرأس", pl="celując w głowę",
        tr="kafaya nişan alarak",
    ),
    "leaderboard_body": t(
        es="al centro del cuerpo", pt="no centro do corpo", fr="au centre du corps",
        de="auf den Rumpf", ja="胴体狙い", ko="몸통을 조준", zhCN="瞄准躯干",
        zhTW="瞄準軀幹", ru="в корпус", uk="у корпус", sv="mot kroppen",
        ar="على الجسد", pl="w tułów", tr="gövdeye",
    ),
    "leaderboard_at_distance": t(
        es="a %1$s m", pt="a %1$s m", fr="à %1$s m", de="auf %1$s m", ja="%1$s m で",
        ko="%1$s m에서", zhCN="在 %1$s 米", zhTW="在 %1$s 公尺", ru="на %1$s м",
        uk="на %1$s м", sv="på %1$s m", ar="على بعد %1$s م", pl="na %1$s m",
        tr="%1$s m mesafede",
    ),
    "leaderboard_crits": t(
        es="%1$s%% críticos", pt="%1$s%% críticos", fr="%1$s%% de critiques",
        de="%1$s%% Krits", ja="クリティカル %1$s%%", ko="치명타 %1$s%%",
        zhCN="%1$s%% 爆头", zhTW="%1$s%% 爆頭", ru="криты %1$s%%", uk="крити %1$s%%",
        sv="%1$s%% kritiska", ar="%1$s%% ضربات حرجة", pl="%1$s%% krytyków",
        tr="%%%1$s kritik",
    ),
    "leaderboard_kill_instant": t(
        es="mata un objetivo de 600 pv al instante",
        pt="mata um alvo de 600 pv instantaneamente",
        fr="tue une cible de 600 pv instantanément",
        de="tötet ein 600-TP-Ziel sofort",
        ja="600HPの敵を即座に倒す",
        ko="600 체력 대상을 즉시 처치",
        zhCN="瞬间击杀 600 生命值目标",
        zhTW="瞬間擊殺 600 生命值目標",
        ru="убивает цель с 600 ХП мгновенно",
        uk="вбиває ціль із 600 ХП миттєво",
        sv="dödar ett mål med 600 hp direkt",
        ar="يقتل هدفًا بـ600 نقطة صحة فورًا",
        pl="zabija cel z 600 pż natychmiast",
        tr="600 canlı hedefi anında öldürür",
    ),
    "leaderboard_kill_in": t(
        es="mata un objetivo de 600 pv en %1$s s",
        pt="mata um alvo de 600 pv em %1$s s",
        fr="tue une cible de 600 pv en %1$s s",
        de="tötet ein 600-TP-Ziel in %1$s s",
        ja="600HPの敵を %1$s 秒で倒す",
        ko="600 체력 대상을 %1$s초 만에 처치",
        zhCN="%1$s 秒击杀 600 生命值目标",
        zhTW="%1$s 秒擊殺 600 生命值目標",
        ru="убивает цель с 600 ХП за %1$s с",
        uk="вбиває ціль із 600 ХП за %1$s с",
        sv="dödar ett mål med 600 hp på %1$s s",
        ar="يقتل هدفًا بـ600 نقطة صحة في %1$s ث",
        pl="zabija cel z 600 pż w %1$s s",
        tr="600 canlı hedefi %1$s sn\\'de öldürür",
    ),
    "buff_nano": t(
        es="Nanoimpulso", pt="Nanoimpulso", fr="Nano-boost", de="Nano-Boost",
        ja="ナノ・ブースト", ko="나노 강화", zhCN="纳米激素", zhTW="奈米激素",
        ru="Наноусилитель", uk="Наностимулятор", sv="Nanoboost", ar="تعزيز النانو",
        pl="Nanowzmocnienie", tr="Nano güçlendirme",
    ),
    "rank_buffs_weapons_only": t(
        es="Las mejoras solo valen para las armas: una definitiva hace lo que hace, y amplificar daño no multiplica la curación.",
        pt="Os bônus valem só para armas: uma suprema causa o que causa, e ampliar dano não multiplica cura.",
        fr="Les bonus ne valent que pour les armes : une ultime fait ce qu\\'elle fait, et l\\'amplification de dégâts ne multiplie pas les soins.",
        de="Buffs gelten nur für Waffen: Eine Ulti macht, was sie macht, und Schadensverstärkung vervielfacht keine Heilung.",
        ja="バフが効くのは武器だけです。アルティメットの威力は固定で、ダメージブーストは回復量を増やしません。",
        ko="버프는 무기에만 적용됩니다. 궁극기의 피해량은 고정이며, 공격력 강화는 치유량을 늘리지 않습니다.",
        zhCN="增益只对武器生效：终极技能的伤害是固定的，伤害增强也不会放大治疗量。",
        zhTW="增益只對武器生效：終極技能的傷害是固定的，傷害增強也不會放大治療量。",
        ru="Усиления действуют только на оружие: урон суперспособности фиксирован, а усиление урона не умножает лечение.",
        uk="Підсилення діють лише на зброю: шкода суперздібності фіксована, а підсилення шкоди не множить лікування.",
        sv="Buffar gäller bara vapen: en ultimate gör vad den gör, och skadeboost multiplicerar inte läkning.",
        ar="التعزيزات تنطبق على الأسلحة فقط: القدرة القصوى تُحدث ضررها الثابت، وتعزيز الضرر لا يضاعف الشفاء.",
        pl="Wzmocnienia działają tylko na broń: ultimate zadaje tyle, ile zadaje, a wzmocnienie obrażeń nie mnoży leczenia.",
        tr="Buff\\'lar yalnızca silahlar için geçerli: bir ultimate ne veriyorsa onu verir ve hasar artışı iyileştirmeyi çarpmaz.",
    ),

    # --- hero wiki ------------------------------------------------------------------
    "wiki_search": t(
        es="Buscar héroes y habilidades", pt="Buscar heróis e habilidades",
        fr="Rechercher héros et capacités", de="Helden und Fähigkeiten suchen",
        ja="ヒーローとアビリティを検索", ko="영웅과 기술 검색", zhCN="搜索英雄和技能",
        zhTW="搜尋英雄和技能", ru="Поиск героев и способностей", uk="Пошук героїв і здібностей",
        sv="Sök hjältar och förmågor", ar="ابحث عن الأبطال والقدرات",
        pl="Szukaj bohaterów i zdolności", tr="Kahraman ve yetenek ara",
    ),
    "wiki_sort_name": t(
        es="Nombre", pt="Nome", fr="Nom", de="Name", ja="名前", ko="이름", zhCN="名称",
        zhTW="名稱", ru="Имя", uk="Ім\\'я", sv="Namn", ar="الاسم", pl="Nazwa", tr="İsim",
    ),
    "wiki_sort_release": t(
        es="Lanzamiento", pt="Lançamento", fr="Sortie", de="Erscheinen", ja="実装日",
        ko="출시", zhCN="上线时间", zhTW="上線時間", ru="Выход", uk="Вихід",
        sv="Släpp", ar="الإصدار", pl="Premiera", tr="Çıkış",
    ),
    "wiki_sort_health": t(
        es="Vida", pt="Vida", fr="Points de vie", de="Leben", ja="体力", ko="체력",
        zhCN="生命值", zhTW="生命值", ru="Здоровье", uk="Здоров\\'я", sv="Liv",
        ar="الصحة", pl="Zdrowie", tr="Can",
    ),
    "wiki_sort_changes": t(
        es="Más retocados", pt="Mais alterados", fr="Les plus retouchés",
        de="Am meisten geändert", ja="変更が多い順", ko="변경 많은 순",
        zhCN="改动最多", zhTW="改動最多", ru="Чаще менялись", uk="Частіше змінювались",
        sv="Mest ändrade", ar="الأكثر تعديلًا", pl="Najczęściej zmieniani",
        tr="En çok değişen",
    ),
    "wiki_health": t(
        es="Vida", pt="Vida", fr="Vie", de="Leben", ja="体力", ko="체력", zhCN="生命值",
        zhTW="生命值", ru="Здоровье", uk="Здоров\\'я", sv="Liv", ar="الصحة",
        pl="Zdrowie", tr="Can",
    ),
    "wiki_armor": t(
        es="Armadura", pt="Armadura", fr="Armure", de="Rüstung", ja="アーマー",
        ko="방어구", zhCN="护甲", zhTW="護甲", ru="Броня", uk="Броня", sv="Rustning",
        ar="درع", pl="Pancerz", tr="Zırh",
    ),
    "wiki_shields": t(
        es="Escudos", pt="Escudos", fr="Boucliers", de="Schilde", ja="シールド",
        ko="보호막", zhCN="护盾", zhTW="護盾", ru="Щиты", uk="Щити", sv="Sköldar",
        ar="دروع", pl="Tarcze", tr="Kalkan",
    ),
    "wiki_released": t(
        es="Lanzamiento", pt="Lançamento", fr="Sortie", de="Erschienen", ja="実装",
        ko="출시", zhCN="上线", zhTW="上線", ru="Вышел", uk="Вийшов", sv="Släppt",
        ar="صدر", pl="Premiera", tr="Çıkış",
    ),
    "wiki_hero_number": t(
        es="Héroe n.º %1$d", pt="Herói nº %1$d", fr="Héros nº %1$d", de="Held Nr. %1$d",
        ja="%1$d 番目のヒーロー", ko="%1$d번째 영웅", zhCN="第 %1$d 位英雄",
        zhTW="第 %1$d 位英雄", ru="Герой № %1$d", uk="Герой № %1$d", sv="Hjälte nr %1$d",
        ar="البطل رقم %1$d", pl="Bohater nr %1$d", tr="%1$d. kahraman",
    ),
    "wiki_abilities": t(
        es="Habilidades", pt="Habilidades", fr="Capacités", de="Fähigkeiten",
        ja="アビリティ", ko="기술", zhCN="技能", zhTW="技能", ru="Способности",
        uk="Здібності", sv="Förmågor", ar="القدرات", pl="Zdolności", tr="Yetenekler",
    ),
    "wiki_perks": t(
        es="Ventajas", pt="Vantagens", fr="Atouts", de="Vorteile", ja="パーク",
        ko="특성", zhCN="天赋", zhTW="天賦", ru="Перки", uk="Перки", sv="Perks",
        ar="المزايا", pl="Atuty", tr="Yetiler",
    ),
    "wiki_perk_minor": t(
        es="MENOR", pt="MENOR", fr="MINEUR", de="KLEIN", ja="マイナー", ko="하급",
        zhCN="初级", zhTW="初階", ru="МАЛЫЙ", uk="МАЛИЙ", sv="MINDRE", ar="صغيرة",
        pl="MNIEJSZY", tr="KÜÇÜK",
    ),
    "wiki_perk_major": t(
        es="MAYOR", pt="MAIOR", fr="MAJEUR", de="GROSS", ja="メジャー", ko="상급",
        zhCN="高级", zhTW="高階", ru="БОЛЬШОЙ", uk="ВЕЛИКИЙ", sv="STÖRRE", ar="كبيرة",
        pl="WIĘKSZY", tr="BÜYÜK",
    ),
    "wiki_close": t(
        es="Cerrar los detalles", pt="Fechar os detalhes", fr="Fermer les détails",
        de="Details schließen", ja="詳細を閉じる", ko="상세 정보 닫기", zhCN="关闭详情",
        zhTW="關閉詳情", ru="Закрыть подробности", uk="Закрити подробиці",
        sv="Stäng detaljerna", ar="إغلاق التفاصيل", pl="Zamknij szczegóły",
        tr="Ayrıntıları kapat",
    ),
    "wiki_pick_a_hero": t(
        es="Elige un héroe para ver sus detalles.",
        pt="Escolha um herói para ver os detalhes.",
        fr="Choisissez un héros pour voir ses détails.",
        de="Wähle einen Helden, um Details zu sehen.",
        ja="ヒーローを選ぶと詳細が表示されます。",
        ko="영웅을 선택하면 상세 정보가 표시됩니다.",
        zhCN="选择一位英雄以查看详情。",
        zhTW="選擇一位英雄以查看詳情。",
        ru="Выберите героя, чтобы увидеть подробности.",
        uk="Виберіть героя, щоб побачити подробиці.",
        sv="Välj en hjälte för att se detaljerna.",
        ar="اختر بطلًا لعرض تفاصيله.",
        pl="Wybierz bohatera, aby zobaczyć szczegóły.",
        tr="Ayrıntıları görmek için bir kahraman seçin.",
    ),
    "wiki_damage_over_time": t(
        es="Daño a lo largo del tiempo", pt="Dano ao longo do tempo",
        fr="Dégâts au fil du temps", de="Schaden im Zeitverlauf", ja="ダメージの推移",
        ko="시간에 따른 피해량", zhCN="伤害随时间变化", zhTW="傷害隨時間變化",
        ru="Урон со временем", uk="Шкода з часом", sv="Skada över tid",
        ar="الضرر عبر الزمن", pl="Obrażenia w czasie", tr="Zaman içinde hasar",
    ),
    "wiki_balance_history": t(
        es="Historial de ajustes", pt="Histórico de ajustes", fr="Historique d\\'équilibrage",
        de="Balance-Verlauf", ja="バランス調整の履歴", ko="밸런스 변경 이력",
        zhCN="平衡性调整记录", zhTW="平衡性調整紀錄", ru="История баланса",
        uk="Історія балансу", sv="Balanshistorik", ar="سجل التوازن",
        pl="Historia balansu", tr="Denge geçmişi",
    ),
    "wiki_filter_all": t(
        es="Todas", pt="Todas", fr="Toutes", de="Alle", ja="すべて", ko="전체",
        zhCN="全部", zhTW="全部", ru="Все", uk="Усі", sv="Alla", ar="الكل",
        pl="Wszystkie", tr="Tümü",
    ),
    "wiki_no_changes_for_ability": t(
        es="No hay cambios registrados para esta habilidad.",
        pt="Nenhuma alteração registrada para esta habilidade.",
        fr="Aucun changement enregistré pour cette capacité.",
        de="Für diese Fähigkeit sind keine Änderungen verzeichnet.",
        ja="このアビリティに記録された変更はありません。",
        ko="이 기술에 기록된 변경 사항이 없습니다.",
        zhCN="该技能没有记录在案的改动。",
        zhTW="該技能沒有記錄在案的改動。",
        ru="Для этой способности изменений не записано.",
        uk="Для цієї здібності змін не записано.",
        sv="Inga registrerade ändringar för den här förmågan.",
        ar="لا توجد تغييرات مسجّلة لهذه القدرة.",
        pl="Brak zapisanych zmian dla tej zdolności.",
        tr="Bu yetenek için kayıtlı bir değişiklik yok.",
    ),
    "wiki_more_changes": t(
        es="+%1$d más", pt="+%1$d mais", fr="+%1$d autres", de="+%1$d weitere",
        ja="他 %1$d 件", ko="%1$d개 더", zhCN="还有 %1$d 条", zhTW="還有 %1$d 條",
        ru="ещё %1$d", uk="ще %1$d", sv="+%1$d till", ar="+%1$d أخرى",
        pl="+%1$d więcej", tr="+%1$d daha",
    ),
    "wiki_buffs": t(
        es="%1$d mejora", pt="%1$d buff", fr="%1$d buff", de="%1$d Buff", ja="強化 %1$d",
        ko="상향 %1$d", zhCN="%1$d 加强", zhTW="%1$d 加強", ru="%1$d усиление",
        uk="%1$d підсилення", sv="%1$d buff", ar="%1$d تحسين", pl="%1$d wzmocnienie",
        tr="%1$d güçlendirme",
    ),
    "wiki_nerfs": t(
        es="%1$d recorte", pt="%1$d nerf", fr="%1$d nerf", de="%1$d Nerf", ja="弱体 %1$d",
        ko="하향 %1$d", zhCN="%1$d 削弱", zhTW="%1$d 削弱", ru="%1$d ослабление",
        uk="%1$d послаблення", sv="%1$d nerf", ar="%1$d إضعاف", pl="%1$d osłabienie",
        tr="%1$d zayıflatma",
    ),

    # --- lab ------------------------------------------------------------------------
    "custom_pick_hero": t(
        es="Héroe", pt="Herói", fr="Héros", de="Held", ja="ヒーロー", ko="영웅",
        zhCN="英雄", zhTW="英雄", ru="Герой", uk="Герой", sv="Hjälte", ar="البطل",
        pl="Bohater", tr="Kahraman",
    ),
    "custom_pick_weapon": t(
        es="Arma", pt="Arma", fr="Arme", de="Waffe", ja="武器", ko="무기", zhCN="武器",
        zhTW="武器", ru="Оружие", uk="Зброя", sv="Vapen", ar="السلاح", pl="Broń",
        tr="Silah",
    ),
    "custom_stat_damage": t(
        es="Daño por perdigón", pt="Dano por projétil", fr="Dégâts par plomb",
        de="Schaden pro Kugel", ja="弾あたりのダメージ", ko="탄환당 피해량",
        zhCN="每颗弹丸伤害", zhTW="每顆彈丸傷害", ru="Урон за дробину",
        uk="Шкода за дробину", sv="Skada per kula", ar="الضرر لكل قذيفة",
        pl="Obrażenia na pocisk", tr="Saçma başına hasar",
    ),
    "custom_stat_fire_rate": t(
        es="Disparos por segundo", pt="Disparos por segundo", fr="Tirs par seconde",
        de="Schüsse pro Sekunde", ja="毎秒の発射数", ko="초당 발사 수",
        zhCN="每秒射速", zhTW="每秒射速", ru="Выстрелов в секунду",
        uk="Пострілів за секунду", sv="Skott per sekund", ar="طلقات في الثانية",
        pl="Strzały na sekundę", tr="Saniyedeki atış",
    ),
    "custom_stat_ammo": t(
        es="Cargador", pt="Carregador", fr="Chargeur", de="Magazin", ja="装弾数",
        ko="탄창", zhCN="弹匣", zhTW="彈匣", ru="Магазин", uk="Магазин", sv="Magasin",
        ar="المخزن", pl="Magazynek", tr="Şarjör",
    ),
    "custom_stat_reload": t(
        es="Segundos de recarga", pt="Segundos de recarga", fr="Secondes de recharge",
        de="Nachladezeit in Sekunden", ja="リロード秒数", ko="재장전 시간(초)",
        zhCN="换弹秒数", zhTW="換彈秒數", ru="Перезарядка, с", uk="Перезарядка, с",
        sv="Omladdning i sekunder", ar="ثواني إعادة التذخير",
        pl="Sekundy przeładowania", tr="Şarjör süresi (sn)",
    ),
    "custom_stat_pellets": t(
        es="Perdigones por disparo", pt="Projéteis por disparo", fr="Plombs par tir",
        de="Kugeln pro Schuss", ja="1発あたりの弾数", ko="발사당 탄환 수",
        zhCN="每次射击弹丸数", zhTW="每次射擊彈丸數", ru="Дробин за выстрел",
        uk="Дробин за постріл", sv="Kulor per skott", ar="قذائف لكل طلقة",
        pl="Pociski na strzał", tr="Atış başına saçma",
    ),
    "custom_reset": t(
        es="Restaurar los valores reales", pt="Restaurar os valores reais",
        fr="Rétablir les valeurs réelles", de="Echte Werte wiederherstellen",
        ja="実際の値に戻す", ko="실제 값으로 되돌리기", zhCN="恢复真实数值",
        zhTW="恢復真實數值", ru="Вернуть настоящие значения",
        uk="Повернути справжні значення", sv="Återställ de riktiga värdena",
        ar="استعادة القيم الحقيقية", pl="Przywróć prawdziwe wartości",
        tr="Gerçek değerlere dön",
    ),
    "custom_rank": t(
        es="Puesto %1$d de %2$d", pt="Posição %1$d de %2$d", fr="Rang %1$d sur %2$d",
        de="Platz %1$d von %2$d", ja="%2$d 中 %1$d 位", ko="%2$d명 중 %1$d위",
        zhCN="第 %1$d 名，共 %2$d 名", zhTW="第 %1$d 名，共 %2$d 名",
        ru="%1$d место из %2$d", uk="%1$d місце з %2$d", sv="Plats %1$d av %2$d",
        ar="المرتبة %1$d من %2$d", pl="Miejsce %1$d z %2$d", tr="%2$d içinde %1$d.",
    ),
    "custom_rank_was": t(
        es="antes %1$d", pt="antes %1$d", fr="avant %1$d", de="vorher %1$d",
        ja="以前は %1$d 位", ko="이전 %1$d위", zhCN="原为第 %1$d 名",
        zhTW="原為第 %1$d 名", ru="было %1$d", uk="було %1$d", sv="var %1$d",
        ar="كان %1$d", pl="było %1$d", tr="önceden %1$d",
    ),
    "custom_delta": t(
        es="%1$s%% frente al héroe real", pt="%1$s%% em relação ao herói real",
        fr="%1$s%% par rapport au héros réel", de="%1$s%% gegenüber dem echten Helden",
        ja="実際のヒーロー比 %1$s%%", ko="실제 영웅 대비 %1$s%%",
        zhCN="相比真实英雄 %1$s%%", zhTW="相比真實英雄 %1$s%%",
        ru="%1$s%% к настоящему герою", uk="%1$s%% до справжнього героя",
        sv="%1$s%% mot den riktiga hjälten", ar="%1$s%% مقارنةً بالبطل الحقيقي",
        pl="%1$s%% wobec prawdziwego bohatera", tr="gerçek kahramana göre %%%1$s",
    ),
    "custom_unchanged": t(
        es="Aún no has cambiado nada: mueve un control.",
        pt="Você ainda não mudou nada: mova um controle.",
        fr="Rien n\\'a encore changé : bougez un curseur.",
        de="Noch nichts geändert – bewege einen Regler.",
        ja="まだ何も変えていません。スライダーを動かしてください。",
        ko="아직 아무것도 바꾸지 않았습니다. 슬라이더를 움직여 보세요.",
        zhCN="还没有改动，试试拖动一个滑块。",
        zhTW="還沒有改動，試試拖曳一個滑桿。",
        ru="Пока ничего не изменено — подвиньте ползунок.",
        uk="Поки нічого не змінено — посуньте повзунок.",
        sv="Inget ändrat än – dra i ett reglage.",
        ar="لم تغيّر شيئًا بعد: حرّك أحد المؤشرات.",
        pl="Nic jeszcze nie zmieniono – przesuń suwak.",
        tr="Henüz bir şey değişmedi – bir kaydırıcıyı oynatın.",
    ),
    "custom_neighbours": t(
        es="Quién tiene alrededor", pt="Quem está por perto", fr="Ses voisins au classement",
        de="Nachbarn in der Rangliste", ja="順位の前後", ko="순위에서 가까운 영웅",
        zhCN="排名前后", zhTW="排名前後", ru="Соседи в рейтинге", uk="Сусіди в рейтингу",
        sv="Grannarna i rankingen", ar="الجيران في الترتيب", pl="Sąsiedzi w rankingu",
        tr="Sıralamadaki komşuları",
    ),

    # --- stadium --------------------------------------------------------------------
    "stadium_spent": t(
        es="%1$d de %2$d gastados", pt="%1$d de %2$d gastos", fr="%1$d sur %2$d dépensés",
        de="%1$d von %2$d ausgegeben", ja="%2$d 中 %1$d 使用", ko="%2$d 중 %1$d 사용",
        zhCN="已花费 %1$d / %2$d", zhTW="已花費 %1$d / %2$d",
        ru="потрачено %1$d из %2$d", uk="витрачено %1$d із %2$d",
        sv="%1$d av %2$d spenderat", ar="أُنفق %1$d من %2$d",
        pl="wydano %1$d z %2$d", tr="%2$d bütçeden %1$d harcandı",
    ),
    "stadium_suggest": t(
        es="Proponer una build", pt="Sugerir uma build", fr="Proposer une build",
        de="Build vorschlagen", ja="ビルドを提案", ko="빌드 제안", zhCN="推荐配装",
        zhTW="推薦配裝", ru="Предложить сборку", uk="Запропонувати збірку",
        sv="Föreslå en build", ar="اقترح تجهيزة", pl="Zaproponuj build",
        tr="Bir yapı öner",
    ),
    "stadium_clear": t(
        es="Vaciar", pt="Limpar", fr="Vider", de="Leeren", ja="クリア", ko="비우기",
        zhCN="清空", zhTW="清空", ru="Очистить", uk="Очистити", sv="Rensa",
        ar="مسح", pl="Wyczyść", tr="Temizle",
    ),
    "stadium_armory": t(
        es="Armería", pt="Armaria", fr="Armurerie", de="Waffenkammer", ja="アーモリー",
        ko="무기고", zhCN="军械库", zhTW="軍械庫", ru="Арсенал", uk="Арсенал",
        sv="Vapenförråd", ar="مستودع الأسلحة", pl="Zbrojownia", tr="Cephanelik",
    ),
    "stadium_boosted": t(
        es="Habilidades potenciadas", pt="Habilidades potencializadas",
        fr="Capacités améliorées", de="Verstärkte Fähigkeiten", ja="強化されたアビリティ",
        ko="강화된 기술", zhCN="被强化的技能", zhTW="被強化的技能",
        ru="Усиленные способности", uk="Підсилені здібності", sv="Förstärkta förmågor",
        ar="القدرات المُعزَّزة", pl="Wzmocnione zdolności", tr="Güçlendirilen yetenekler",
    ),
    "stat_weapon_power": t(
        es="Potencia de arma", pt="Potência de arma", fr="Puissance d\\'arme",
        de="Waffenstärke", ja="武器パワー", ko="무기 위력", zhCN="武器强度",
        zhTW="武器強度", ru="Сила оружия", uk="Сила зброї", sv="Vapenstyrka",
        ar="قوة السلاح", pl="Moc broni", tr="Silah gücü",
    ),
    "stat_attack_speed": t(
        es="Velocidad de ataque", pt="Velocidade de ataque", fr="Vitesse d\\'attaque",
        de="Angriffstempo", ja="攻撃速度", ko="공격 속도", zhCN="攻击速度",
        zhTW="攻擊速度", ru="Скорость атаки", uk="Швидкість атаки",
        sv="Attackhastighet", ar="سرعة الهجوم", pl="Szybkość ataku", tr="Saldırı hızı",
    ),
    "stat_ability_power": t(
        es="Potencia de habilidad", pt="Potência de habilidade", fr="Puissance de capacité",
        de="Fähigkeitsstärke", ja="アビリティパワー", ko="기술 위력", zhCN="技能强度",
        zhTW="技能強度", ru="Сила способностей", uk="Сила здібностей",
        sv="Förmågestyrka", ar="قوة القدرات", pl="Moc zdolności", tr="Yetenek gücü",
    ),
    "stat_cooldown": t(
        es="Reducción de reutilización", pt="Redução de recarga",
        fr="Réduction de recharge", de="Abklingzeit-Reduktion", ja="クールダウン短縮",
        ko="재사용 대기시간 감소", zhCN="冷却缩减", zhTW="冷卻縮減",
        ru="Сокращение перезарядки", uk="Скорочення перезарядки",
        sv="Nedkylningsreduktion", ar="تقليل زمن الانتظار", pl="Skrócenie odnowienia",
        tr="Bekleme azaltma",
    ),
    "stat_move_speed": t(
        es="Velocidad de movimiento", pt="Velocidade de movimento",
        fr="Vitesse de déplacement", de="Bewegungstempo", ja="移動速度", ko="이동 속도",
        zhCN="移动速度", zhTW="移動速度", ru="Скорость передвижения",
        uk="Швидкість пересування", sv="Rörelsehastighet", ar="سرعة الحركة",
        pl="Szybkość ruchu", tr="Hareket hızı",
    ),
    "stat_weapon_lifesteal": t(
        es="Robo de vida con arma", pt="Roubo de vida com arma",
        fr="Vol de vie (arme)", de="Waffen-Lebensraub", ja="武器ライフスティール",
        ko="무기 생명력 흡수", zhCN="武器吸血", zhTW="武器吸血",
        ru="Вампиризм оружия", uk="Вампіризм зброї", sv="Vapenlivstöld",
        ar="امتصاص حياة السلاح", pl="Kradzież życia bronią", tr="Silah can çalma",
    ),
    "stat_ability_lifesteal": t(
        es="Robo de vida con habilidad", pt="Roubo de vida com habilidade",
        fr="Vol de vie (capacité)", de="Fähigkeits-Lebensraub",
        ja="アビリティライフスティール", ko="기술 생명력 흡수", zhCN="技能吸血",
        zhTW="技能吸血", ru="Вампиризм способностей", uk="Вампіризм здібностей",
        sv="Förmågelivstöld", ar="امتصاص حياة القدرات",
        pl="Kradzież życia zdolnością", tr="Yetenek can çalma",
    ),
    "stat_max_ammo": t(
        es="Munición máxima", pt="Munição máxima", fr="Munitions max", de="Max. Munition",
        ja="最大弾数", ko="최대 탄약", zhCN="最大弹药", zhTW="最大彈藥",
        ru="Макс. боезапас", uk="Макс. боєзапас", sv="Max ammunition",
        ar="أقصى ذخيرة", pl="Maks. amunicja", tr="Maks. cephane",
    ),

    # --- saved builds ---------------------------------------------------------------
    "build_saved": t(
        es="Builds guardadas", pt="Builds salvas", fr="Builds enregistrées",
        de="Gespeicherte Builds", ja="保存したビルド", ko="저장한 빌드",
        zhCN="已保存的配装", zhTW="已儲存的配裝", ru="Сохранённые сборки",
        uk="Збережені збірки", sv="Sparade builds", ar="التجهيزات المحفوظة",
        pl="Zapisane buildy", tr="Kayıtlı yapılar",
    ),
    "build_save": t(
        es="Guardar esta build", pt="Salvar esta build", fr="Enregistrer cette build",
        de="Diesen Build speichern", ja="このビルドを保存", ko="이 빌드 저장",
        zhCN="保存此配装", zhTW="儲存此配裝", ru="Сохранить сборку",
        uk="Зберегти збірку", sv="Spara denna build", ar="احفظ هذه التجهيزة",
        pl="Zapisz ten build", tr="Bu yapıyı kaydet",
    ),
    "build_name": t(
        es="Nombre de la build", pt="Nome da build", fr="Nom de la build",
        de="Name des Builds", ja="ビルド名", ko="빌드 이름", zhCN="配装名称",
        zhTW="配裝名稱", ru="Название сборки", uk="Назва збірки", sv="Buildens namn",
        ar="اسم التجهيزة", pl="Nazwa builda", tr="Yapı adı",
    ),
    "build_clone": t(
        es="Duplicar", pt="Duplicar", fr="Dupliquer", de="Duplizieren", ja="複製",
        ko="복제", zhCN="复制", zhTW="複製", ru="Дублировать", uk="Дублювати",
        sv="Duplicera", ar="تكرار", pl="Duplikuj", tr="Çoğalt",
    ),
    "build_delete": t(
        es="Eliminar", pt="Excluir", fr="Supprimer", de="Löschen", ja="削除", ko="삭제",
        zhCN="删除", zhTW="刪除", ru="Удалить", uk="Видалити", sv="Radera",
        ar="حذف", pl="Usuń", tr="Sil",
    ),
    "build_none": t(
        es="Todavía no hay builds guardadas para este héroe.",
        pt="Ainda não há builds salvas para este herói.",
        fr="Aucune build enregistrée pour ce héros.",
        de="Für diesen Helden sind noch keine Builds gespeichert.",
        ja="このヒーローの保存済みビルドはまだありません。",
        ko="이 영웅에 저장된 빌드가 아직 없습니다.",
        zhCN="这位英雄还没有保存的配装。",
        zhTW="這位英雄還沒有儲存的配裝。",
        ru="Для этого героя ещё нет сохранённых сборок.",
        uk="Для цього героя ще немає збережених збірок.",
        sv="Inga sparade builds för den här hjälten än.",
        ar="لا توجد تجهيزات محفوظة لهذا البطل بعد.",
        pl="Brak zapisanych buildów dla tego bohatera.",
        tr="Bu kahraman için henüz kayıtlı yapı yok.",
    ),
    "build_item_count": t(
        es="%1$d objetos", pt="%1$d itens", fr="%1$d objets", de="%1$d Gegenstände",
        ja="アイテム %1$d 個", ko="아이템 %1$d개", zhCN="%1$d 件物品",
        zhTW="%1$d 件物品", ru="предметов: %1$d", uk="предметів: %1$d",
        sv="%1$d föremål", ar="%1$d عنصر", pl="%1$d przedmiotów", tr="%1$d eşya",
    ),
    "build_cancel": t(
        es="Cancelar", pt="Cancelar", fr="Annuler", de="Abbrechen", ja="キャンセル",
        ko="취소", zhCN="取消", zhTW="取消", ru="Отмена", uk="Скасувати",
        sv="Avbryt", ar="إلغاء", pl="Anuluj", tr="İptal",
    ),
    "build_confirm": t(
        es="Guardar", pt="Salvar", fr="Enregistrer", de="Speichern", ja="保存",
        ko="저장", zhCN="保存", zhTW="儲存", ru="Сохранить", uk="Зберегти",
        sv="Spara", ar="حفظ", pl="Zapisz", tr="Kaydet",
    ),

    # --- about ----------------------------------------------------------------------
    "about_intro": t(
        es="Proyecto de fans, sin ánimo de lucro. No está afiliado a Blizzard Entertainment ni cuenta con su respaldo.",
        pt="Projeto de fãs, sem fins lucrativos. Não é afiliado nem endossado pela Blizzard Entertainment.",
        fr="Projet de fans non commercial. Ni affilié à Blizzard Entertainment, ni approuvé par elle.",
        de="Nicht kommerzielles Fanprojekt. Weder mit Blizzard Entertainment verbunden noch von ihnen unterstützt.",
        ja="非営利のファンプロジェクトです。Blizzard Entertainment とは無関係で、公認でもありません。",
        ko="비영리 팬 프로젝트입니다. Blizzard Entertainment와 제휴하거나 승인받지 않았습니다.",
        zhCN="非商业性质的粉丝项目，与暴雪娱乐无关，也未获其认可。",
        zhTW="非商業性質的粉絲專案，與暴雪娛樂無關，也未獲其認可。",
        ru="Некоммерческий фанатский проект. Не связан с Blizzard Entertainment и не одобрен ею.",
        uk="Некомерційний фанатський проєкт. Не пов\\'язаний із Blizzard Entertainment і не схвалений нею.",
        sv="Icke-kommersiellt fanprojekt. Varken knutet till eller godkänt av Blizzard Entertainment.",
        ar="مشروع من صنع المعجبين وغير تجاري. غير تابع لـBlizzard Entertainment ولا معتمد منها.",
        pl="Niekomercyjny projekt fanowski. Niepowiązany z Blizzard Entertainment ani przez nią niewspierany.",
        tr="Ticari olmayan bir hayran projesi. Blizzard Entertainment ile bağlantılı veya onaylı değildir.",
    ),
    "about_data_title": t(
        es="De dónde vienen los datos", pt="De onde vêm os dados",
        fr="D\\'où viennent les données", de="Woher die Daten stammen",
        ja="データの出典", ko="데이터 출처", zhCN="数据来源", zhTW="資料來源",
        ru="Откуда данные", uk="Звідки дані", sv="Var data kommer ifrån",
        ar="من أين تأتي البيانات", pl="Skąd pochodzą dane", tr="Veriler nereden geliyor",
    ),
    "about_art_title": t(
        es="Imágenes y nombres", pt="Imagens e nomes", fr="Images et noms",
        de="Bilder und Namen", ja="画像と名称", ko="이미지와 이름", zhCN="图像与名称",
        zhTW="圖像與名稱", ru="Изображения и названия", uk="Зображення та назви",
        sv="Bilder och namn", ar="الصور والأسماء", pl="Grafiki i nazwy",
        tr="Görseller ve isimler",
    ),
    "about_fonts_title": t(
        es="Tipografías", pt="Tipografias", fr="Polices", de="Schriftarten",
        ja="書体", ko="글꼴", zhCN="字体", zhTW="字型", ru="Шрифты", uk="Шрифти",
        sv="Typsnitt", ar="الخطوط", pl="Kroje pisma", tr="Yazı tipleri",
    ),
    "about_dataset_title": t(
        es="Datos", pt="Dados", fr="Données", de="Datensatz", ja="データセット",
        ko="데이터", zhCN="数据集", zhTW="資料集", ru="Набор данных", uk="Набір даних",
        sv="Datamängd", ar="مجموعة البيانات", pl="Zestaw danych", tr="Veri kümesi",
    ),
    "about_dataset_version": t(
        es="Versión %1$d · %2$d héroes · %3$d armas",
        pt="Versão %1$d · %2$d heróis · %3$d armas",
        fr="Version %1$d · %2$d héros · %3$d armes",
        de="Version %1$d · %2$d Helden · %3$d Waffen",
        ja="バージョン %1$d · ヒーロー %2$d · 武器 %3$d",
        ko="버전 %1$d · 영웅 %2$d · 무기 %3$d",
        zhCN="版本 %1$d · %2$d 位英雄 · %3$d 件武器",
        zhTW="版本 %1$d · %2$d 位英雄 · %3$d 件武器",
        ru="Версия %1$d · героев: %2$d · оружия: %3$d",
        uk="Версія %1$d · героїв: %2$d · зброї: %3$d",
        sv="Version %1$d · %2$d hjältar · %3$d vapen",
        ar="الإصدار %1$d · %2$d بطل · %3$d سلاح",
        pl="Wersja %1$d · %2$d bohaterów · %3$d broni",
        tr="Sürüm %1$d · %2$d kahraman · %3$d silah",
    ),
    "about_check_updates": t(
        es="Buscar datos más recientes", pt="Procurar dados mais recentes",
        fr="Chercher des données plus récentes", de="Nach neueren Daten suchen",
        ja="新しいデータを確認", ko="최신 데이터 확인", zhCN="检查是否有更新的数据",
        zhTW="檢查是否有更新的資料", ru="Проверить обновление данных",
        uk="Перевірити оновлення даних", sv="Sök efter nyare data",
        ar="ابحث عن بيانات أحدث", pl="Sprawdź nowsze dane", tr="Daha yeni veri ara",
    ),
    "about_checking": t(
        es="Comprobando…", pt="Verificando…", fr="Vérification…", de="Prüfe…",
        ja="確認中…", ko="확인 중…", zhCN="检查中…", zhTW="檢查中…", ru="Проверка…",
        uk="Перевірка…", sv="Kontrollerar…", ar="جارٍ التحقق…", pl="Sprawdzanie…",
        tr="Kontrol ediliyor…",
    ),
    "about_up_to_date": t(
        es="Los datos incluidos son los más recientes.",
        pt="Os dados incluídos são os mais recentes.",
        fr="Les données incluses sont les plus récentes.",
        de="Die enthaltenen Daten sind die neuesten.",
        ja="同梱のデータが最新です。",
        ko="포함된 데이터가 최신입니다.",
        zhCN="内置数据已是最新。",
        zhTW="內建資料已是最新。",
        ru="Встроенные данные — самые свежие.",
        uk="Вбудовані дані — найсвіжіші.",
        sv="Den medföljande datan är den nyaste.",
        ar="البيانات المضمّنة هي الأحدث.",
        pl="Dołączone dane są najnowsze.",
        tr="Uygulamadaki veriler en güncel olanlar.",
    ),
    "about_update_failed": t(
        es="No se pudo contactar con el servidor. La app funciona igualmente sin conexión.",
        pt="Não foi possível contatar o servidor. O app funciona mesmo offline.",
        fr="Serveur injoignable. L\\'application fonctionne hors ligne de toute façon.",
        de="Server nicht erreichbar. Die App funktioniert ohnehin offline.",
        ja="サーバーに接続できませんでした。オフラインでも問題なく動作します。",
        ko="서버에 연결하지 못했습니다. 오프라인에서도 정상 작동합니다.",
        zhCN="无法连接服务器。应用在离线状态下同样可用。",
        zhTW="無法連線伺服器。應用程式在離線狀態下同樣可用。",
        ru="Сервер недоступен. Приложение всё равно работает офлайн.",
        uk="Сервер недоступний. Застосунок усе одно працює офлайн.",
        sv="Servern kunde inte nås. Appen fungerar ändå offline.",
        ar="تعذّر الوصول إلى الخادم. التطبيق يعمل دون اتصال على أي حال.",
        pl="Nie udało się połączyć z serwerem. Aplikacja i tak działa offline.",
        tr="Sunucuya ulaşılamadı. Uygulama zaten çevrimdışı çalışır.",
    ),
    "about_updates_unconfigured": t(
        es="Esta versión no tiene servidor de actualizaciones configurado.",
        pt="Esta versão não tem servidor de atualizações configurado.",
        fr="Aucun serveur de mise à jour n\\'est configuré pour cette version.",
        de="Für diese Version ist kein Update-Server eingerichtet.",
        ja="このビルドには更新サーバーが設定されていません。",
        ko="이 빌드에는 업데이트 서버가 설정되어 있지 않습니다.",
        zhCN="此版本未配置更新服务器。",
        zhTW="此版本未設定更新伺服器。",
        ru="Для этой сборки сервер обновлений не настроен.",
        uk="Для цієї збірки сервер оновлень не налаштовано.",
        sv="Ingen uppdateringsserver är konfigurerad för den här versionen.",
        ar="لا يوجد خادم تحديثات مُهيّأ لهذه النسخة.",
        pl="Ta wersja nie ma skonfigurowanego serwera aktualizacji.",
        tr="Bu sürüm için güncelleme sunucusu yapılandırılmamış.",
    ),
    # --- live meta (Blizzard's published rates) ---------------------------------------
    "tab_meta": t(
        es="Meta", pt="Meta", fr="Méta", de="Meta", ja="環境", ko="메타",
        zhCN="版本", zhTW="版本", ru="Мета", uk="Мета", sv="Meta",
        ar="الميتا", pl="Meta", tr="Meta",
    ),
    "meta_title": t(
        es="Meta actual", pt="Meta atual", fr="Méta actuelle", de="Aktuelle Meta",
        ja="現在の環境", ko="현재 메타", zhCN="当前环境", zhTW="當前環境",
        ru="Текущая мета", uk="Поточна мета", sv="Aktuell meta", ar="الميتا الحالية",
        pl="Aktualna meta", tr="Güncel meta",
    ),
    "meta_sort": t(
        es="Ordenar por", pt="Ordenar por", fr="Classer par", de="Sortieren nach",
        ja="並び替え", ko="정렬 기준", zhCN="排序依据", zhTW="排序依據",
        ru="Сортировать по", uk="Сортувати за", sv="Sortera efter", ar="الترتيب حسب",
        pl="Sortuj wg", tr="Sırala",
    ),
    "meta_sort_ban": t(
        es="Más baneados", pt="Mais banidos", fr="Plus bannis", de="Meist gebannt",
        ja="BAN率順", ko="밴율순", zhCN="禁用率", zhTW="禁用率",
        ru="Чаще банят", uk="Частіше банять", sv="Mest bannade", ar="الأكثر حظرًا",
        pl="Najczęściej banowani", tr="En çok banlanan",
    ),
    "meta_sort_pick": t(
        es="Más elegidos", pt="Mais escolhidos", fr="Plus choisis", de="Meist gewählt",
        ja="ピック率順", ko="픽률순", zhCN="选取率", zhTW="選取率",
        ru="Чаще берут", uk="Частіше беруть", sv="Mest valda", ar="الأكثر اختيارًا",
        pl="Najczęściej wybierani", tr="En çok seçilen",
    ),
    "meta_sort_win": t(
        es="Más victorias", pt="Mais vitórias", fr="Meilleur taux de victoire",
        de="Höchste Siegrate", ja="勝率順", ko="승률순", zhCN="胜率", zhTW="勝率",
        ru="Выше винрейт", uk="Вищий вінрейт", sv="Högst vinstprocent",
        ar="الأعلى فوزًا", pl="Najwyższa wygrywalność", tr="En yüksek kazanma",
    ),
    "meta_queue": t(
        es="Cola", pt="Fila", fr="File", de="Warteschlange", ja="キュー", ko="대기열",
        zhCN="队列", zhTW="佇列", ru="Очередь", uk="Черга", sv="Kö",
        ar="الطابور", pl="Kolejka", tr="Sıra",
    ),
    "meta_queue_comp": t(
        es="Competitivo", pt="Competitivo", fr="Compétitif", de="Gewertet",
        ja="コンペティティブ", ko="경쟁전", zhCN="竞技比赛", zhTW="競技比賽",
        ru="Соревновательный", uk="Змагальний", sv="Rankad", ar="تنافسي",
        pl="Rankingowe", tr="Rekabetçi",
    ),
    "meta_queue_qp": t(
        es="Partida rápida", pt="Partida rápida", fr="Partie rapide", de="Schnellsuche",
        ja="クイックプレイ", ko="빠른 대전", zhCN="快速比赛", zhTW="快速比賽",
        ru="Быстрая игра", uk="Швидка гра", sv="Snabbmatch", ar="لعب سريع",
        pl="Szybka gra", tr="Hızlı oyun",
    ),
    "meta_tier": t(
        es="Rango", pt="Elo", fr="Rang", de="Rang", ja="ランク", ko="티어",
        zhCN="段位", zhTW="段位", ru="Ранг", uk="Ранг", sv="Rank",
        ar="الرتبة", pl="Ranga", tr="Rütbe",
    ),
    "meta_tier_all": t(
        es="Todos los rangos", pt="Todos os elos", fr="Tous les rangs", de="Alle Ränge",
        ja="全ランク", ko="전체 티어", zhCN="全部段位", zhTW="全部段位",
        ru="Все ранги", uk="Усі ранги", sv="Alla rankar", ar="كل الرتب",
        pl="Wszystkie rangi", tr="Tüm rütbeler",
    ),
    "meta_tier_bronze": t(
        es="Bronce", pt="Bronze", fr="Bronze", de="Bronze", ja="ブロンズ", ko="브론즈",
        zhCN="青铜", zhTW="青銅", ru="Бронза", uk="Бронза", sv="Brons",
        ar="برونزي", pl="Brąz", tr="Bronz",
    ),
    "meta_tier_silver": t(
        es="Plata", pt="Prata", fr="Argent", de="Silber", ja="シルバー", ko="실버",
        zhCN="白银", zhTW="白銀", ru="Серебро", uk="Срібло", sv="Silver",
        ar="فضي", pl="Srebro", tr="Gümüş",
    ),
    "meta_tier_gold": t(
        es="Oro", pt="Ouro", fr="Or", de="Gold", ja="ゴールド", ko="골드",
        zhCN="黄金", zhTW="黃金", ru="Золото", uk="Золото", sv="Guld",
        ar="ذهبي", pl="Złoto", tr="Altın",
    ),
    "meta_tier_platinum": t(
        es="Platino", pt="Platina", fr="Platine", de="Platin", ja="プラチナ", ko="플래티넘",
        zhCN="白金", zhTW="白金", ru="Платина", uk="Платина", sv="Platina",
        ar="بلاتيني", pl="Platyna", tr="Platin",
    ),
    "meta_tier_diamond": t(
        es="Diamante", pt="Diamante", fr="Diamant", de="Diamant", ja="ダイヤモンド",
        ko="다이아몬드", zhCN="钻石", zhTW="鑽石", ru="Алмаз", uk="Алмаз",
        sv="Diamant", ar="ماسي", pl="Diament", tr="Elmas",
    ),
    "meta_tier_master": t(
        es="Maestro", pt="Mestre", fr="Maître", de="Meister", ja="マスター", ko="마스터",
        zhCN="大师", zhTW="大師", ru="Мастер", uk="Майстер", sv="Mästare",
        ar="أستاذ", pl="Mistrz", tr="Usta",
    ),
    "meta_tier_grandmaster": t(
        es="GM y Campeón", pt="GM e Campeão", fr="GM et Champion", de="GM und Champion",
        ja="GM・チャンピオン", ko="그랜드마스터·챔피언", zhCN="宗师与冠军",
        zhTW="宗師與冠軍", ru="ГМ и Чемпион", uk="ГМ і Чемпіон", sv="GM och Champion",
        ar="جراند ماستر وبطل", pl="GM i Mistrz Świata", tr="GM ve Şampiyon",
    ),
    "meta_region": t(
        es="Región y control", pt="Região e controle", fr="Région et contrôle",
        de="Region und Eingabe", ja="地域と入力方式", ko="지역 및 입력 방식",
        zhCN="地区与操作方式", zhTW="地區與操作方式", ru="Регион и управление",
        uk="Регіон і керування", sv="Region och styrning", ar="المنطقة وطريقة التحكم",
        pl="Region i sterowanie", tr="Bölge ve giriş",
    ),
    "meta_region_europe": t(
        es="Europa", pt="Europa", fr="Europe", de="Europa", ja="ヨーロッパ", ko="유럽",
        zhCN="欧洲", zhTW="歐洲", ru="Европа", uk="Європа", sv="Europa",
        ar="أوروبا", pl="Europa", tr="Avrupa",
    ),
    "meta_region_americas": t(
        es="América", pt="Américas", fr="Amériques", de="Amerika", ja="アメリカ",
        ko="아메리카", zhCN="美洲", zhTW="美洲", ru="Америка", uk="Америка",
        sv="Amerika", ar="الأمريكتان", pl="Ameryki", tr="Amerika",
    ),
    "meta_region_asia": t(
        es="Asia", pt="Ásia", fr="Asie", de="Asien", ja="アジア", ko="아시아",
        zhCN="亚洲", zhTW="亞洲", ru="Азия", uk="Азія", sv="Asien",
        ar="آسيا", pl="Azja", tr="Asya",
    ),
    "meta_input_pc": t(
        es="Ratón y teclado", pt="Mouse e teclado", fr="Souris et clavier",
        de="Maus und Tastatur", ja="マウス＆キーボード", ko="마우스·키보드",
        zhCN="键鼠", zhTW="鍵鼠", ru="Мышь и клавиатура", uk="Миша й клавіатура",
        sv="Mus och tangentbord", ar="فأرة ولوحة مفاتيح", pl="Mysz i klawiatura",
        tr="Fare ve klavye",
    ),
    "meta_input_console": t(
        es="Mando", pt="Controle", fr="Manette", de="Controller", ja="コントローラー",
        ko="컨트롤러", zhCN="手柄", zhTW="手把", ru="Геймпад", uk="Геймпад",
        sv="Handkontroll", ar="ذراع تحكم", pl="Pad", tr="Oyun kumandası",
    ),
    "meta_role": t(
        es="Rol", pt="Função", fr="Rôle", de="Rolle", ja="ロール", ko="역할",
        zhCN="定位", zhTW="定位", ru="Роль", uk="Роль", sv="Roll",
        ar="الدور", pl="Rola", tr="Rol",
    ),
    "meta_role_all": t(
        es="Todos los roles", pt="Todas as funções", fr="Tous les rôles", de="Alle Rollen",
        ja="全ロール", ko="전체 역할", zhCN="全部定位", zhTW="全部定位",
        ru="Все роли", uk="Усі ролі", sv="Alla roller", ar="كل الأدوار",
        pl="Wszystkie role", tr="Tüm roller",
    ),
    "meta_row_detail": t(
        es="Baneo %1$.1f%%  ·  Elección %2$.1f%%  ·  Victoria %3$.1f%%",
        pt="Banimento %1$.1f%%  ·  Escolha %2$.1f%%  ·  Vitória %3$.1f%%",
        fr="Ban %1$.1f%%  ·  Choix %2$.1f%%  ·  Victoire %3$.1f%%",
        de="Bann %1$.1f%%  ·  Wahl %2$.1f%%  ·  Sieg %3$.1f%%",
        ja="BAN %1$.1f%%  ·  ピック %2$.1f%%  ·  勝率 %3$.1f%%",
        ko="밴 %1$.1f%%  ·  픽 %2$.1f%%  ·  승률 %3$.1f%%",
        zhCN="禁用 %1$.1f%%  ·  选取 %2$.1f%%  ·  胜率 %3$.1f%%",
        zhTW="禁用 %1$.1f%%  ·  選取 %2$.1f%%  ·  勝率 %3$.1f%%",
        ru="Бан %1$.1f%%  ·  Пик %2$.1f%%  ·  Победы %3$.1f%%",
        uk="Бан %1$.1f%%  ·  Пік %2$.1f%%  ·  Перемоги %3$.1f%%",
        sv="Ban %1$.1f%%  ·  Val %2$.1f%%  ·  Vinst %3$.1f%%",
        ar="حظر %1$.1f%%  ·  اختيار %2$.1f%%  ·  فوز %3$.1f%%",
        pl="Ban %1$.1f%%  ·  Wybór %2$.1f%%  ·  Wygrane %3$.1f%%",
        tr="Ban %1$.1f%%  ·  Seçim %2$.1f%%  ·  Kazanma %3$.1f%%",
    ),
    "meta_offline": t(
        es="Esta es la única pantalla que necesita conexión. El resto de la app funciona sin ella porque los números de un arma y un parche que ya salió no cambian; qué héroes se banean cambia cada semana, así que se descarga en vez de viajar dentro de la app.",
        pt="Esta é a única tela que precisa de conexão. O resto do app funciona sem ela porque os números de uma arma e um patch que já saiu não mudam; quais heróis são banidos muda toda semana, então isso é baixado em vez de embarcado.",
        fr="C\'est le seul écran qui a besoin d\'une connexion. Le reste de l\'appli fonctionne sans, parce que les chiffres d\'une arme et un correctif déjà sorti ne changent pas ; les héros bannis, eux, changent chaque semaine, donc ces données sont téléchargées plutôt qu\'embarquées.",
        de="Dies ist der einzige Bildschirm, der eine Verbindung braucht. Der Rest der App kommt ohne aus, denn die Werte einer Waffe und ein bereits erschienener Patch ändern sich nicht; welche Helden gebannt werden, ändert sich wöchentlich, also wird das geladen statt mitgeliefert.",
        ja="接続が必要なのはこの画面だけです。武器の数値やすでに適用されたパッチは変わらないため、他の画面はオフラインで動きます。しかしBANされるヒーローは毎週変わるので、この情報だけは内蔵せずに取得しています。",
        ko="연결이 필요한 화면은 여기뿐입니다. 무기 수치와 이미 적용된 패치는 변하지 않으므로 나머지 화면은 오프라인으로 동작합니다. 하지만 밴되는 영웅은 매주 바뀌므로 이 정보만은 내장하지 않고 받아옵니다.",
        zhCN="这是唯一需要联网的页面。武器数值和已经发布的补丁不会改变，所以其他页面都能离线使用；但被禁用的英雄每周都在变，因此这部分是下载而非内置的。",
        zhTW="這是唯一需要連線的頁面。武器數值和已經發布的更新不會改變，所以其他頁面都能離線使用；但被禁用的英雄每週都在變，因此這部分是下載而非內建的。",
        ru="Это единственный экран, которому нужна сеть. Остальное приложение работает без неё: числа оружия и уже вышедший патч не меняются, а вот то, каких героев банят, меняется каждую неделю, поэтому эти данные загружаются, а не лежат внутри.",
        uk="Це єдиний екран, якому потрібна мережа. Решта застосунку працює без неї: числа зброї та вже випущений патч не змінюються, а от те, яких героїв банять, змінюється щотижня, тож ці дані завантажуються, а не лежать усередині.",
        sv="Det här är den enda skärmen som behöver uppkoppling. Resten av appen klarar sig utan, eftersom ett vapens siffror och en redan släppt patch inte ändras; vilka hjältar som bannas ändras varje vecka, så det hämtas i stället för att följa med.",
        ar="هذه هي الشاشة الوحيدة التي تحتاج إلى اتصال. باقي التطبيق يعمل بدونه لأن أرقام السلاح والتحديث الذي صدر بالفعل لا تتغير، أما الأبطال الذين يُحظرون فيتغيرون كل أسبوع، لذا تُجلب هذه البيانات بدل تضمينها.",
        pl="To jedyny ekran, który potrzebuje połączenia. Reszta aplikacji działa bez niego, bo liczby broni i wydana już łatka się nie zmieniają; to, których bohaterów się banuje, zmienia się co tydzień, więc te dane są pobierane, a nie zaszyte.",
        tr="Bağlantı gerektiren tek ekran budur. Uygulamanın geri kalanı bağlantısız çalışır, çünkü bir silahın sayıları ve çıkmış bir yama değişmez; hangi kahramanların banlandığı ise her hafta değişir, bu yüzden bu veri gömülmek yerine indirilir.",
    ),
    "meta_retry": t(
        es="Reintentar", pt="Tentar de novo", fr="Réessayer", de="Erneut versuchen",
        ja="再試行", ko="다시 시도", zhCN="重试", zhTW="重試",
        ru="Повторить", uk="Повторити", sv="Försök igen", ar="أعد المحاولة",
        pl="Spróbuj ponownie", tr="Yeniden dene",
    ),
    "meta_open_source": t(
        es="Abrir la página oficial", pt="Abrir a página oficial",
        fr="Ouvrir la page officielle", de="Offizielle Seite öffnen",
        ja="公式ページを開く", ko="공식 페이지 열기", zhCN="打开官方页面",
        zhTW="開啟官方頁面", ru="Открыть официальную страницу",
        uk="Відкрити офіційну сторінку", sv="Öppna den officiella sidan",
        ar="افتح الصفحة الرسمية", pl="Otwórz oficjalną stronę", tr="Resmî sayfayı aç",
    ),
    "meta_credit": t(
        es="Cifras publicadas por Blizzard en overwatch.blizzard.com. Solo el competitivo tiene fase de baneo, así que en partida rápida el baneo marca cero. Nada de esto se guarda en tu teléfono.",
        pt="Números publicados pela Blizzard em overwatch.blizzard.com. Só o competitivo tem fase de banimento, então na partida rápida o banimento fica em zero. Nada disso é salvo no seu telefone.",
        fr="Chiffres publiés par Blizzard sur overwatch.blizzard.com. Seul le compétitif a une phase de bannissement : en partie rapide, le taux de ban est donc à zéro. Rien de tout cela n\'est enregistré sur votre téléphone.",
        de="Zahlen von Blizzard auf overwatch.blizzard.com. Nur der gewertete Modus hat eine Bannphase, in der Schnellsuche steht die Bannrate deshalb auf null. Nichts davon wird auf dem Gerät gespeichert.",
        ja="数値は Blizzard が overwatch.blizzard.com で公開しているものです。BANフェーズがあるのはコンペティティブのみなので、クイックプレイではBAN率はゼロになります。これらの情報は端末に保存されません。",
        ko="수치는 Blizzard가 overwatch.blizzard.com에 공개한 자료입니다. 밴 단계가 있는 모드는 경쟁전뿐이므로 빠른 대전에서는 밴율이 0으로 표시됩니다. 이 정보는 기기에 저장되지 않습니다.",
        zhCN="数据由暴雪发布于 overwatch.blizzard.com。只有竞技比赛有禁用环节，因此快速比赛中禁用率为零。这些内容不会保存到你的手机上。",
        zhTW="數據由暴雪發布於 overwatch.blizzard.com。只有競技比賽有禁用環節，因此快速比賽中禁用率為零。這些內容不會儲存到你的手機上。",
        ru="Цифры опубликованы Blizzard на overwatch.blizzard.com. Фаза банов есть только в соревновательном режиме, поэтому в быстрой игре бан-рейт нулевой. Ничего из этого не сохраняется на телефоне.",
        uk="Цифри опубліковані Blizzard на overwatch.blizzard.com. Фаза банів є лише у змагальному режимі, тому у швидкій грі бан-рейт нульовий. Нічого з цього не зберігається на телефоні.",
        sv="Siffror publicerade av Blizzard på overwatch.blizzard.com. Bara rankat har en banfas, så i snabbmatch står banprocenten på noll. Inget av detta sparas på telefonen.",
        ar="أرقام تنشرها Blizzard على overwatch.blizzard.com. مرحلة الحظر موجودة في الوضع التنافسي فقط، لذا تكون نسبة الحظر صفرًا في اللعب السريع. لا يُحفظ أي من هذا على هاتفك.",
        pl="Liczby publikowane przez Blizzard na overwatch.blizzard.com. Tylko tryb rankingowy ma fazę banów, więc w szybkiej grze ban wynosi zero. Nic z tego nie jest zapisywane na telefonie.",
        tr="Rakamlar Blizzard tarafından overwatch.blizzard.com adresinde yayımlanır. Ban aşaması yalnızca rekabetçi modda vardır, bu yüzden hızlı oyunda ban oranı sıfır görünür. Bunların hiçbiri telefonunuza kaydedilmez.",
    ),
    # --- match-ups -------------------------------------------------------------------
    "wiki_matchups": t(
        es="Enfrentamientos", pt="Confrontos", fr="Duels", de="Duelle",
        ja="対面相性", ko="상성", zhCN="对位", zhTW="對位",
        ru="Противостояния", uk="Протистояння", sv="Matchups", ar="المواجهات",
        pl="Starcia", tr="Eşleşmeler",
    ),
    "wiki_matchups_note": t(
        es="Escrito por la wiki desde la perspectiva de quien juega con este héroe. Donde un editor calificó el enfrentamiento, se muestra su redacción tal cual; las filas sin calificación nunca la recibieron, y no se ha deducido ningún veredicto para ellas. Toca un retrato para abrir ese héroe.",
        pt="Escrito pela wiki na perspectiva de quem joga com este herói. Onde um editor classificou o confronto, o texto dele aparece como está; as linhas sem classificação nunca receberam uma, e nenhum veredito foi deduzido para elas. Toque em um retrato para abrir aquele herói.",
        fr="Rédigé par le wiki du point de vue de celui qui joue ce héros. Quand un contributeur a noté le duel, sa formulation est reprise telle quelle ; les lignes sans note n\'en ont jamais reçu, et aucun verdict n\'a été déduit pour elles. Touchez un portrait pour ouvrir ce héros.",
        de="Vom Wiki aus der Sicht dessen geschrieben, der diesen Helden spielt. Wo jemand das Duell bewertet hat, steht seine Formulierung unverändert da; Zeilen ohne Bewertung haben nie eine bekommen, und es wurde kein Urteil für sie abgeleitet. Tippe auf ein Porträt, um den Helden zu öffnen.",
        ja="このヒーローを使う側の視点でウィキが書いたものです。編集者が相性を評価している場合はその表現をそのまま表示しています。評価のない行はもともと評価されていないだけで、こちらで判定を推測してはいません。ポートレートをタップするとそのヒーローを開きます。",
        ko="이 영웅을 플레이하는 입장에서 위키가 작성한 내용입니다. 편집자가 상성을 평가한 경우 그 표현을 그대로 보여줍니다. 평가가 없는 항목은 애초에 평가되지 않은 것이며, 임의로 판정을 추론하지 않았습니다. 초상화를 누르면 해당 영웅이 열립니다.",
        zhCN="由维基以使用该英雄的视角撰写。编辑给出评级的对位，原文照录；没有评级的条目本就没有被评过，我们也没有代为推断结论。点击头像可打开该英雄。",
        zhTW="由維基以使用該英雄的視角撰寫。編輯給出評級的對位，原文照錄；沒有評級的項目本來就沒有被評過，我們也沒有代為推斷結論。點擊頭像可開啟該英雄。",
        ru="Написано вики от лица того, кто играет за этого героя. Там, где редактор оценил противостояние, его формулировка приведена дословно; строки без оценки её просто не получили, и вердикт за них не додумывался. Нажмите на портрет, чтобы открыть героя.",
        uk="Написано вікі від імені того, хто грає за цього героя. Там, де редактор оцінив протистояння, його формулювання наведено дослівно; рядки без оцінки її просто не отримали, і вердикт за них не домислювався. Натисніть на портрет, щоб відкрити героя.",
        sv="Skrivet av wikin ur perspektivet från den som spelar hjälten. Där en redaktör har betygsatt matchupen visas deras formulering ordagrant; rader utan betyg har aldrig fått något, och ingen dom har härletts åt dem. Tryck på ett porträtt för att öppna den hjälten.",
        ar="كُتب في الويكي من منظور من يلعب بهذا البطل. حيث قيّم أحد المحررين المواجهة تُعرض صياغته كما هي؛ أما الصفوف بلا تقييم فلم تُقيَّم أصلًا، ولم يُستنتج لها حكم. اضغط على صورة لفتح صفحة ذلك البطل.",
        pl="Napisane przez wiki z perspektywy grającego tym bohaterem. Tam, gdzie redaktor ocenił starcie, jego sformułowanie podano dosłownie; wiersze bez oceny nigdy jej nie dostały i nie wywnioskowano dla nich werdyktu. Dotknij portretu, aby otworzyć tego bohatera.",
        tr="Bu kahramanı oynayan kişinin bakış açısıyla wiki tarafından yazılmıştır. Bir editörün eşleşmeyi derecelendirdiği yerlerde ifadesi olduğu gibi gösterilir; derecesi olmayan satırlar hiç derecelendirilmemiştir ve onlar için bir hüküm çıkarılmamıştır. Bir portreye dokunarak o kahramanı açın.",
    ),
    "wiki_matchup_risk": t(
        es="riesgo %1$s", pt="risco %1$s", fr="risque %1$s", de="%1$s Risiko",
        ja="リスク %1$s", ko="위험도 %1$s", zhCN="%1$s 风险", zhTW="%1$s 風險",
        ru="риск %1$s", uk="ризик %1$s", sv="%1$s risk", ar="خطر %1$s",
        pl="ryzyko %1$s", tr="%1$s risk",
    ),
    "wiki_matchup_synergy": t(
        es="En tu equipo", pt="No seu time", fr="Dans votre équipe", de="Im eigenen Team",
        ja="味方にいる場合", ko="아군일 때", zhCN="在你的队伍中", zhTW="在你的隊伍中",
        ru="В вашей команде", uk="У вашій команді", sv="I ditt lag",
        ar="في فريقك", pl="W twojej drużynie", tr="Takımındayken",
    ),
    # --- update banner ----------------------------------------------------------------
    "update_available": t(
        es="La versión %1$s ya está disponible", pt="A versão %1$s já saiu",
        fr="La version %1$s est sortie", de="Version %1$s ist da",
        ja="バージョン %1$s が公開されました", ko="버전 %1$s이(가) 나왔습니다",
        zhCN="版本 %1$s 已发布", zhTW="版本 %1$s 已發布",
        ru="Вышла версия %1$s", uk="Вийшла версія %1$s",
        sv="Version %1$s har släppts", ar="صدر الإصدار %1$s",
        pl="Jest już wersja %1$s", tr="Sürüm %1$s çıktı",
    ),
    "update_open": t(
        es="Ver novedades", pt="Ver novidades", fr="Voir les nouveautés",
        de="Was ist neu", ja="変更点を見る", ko="변경 사항 보기",
        zhCN="查看更新内容", zhTW="查看更新內容", ru="Что изменилось",
        uk="Що змінилося", sv="Se vad som ändrats", ar="ما الجديد",
        pl="Zobacz zmiany", tr="Neler değişti",
    ),
    "update_dismiss": t(
        es="No volver a avisar de esta versión",
        pt="Não avisar mais sobre esta versão",
        fr="Ne plus signaler cette version",
        de="Diese Version nicht mehr erwähnen",
        ja="このバージョンについて今後知らせない",
        ko="이 버전은 다시 알리지 않기",
        zhCN="不再提示此版本", zhTW="不再提示此版本",
        ru="Больше не напоминать об этой версии",
        uk="Більше не нагадувати про цю версію",
        sv="Nämn inte den här versionen igen",
        ar="لا تذكر هذا الإصدار مرة أخرى",
        pl="Nie przypominaj o tej wersji",
        tr="Bu sürümü bir daha hatırlatma",
    ),
    # --- fire mode and perks ----------------------------------------------------------
    "chart_section_fire_mode": t(
        es="Modo de disparo", pt="Modo de disparo", fr="Mode de tir", de="Feuermodus",
        ja="射撃モード", ko="사격 방식", zhCN="开火方式", zhTW="開火方式",
        ru="Режим огня", uk="Режим вогню", sv="Eldläge", ar="نمط إطلاق النار",
        pl="Tryb ognia", tr="Ateş modu",
    ),
    "fire_primary": t(
        es="Primario", pt="Primário", fr="Principal", de="Primär",
        ja="通常攻撃", ko="기본 공격", zhCN="主要攻击", zhTW="主要攻擊",
        ru="Основной", uk="Основний", sv="Primär", ar="أساسي",
        pl="Podstawowy", tr="Birincil",
    ),
    "fire_secondary": t(
        es="Secundario", pt="Secundário", fr="Secondaire", de="Sekundär",
        ja="サブ攻撃", ko="보조 공격", zhCN="次要攻击", zhTW="次要攻擊",
        ru="Альтернативный", uk="Альтернативний", sv="Sekundär", ar="ثانوي",
        pl="Dodatkowy", tr="İkincil",
    ),
    "chart_with_perk": t(
        es="con %1$s", pt="com %1$s", fr="avec %1$s", de="mit %1$s",
        ja="%1$s あり", ko="%1$s 적용", zhCN="搭配 %1$s", zhTW="搭配 %1$s",
        ru="с %1$s", uk="з %1$s", sv="med %1$s", ar="مع %1$s",
        pl="z %1$s", tr="%1$s ile",
    ),
    # --- map filter and player profile ------------------------------------------------
    "meta_map": t(
        es="Mapa", pt="Mapa", fr="Carte", de="Karte", ja="マップ", ko="맵",
        zhCN="地图", zhTW="地圖", ru="Карта", uk="Карта", sv="Karta",
        ar="الخريطة", pl="Mapa", tr="Harita",
    ),
    "meta_map_all": t(
        es="Todos los mapas", pt="Todos os mapas", fr="Toutes les cartes",
        de="Alle Karten", ja="全マップ", ko="전체 맵", zhCN="全部地图", zhTW="全部地圖",
        ru="Все карты", uk="Усі карти", sv="Alla kartor", ar="كل الخرائط",
        pl="Wszystkie mapy", tr="Tüm haritalar",
    ),
    "meta_map_note": t(
        es="Acotado a un mapa, estos son los héroes que la gente elige ahí.",
        pt="Restrito a um mapa, estes são os heróis que as pessoas escolhem ali.",
        fr="Limité à une carte, voici les héros que les gens y choisissent.",
        de="Auf eine Karte begrenzt: die Helden, die dort tatsächlich gewählt werden.",
        ja="マップを絞ると、そこで実際に選ばれているヒーローが分かります。",
        ko="맵을 한정하면 그곳에서 실제로 선택되는 영웅이 보입니다.",
        zhCN="限定到一张地图后，这些就是玩家在那里实际选择的英雄。",
        zhTW="限定到一張地圖後，這些就是玩家在那裡實際選擇的英雄。",
        ru="С фильтром по карте это герои, которых там действительно берут.",
        uk="З фільтром за картою це герої, яких там справді беруть.",
        sv="Begränsat till en karta: hjältarna folk faktiskt väljer där.",
        ar="عند التحديد بخريطة واحدة، هؤلاء هم الأبطال الذين يختارهم الناس فيها.",
        pl="Zawężone do jednej mapy: bohaterowie, których ludzie tam naprawdę wybierają.",
        tr="Tek haritaya daraltıldığında, orada gerçekten seçilen kahramanlar.",
    ),
    "meta_tab_everyone": t(
        es="Todos", pt="Todos", fr="Tout le monde", de="Alle", ja="全体", ko="전체",
        zhCN="所有人", zhTW="所有人", ru="Все", uk="Усі", sv="Alla",
        ar="الجميع", pl="Wszyscy", tr="Herkes",
    ),
    "meta_tab_you": t(
        es="Tú", pt="Você", fr="Vous", de="Du", ja="自分", ko="나",
        zhCN="你", zhTW="你", ru="Вы", uk="Ви", sv="Du",
        ar="أنت", pl="Ty", tr="Sen",
    ),
    "player_intro": t(
        es="Tu carrera, buscada por BattleTag. No hay inicio de sesión: Blizzard ya publica el perfil de quien lo haya puesto público, así que basta un nombre y esta app nunca ve una contraseña.",
        pt="Sua carreira, buscada por BattleTag. Não há login: a Blizzard já publica o perfil de quem o deixou público, então basta um nome e este app nunca vê uma senha.",
        fr="Votre carrière, recherchée par BattleTag. Pas de connexion : Blizzard publie déjà le profil de qui l\'a rendu public, un nom suffit donc et cette appli ne voit jamais de mot de passe.",
        de="Deine Laufbahn, über den BattleTag gesucht. Keine Anmeldung: Blizzard veröffentlicht das Profil ohnehin, wenn es auf öffentlich steht - ein Name genügt, und diese App sieht nie ein Passwort.",
        ja="BattleTag で検索する自分の戦績。ログインはありません。公開設定にしていれば Blizzard が profile を公開しているので、名前だけで足り、このアプリがパスワードを見ることはありません。",
        ko="BattleTag로 조회하는 내 전적입니다. 로그인은 없습니다. 공개로 설정했다면 Blizzard가 이미 프로필을 공개하므로 이름만으로 충분하며, 이 앱은 비밀번호를 보지 않습니다.",
        zhCN="用 BattleTag 查询你的战绩。无需登录：只要资料设为公开，暴雪本就会公布，因此只需一个名字，本应用永远不会看到密码。",
        zhTW="用 BattleTag 查詢你的戰績。無需登入：只要資料設為公開，暴雪本就會公布，因此只需一個名字，本應用程式永遠不會看到密碼。",
        ru="Ваша карьера по BattleTag. Входа нет: Blizzard и так публикует профиль тех, кто открыл его, поэтому достаточно имени, и приложение никогда не видит пароль.",
        uk="Ваша кар\'єра за BattleTag. Входу немає: Blizzard і так публікує профіль тих, хто його відкрив, тож достатньо імені, і застосунок ніколи не бачить пароля.",
        sv="Din karriär, uppslagen via BattleTag. Ingen inloggning: Blizzard publicerar redan profilen för den som satt den till offentlig, så ett namn räcker och appen ser aldrig ett lösenord.",
        ar="سجلك مبحوثًا عنه بواسطة BattleTag. لا تسجيل دخول: Blizzard تنشر الملف أصلًا لمن جعله عامًا، فيكفي الاسم، وهذا التطبيق لا يرى كلمة مرور أبدًا.",
        pl="Twoja kariera wyszukiwana po BattleTagu. Bez logowania: Blizzard i tak publikuje profil tych, którzy ustawili go jako publiczny, więc wystarczy nazwa, a ta aplikacja nigdy nie widzi hasła.",
        tr="BattleTag ile aranan kariyerin. Giriş yok: Blizzard, profilini herkese açık yapanların profilini zaten yayımlıyor, bu yüzden bir ad yeterli ve bu uygulama asla parola görmez.",
    ),
    "player_battletag": t(
        es="BattleTag o nombre", pt="BattleTag ou nome", fr="BattleTag ou nom",
        de="BattleTag oder Name", ja="BattleTag または名前", ko="BattleTag 또는 이름",
        zhCN="BattleTag 或名称", zhTW="BattleTag 或名稱", ru="BattleTag или имя",
        uk="BattleTag або ім\'я", sv="BattleTag eller namn", ar="BattleTag أو الاسم",
        pl="BattleTag lub nazwa", tr="BattleTag veya ad",
    ),
    "player_search": t(
        es="Buscar", pt="Buscar", fr="Rechercher", de="Suchen", ja="検索", ko="검색",
        zhCN="搜索", zhTW="搜尋", ru="Найти", uk="Знайти", sv="Sök",
        ar="بحث", pl="Szukaj", tr="Ara",
    ),
    "player_no_results": t(
        es="Nadie con ese nombre. La búsqueda quiere el nombre solo, sin el número tras la almohadilla — y distingue mayúsculas, así que se probaron ambas.",
        pt="Ninguém com esse nome. A busca quer o nome sozinho, sem o número após o cerquilha — e diferencia maiúsculas, então ambas foram tentadas.",
        fr="Personne de ce nom. La recherche veut le nom seul, sans le numéro après le dièse — et elle distingue les majuscules, les deux ont donc été essayées.",
        de="Niemand mit diesem Namen. Die Suche will den Namen allein, ohne die Zahl hinter der Raute - und sie unterscheidet Groß- und Kleinschreibung, beides wurde probiert.",
        ja="その名前の人は見つかりません。検索は # の後ろの数字を除いた名前だけを求め、大文字と小文字も区別するため、両方を試しました。",
        ko="그 이름의 사용자가 없습니다. 검색에는 # 뒤 숫자를 뺀 이름만 필요하며, 대소문자를 구분하므로 두 가지 모두 시도했습니다.",
        zhCN="没有这个名字的玩家。搜索只要井号前的名字，并且区分大小写，两种写法都已尝试。",
        zhTW="沒有這個名字的玩家。搜尋只要井號前的名字，並且區分大小寫，兩種寫法都已嘗試。",
        ru="Никого с таким именем. Поиску нужно только имя без числа после решётки, и он различает регистр - оба варианта уже пробовались.",
        uk="Нікого з таким іменем. Пошуку потрібне лише ім\'я без числа після решітки, і він розрізняє регістр - обидва варіанти вже пробувалися.",
        sv="Ingen med det namnet. Sökningen vill ha namnet ensamt, utan siffran efter brädgården - och den skiljer på versaler, så båda stavningarna testades.",
        ar="لا أحد بهذا الاسم. البحث يريد الاسم وحده دون الرقم بعد العلامة، وهو يفرّق بين الحروف الكبيرة والصغيرة، لذا جُرِّبت الصيغتان.",
        pl="Nikogo o tej nazwie. Wyszukiwarka chce samej nazwy, bez liczby po kratce - i rozróżnia wielkość liter, więc sprawdzono obie pisownie.",
        tr="Bu adda kimse yok. Arama, kareden sonraki sayı olmadan yalnızca adı ister ve büyük harfe duyarlıdır; her iki yazım da denendi.",
    ),
    "player_public": t(
        es="Perfil público", pt="Perfil público", fr="Profil public",
        de="Öffentliches Profil", ja="公開プロフィール", ko="공개 프로필",
        zhCN="公开资料", zhTW="公開資料", ru="Открытый профиль", uk="Відкритий профіль",
        sv="Offentlig profil", ar="ملف عام", pl="Profil publiczny", tr="Herkese açık profil",
    ),
    "player_hidden": t(
        es="El perfil no es público", pt="O perfil não é público",
        fr="Le profil n\'est pas public", de="Profil ist nicht öffentlich",
        ja="プロフィールは非公開", ko="프로필이 비공개",
        zhCN="资料未公开", zhTW="資料未公開", ru="Профиль закрыт", uk="Профіль закритий",
        sv="Profilen är inte offentlig", ar="الملف ليس عامًا",
        pl="Profil nie jest publiczny", tr="Profil herkese açık değil",
    ),
    "player_add": t(
        es="Añadir otra", pt="Adicionar outra", fr="En ajouter un", de="Weiteres hinzufügen",
        ja="別のアカウントを追加", ko="다른 계정 추가", zhCN="添加另一个", zhTW="新增另一個",
        ru="Добавить ещё", uk="Додати ще", sv="Lägg till ett till",
        ar="أضف حسابًا آخر", pl="Dodaj kolejne", tr="Başka ekle",
    ),
    "player_saved": t(
        es="Tus cuentas", pt="Suas contas", fr="Vos comptes", de="Deine Konten",
        ja="自分のアカウント", ko="내 계정", zhCN="你的账号", zhTW="你的帳號",
        ru="Ваши аккаунты", uk="Ваші акаунти", sv="Dina konton",
        ar="حساباتك", pl="Twoje konta", tr="Hesapların",
    ),
    "player_remove": t(
        es="Quitar esta cuenta", pt="Remover esta conta", fr="Retirer ce compte",
        de="Dieses Konto entfernen", ja="このアカウントを削除", ko="이 계정 삭제",
        zhCN="移除此账号", zhTW="移除此帳號", ru="Убрать этот аккаунт",
        uk="Прибрати цей акаунт", sv="Ta bort det här kontot", ar="أزل هذا الحساب",
        pl="Usuń to konto", tr="Bu hesabı kaldır",
    ),
    "player_hit_line": t(
        es="%1$d partidas  ·  %2$.1f%% ganadas", pt="%1$d partidas  ·  %2$.1f%% vencidas",
        fr="%1$d parties  ·  %2$.1f%% gagnées", de="%1$d Spiele  ·  %2$.1f%% gewonnen",
        ja="%1$d 試合  ·  勝率 %2$.1f%%", ko="%1$d 경기  ·  승률 %2$.1f%%",
        zhCN="%1$d 场  ·  胜率 %2$.1f%%", zhTW="%1$d 場  ·  勝率 %2$.1f%%",
        ru="%1$d игр  ·  %2$.1f%% побед", uk="%1$d ігор  ·  %2$.1f%% перемог",
        sv="%1$d matcher  ·  %2$.1f%% vunna", ar="%1$d مباراة  ·  %2$.1f%% فوز",
        pl="%1$d gier  ·  %2$.1f%% wygranych", tr="%1$d maç  ·  %%%2$.1f kazanma",
    ),
    "player_private": t(
        es="Este perfil existe, pero sus estadísticas no son públicas. Es un ajuste dentro de Overwatch — Opciones, Social, Perfil de carrera — y no algo que esta app pueda hacer desde fuera.",
        pt="Este perfil existe, mas suas estatísticas não são públicas. É uma configuração dentro do Overwatch — Opções, Social, Perfil de Carreira — e não algo que este app possa fazer de fora.",
        fr="Ce profil existe, mais ses statistiques ne sont pas publiques. C\'est un réglage dans Overwatch — Options, Social, Profil de carrière — et non quelque chose que cette appli puisse faire de l\'extérieur.",
        de="Dieses Profil gibt es, aber seine Statistiken sind nicht öffentlich. Das ist eine Einstellung in Overwatch - Optionen, Soziales, Karriereprofil - und nichts, was diese App von außen ändern könnte.",
        ja="このプロフィールは存在しますが、戦績が公開されていません。Overwatch の「オプション → ソーシャル → キャリアプロフィール」の設定であり、このアプリが外から変えられるものではありません。",
        ko="이 프로필은 존재하지만 전적이 공개되어 있지 않습니다. Overwatch의 옵션 → 소셜 → 경력 프로필 설정이며, 이 앱이 밖에서 바꿀 수 있는 것이 아닙니다.",
        zhCN="该资料存在，但战绩未公开。这是《守望先锋》里的设置——选项、社交、生涯档案——不是本应用能从外部更改的。",
        zhTW="該資料存在，但戰績未公開。這是《鬥陣特攻》裡的設定——選項、社交、生涯檔案——不是本應用程式能從外部更改的。",
        ru="Профиль существует, но его статистика закрыта. Это настройка внутри Overwatch - Параметры, Социальное, Профиль карьеры - и приложение снаружи её не изменит.",
        uk="Профіль існує, але його статистика закрита. Це налаштування всередині Overwatch - Параметри, Соціальне, Профіль кар\'єри - і застосунок ззовні його не змінить.",
        sv="Profilen finns, men dess statistik är inte offentlig. Det är en inställning inne i Overwatch - Alternativ, Socialt, Karriärprofil - och inget appen kan göra utifrån.",
        ar="هذا الملف موجود لكن إحصاءاته ليست عامة. هذا إعداد داخل Overwatch - الخيارات، الاجتماعي، الملف المهني - وليس شيئًا يستطيع التطبيق تغييره من الخارج.",
        pl="Ten profil istnieje, ale jego statystyki nie są publiczne. To ustawienie w Overwatchu - Opcje, Społeczność, Profil kariery - a nie coś, co ta aplikacja może zmienić z zewnątrz.",
        tr="Bu profil var ama istatistikleri herkese açık değil. Bu, Overwatch içindeki bir ayardır - Seçenekler, Sosyal, Kariyer Profili - ve bu uygulamanın dışarıdan yapabileceği bir şey değildir.",
    ),
    "player_failed": t(
        es="No se pudo acceder al perfil.", pt="Não foi possível acessar o perfil.",
        fr="Impossible d\'atteindre le profil.", de="Profil nicht erreichbar.",
        ja="プロフィールに接続できませんでした。", ko="프로필에 접근하지 못했습니다.",
        zhCN="无法访问该资料。", zhTW="無法存取該資料。",
        ru="Не удалось получить профиль.", uk="Не вдалося отримати профіль.",
        sv="Kunde inte nå profilen.", ar="تعذّر الوصول إلى الملف.",
        pl="Nie udało się pobrać profilu.", tr="Profile ulaşılamadı.",
    ),
    "player_games": t(
        es="Partidas", pt="Partidas", fr="Parties", de="Spiele", ja="試合", ko="경기",
        zhCN="场次", zhTW="場次", ru="Игры", uk="Ігри", sv="Matcher",
        ar="المباريات", pl="Gry", tr="Maç",
    ),
    "player_winrate": t(
        es="Victorias", pt="Vitórias", fr="Victoires", de="Siegrate", ja="勝率", ko="승률",
        zhCN="胜率", zhTW="勝率", ru="Винрейт", uk="Вінрейт", sv="Vinstprocent",
        ar="نسبة الفوز", pl="Wygrane", tr="Kazanma",
    ),
    "player_kda": t(
        es="KDA", pt="KDA", fr="KDA", de="KDA", ja="KDA", ko="KDA",
        zhCN="KDA", zhTW="KDA", ru="KDA", uk="KDA", sv="KDA",
        ar="KDA", pl="KDA", tr="KDA",
    ),
    "player_time": t(
        es="Jugado", pt="Jogado", fr="Joué", de="Gespielt", ja="プレイ時間", ko="플레이 시간",
        zhCN="时长", zhTW="時長", ru="Сыграно", uk="Зіграно", sv="Spelat",
        ar="وقت اللعب", pl="Rozegrane", tr="Oynanan",
    ),
    "player_by_role": t(
        es="Por rol", pt="Por função", fr="Par rôle", de="Nach Rolle", ja="ロール別",
        ko="역할별", zhCN="按定位", zhTW="按定位", ru="По ролям", uk="За ролями",
        sv="Per roll", ar="حسب الدور", pl="Wg roli", tr="Role göre",
    ),
    "player_by_hero": t(
        es="Por héroe, más jugados primero", pt="Por herói, mais jogados primeiro",
        fr="Par héros, les plus joués d\'abord", de="Nach Held, meistgespielte zuerst",
        ja="ヒーロー別（プレイ時間順）", ko="영웅별, 많이 한 순서",
        zhCN="按英雄，游玩最多在前", zhTW="按英雄，遊玩最多在前",
        ru="По героям, сначала самые сыгранные", uk="За героями, спершу найбільш зіграні",
        sv="Per hjälte, mest spelade först", ar="حسب البطل، الأكثر لعبًا أولًا",
        pl="Wg bohatera, najczęściej grani najpierw", tr="Kahramana göre, en çok oynanan önce",
    ),
    "player_line": t(
        es="%1$d partidas  ·  %2$.1f%%  ·  %3$.2f KDA  ·  %4$s",
        pt="%1$d partidas  ·  %2$.1f%%  ·  %3$.2f KDA  ·  %4$s",
        fr="%1$d parties  ·  %2$.1f%%  ·  %3$.2f KDA  ·  %4$s",
        de="%1$d Spiele  ·  %2$.1f%%  ·  %3$.2f KDA  ·  %4$s",
        ja="%1$d 試合  ·  %2$.1f%%  ·  KDA %3$.2f  ·  %4$s",
        ko="%1$d 경기  ·  %2$.1f%%  ·  KDA %3$.2f  ·  %4$s",
        zhCN="%1$d 场  ·  %2$.1f%%  ·  KDA %3$.2f  ·  %4$s",
        zhTW="%1$d 場  ·  %2$.1f%%  ·  KDA %3$.2f  ·  %4$s",
        ru="%1$d игр  ·  %2$.1f%%  ·  KDA %3$.2f  ·  %4$s",
        uk="%1$d ігор  ·  %2$.1f%%  ·  KDA %3$.2f  ·  %4$s",
        sv="%1$d matcher  ·  %2$.1f%%  ·  %3$.2f KDA  ·  %4$s",
        ar="%1$d مباراة  ·  %2$.1f%%  ·  %3$.2f KDA  ·  %4$s",
        pl="%1$d gier  ·  %2$.1f%%  ·  %3$.2f KDA  ·  %4$s",
        tr="%1$d maç  ·  %%%2$.1f  ·  %3$.2f KDA  ·  %4$s",
    ),
    "player_credit": t(
        es="Los perfiles de carrera vienen de Blizzard a través de la API OverFast. Solo se guarda el BattleTag que elijas, y solo en este teléfono.",
        pt="Os perfis de carreira vêm da Blizzard pela API OverFast. Só o BattleTag que você escolher é salvo, e só neste telefone.",
        fr="Les profils de carrière viennent de Blizzard via l\'API OverFast. Seul le BattleTag que vous choisissez est conservé, et uniquement sur ce téléphone.",
        de="Die Karriereprofile stammen von Blizzard über die OverFast-API. Gespeichert wird nur der gewählte BattleTag, und nur auf diesem Gerät.",
        ja="キャリアプロフィールは OverFast API 経由で Blizzard から取得しています。保存されるのは選んだ BattleTag だけで、この端末の中だけです。",
        ko="경력 프로필은 OverFast API를 통해 Blizzard에서 가져옵니다. 저장되는 것은 선택한 BattleTag뿐이며, 이 기기 안에만 저장됩니다.",
        zhCN="生涯档案通过 OverFast API 来自暴雪。仅保存你选择的 BattleTag，且只存在这台手机上。",
        zhTW="生涯檔案透過 OverFast API 來自暴雪。僅保存你選擇的 BattleTag，且只存在這台手機上。",
        ru="Профили карьеры приходят от Blizzard через OverFast API. Сохраняется только выбранный BattleTag и только на этом телефоне.",
        uk="Профілі кар\'єри надходять від Blizzard через OverFast API. Зберігається лише вибраний BattleTag і лише на цьому телефоні.",
        sv="Karriärprofilerna kommer från Blizzard via OverFast-API:t. Bara den BattleTag du väljer sparas, och bara på den här telefonen.",
        ar="ملفات المسيرة تأتي من Blizzard عبر واجهة OverFast. يُحفظ فقط الـ BattleTag الذي تختاره، وعلى هذا الهاتف فقط.",
        pl="Profile kariery pochodzą od Blizzarda przez API OverFast. Zapisywany jest tylko wybrany BattleTag i tylko na tym telefonie.",
        tr="Kariyer profilleri OverFast API üzerinden Blizzard\'dan gelir. Yalnızca seçtiğin BattleTag saklanır, o da yalnızca bu telefonda.",
    ),
    "board_background": t(
        es="Cargar mapa", pt="Carregar mapa", fr="Charger une carte", de="Karte laden",
        ja="マップを読み込む", ko="맵 불러오기", zhCN="载入地图", zhTW="載入地圖",
        ru="Загрузить карту", uk="Завантажити карту", sv="Ladda karta",
        ar="حمّل خريطة", pl="Wczytaj mapę", tr="Harita yükle",
    ),
}


def escape(value: str) -> str:
    """Android string escaping: apostrophes must be escaped, ampersands entity-encoded."""
    value = value.replace("&", "&amp;")
    value = re.sub(r"(?<!\\)'", r"\\'", value)
    return value


def write_locale(qualifier: str, key: str) -> int:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generated by tools/make_translations.py. Edit that file, not this one. -->",
        "<resources>",
    ]
    count = 0
    for name, langs in TRANSLATIONS.items():
        value = langs.get(key)
        if not value:
            continue
        lines.append(f'    <string name="{name}">{escape(value)}</string>')
        count += 1
    lines.append("</resources>")

    directory = RES / f"values-{qualifier}"
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "strings.xml").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return count


def main() -> int:
    english = (RES / "values" / "strings.xml").read_text(encoding="utf-8")
    total_keys = len(re.findall(r'<string name="([^"]+)">', english))

    for qualifier, key in LOCALES.items():
        count = write_locale(qualifier, key)
        share = count / total_keys * 100
        print(f"  values-{qualifier:<8} {count:>3}/{total_keys} strings ({share:.0f}%)")

    print(f"\n{len(LOCALES)} locales generated; missing keys fall back to English.")

    # Italian is hand-written rather than generated, which means it is the one language that
    # can silently fall behind: a new string appears in English, lands in fourteen locales
    # from the table above, and is simply absent here. It went forty-five strings behind
    # before anyone noticed. Nothing is written for it - a machine translation of the
    # original language would be a step down - but it is counted out loud.
    hand_written = RES / "values-it" / "strings.xml"
    if hand_written.exists():
        english_keys = set(re.findall(r'<string name="([^"]+)"', english))
        italian_keys = set(
            re.findall(r'<string name="([^"]+)"', hand_written.read_text(encoding="utf-8"))
        )
        # The app's own name is deliberately the same in every language.
        missing = sorted(english_keys - italian_keys - {"app_name"})
        if missing:
            print(f"\nvalues-it is hand-written and is missing {len(missing)} strings:")
            for key in missing:
                print(f"    {key}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
