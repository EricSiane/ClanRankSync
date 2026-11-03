# Clan Rank Sync

Compares your in-game clan ranks against a CSV or Google Sheet and flags members whose rank doesn't match.

## Setup

1. Publish your rank list as a CSV, e.g. with columns `RSN,Rank,Alts`. Use a public API endpoint that returns CSV, or a Google Sheet shared as "Anyone with the link".
2. In the Clan Rank Sync settings, paste the link under **Sheet/CSV URL**. Nothing is fetched while it is blank.
3. Under **Columns**, set the 1-based column numbers for RSN, Rank and (optionally) Alts, and whether the CSV has a header row. The defaults are RSN = 1, Rank = 2, Alts = 3.
   Tick **Sync alts to main rank** to also check alt accounts (off by default).
4. Open the Clan Rank Sync side panel and press **Refresh**.

Mismatches are listed in the side panel and highlighted in the clan settings Members tab.

## Notes

- Ranks must match the sheet exactly (case is ignored). If your sheet names a rank differently, add a pair under **Rank aliases** as `in-game rank;sheet rank`, separated by commas, e.g. `Mithril;Mithril Bar, Rune;Rune Bar`.
- Members holding any rank listed under **Ignored ranks** (comma-separated, empty by default) are never flagged.
- Players listed under **Ignored RSNs** (comma-separated, empty by default) are never flagged, whatever their rank.
- Alt accounts listed in the Alts column are expected to share their main's rank. Enable this with **Sync alts to main rank**.
- The plugin sends one HTTPS request to the link you provide when you press Refresh. It sends no player data.
