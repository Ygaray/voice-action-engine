# 14-RAE-CHECK: Spanish number rules against RAE (D-12, second half)

date: 2026-10-06
fetch_direct: https://www.rae.es/dpd/cardinales, /dpd/cien, /dpd/ciento, /dpd/mil and the ortografia numerales page all returned HTTP 403 to `curl -sL -A 'Mozilla/5.0'` (WebFetch is not available to this executor)
fetch_archive: Wayback Machine snapshots of the same RAE DPD entries returned HTTP 200: cardinales (2022 snapshot), ciento (2025 snapshot; /dpd/cien serves the same entry), mil (2023 snapshot), medio (2022 snapshot, added for R11). The ortografia numerales page was not obtained and is not needed: the DPD entries cover every rule below.
method: each rule was read in the archived RAE text; notes are one-line paraphrases, no RAE passage is reproduced.

RAE rule=R01 status=confirmed source=https://web.archive.org/web/2022/https://www.rae.es/dpd/cardinales note=16 to 19 and 21 to 29 are written as one word today (dieciseis, veintiuno, veintidos); 20 is the simple form veinte
RAE rule=R02 status=confirmed source=https://web.archive.org/web/2022/https://www.rae.es/dpd/cardinales note=the split spellings diez y seis and veinte y uno are called outdated and to be avoided, so the lexicon keeps them as STT aliases only
RAE rule=R03 status=confirmed source=https://web.archive.org/web/2022/https://www.rae.es/dpd/cardinales note=from treinta the conjunction y joins tens and units (treinta y uno, cuarenta y cinco, noventa y ocho); other compounds are plain juxtaposition
RAE rule=R04 status=confirmed source=https://web.archive.org/web/2022/https://www.rae.es/dpd/cardinales note=one-word treintaicinco-style spellings are documented and valid though the split spelling is still the majority; treinticinco and trenta are called vulgar
RAE rule=R05 status=confirmed source=https://web.archive.org/web/2025/https://www.rae.es/dpd/ciento note=cien is the form before a noun and as the bare number, and in complex numerals it is used only before mil (cien mil); the table lists 100 as cien(to)
RAE rule=R06 status=confirmed source=https://web.archive.org/web/2025/https://www.rae.es/dpd/ciento note=ciento stays in full before other numerals (ciento uno, mil ciento dieciseis, dos mil ciento treinta y cuatro)
RAE rule=R07 status=confirmed source=https://web.archive.org/web/2022/https://www.rae.es/dpd/cardinales note=y appears only between tens and units; hundreds and thousands join by juxtaposition (ciento dos, ciento treinta), and the only ciento y in the entry is the idiom ciento y la madre, not a cardinal
RAE rule=R08 status=confirmed source=https://web.archive.org/web/2022/https://www.rae.es/dpd/cardinales note=21 000 is veintiun mil or veintiuna mil and 31 000 is treinta y un mil or treinta y una mil: gender agreement with the noun is optional before mil
RAE rule=R09 status=confirmed source=https://web.archive.org/web/2023/https://www.rae.es/dpd/mil note=1000 is just mil; un mil is called not normal and inadvisable (a vestige in some Central American and Caribbean usage)
RAE rule=R10 status=confirmed source=https://web.archive.org/web/2022/https://www.rae.es/dpd/cardinales note=quinientos, setecientos and novecientos are the forms (never sietecientos or nuevecientos) and the feminines are quinientas, setecientas, novecientas, doscientas and so on
RAE rule=R11 status=confirmed source=https://web.archive.org/web/2022/https://www.rae.es/dpd/medio note=in a decimal quantity medio follows the number with the conjunction y (dos litros y medio); American Spanish also puts it before the noun

## Notes for plan 14-03 (no rule is contradicted, so the research table stands)

- No `contradicted` and no `unverified` rule, so no human-skim obligation is recorded from this check.
- RAE allows the apocopated un forms (veintiun, treinta y un, ciento un) only before a noun or before mil. A bare veintiun at the end of an utterance (stimulus es-13) is therefore a recognizer or alias form, not an RAE form; the strict table accepting it is a deliberate STT alias, and plan 14-09 shows whether the recognizer ever emits it.
- The R04 aliases are one-sided: accept treintaicinco-style spellings, never treinticinco, trenta or trentaicinco (RAE lists these as vulgar).
