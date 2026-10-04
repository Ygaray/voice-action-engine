# v1.0.1: no waivers (patch release)

tag: v1.0.1
no waivers: patch release

v1.0.1 is a patch of v1.0.0. It changes no public API: core/api.txt, providers/api.txt and keystore/api.txt are
byte-identical to the baseline released in v1.0.0, so no pre-freeze (category C) decision is reopened. The 13 rows of
`.planning/phases/11-cut-v1-0-0/11-WAIVER-PACKET.md` that Yahir answered on 2026-10-02 stay as answered for v1.0.0 and are
not consulted for this tag. `scripts/release-cut.sh` accepts this statement only for a patch release (PATCH greater
than 0) and only when both fixed lines above are present.
