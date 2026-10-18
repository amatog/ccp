# Playwright-Tests automatisch durch Claude – Einrichtung

Ziel: Sobald Codex einen Pull Request eröffnet hat, schreibt Claude die Playwright-Tests zur Spezifikation, führt sie gegen die frisch gebaute App aus und committet sie auf den PR-Branch. Danach prüft Gemini den PR inklusive Tests.

```
codex-implement.yml eröffnet PR
  → startet claude-tests.yml (PR-Nummer, Ticket)
       1. PR ermitteln; nur von main aus, nur PRs aus diesem Repository
       2. main (Werkzeuge, CLAUDE.md, Auftrag) und den PR-Stand (Ordner pr/) auschecken
       3. Spezifikation aus JIRA laden, Auftrag aus .github/claude/prompts/e2e-tests.md bauen
       4. App aus dem PR-Stand bauen und auf localhost:8080 starten, Playwright einrichten
       5. Claude Code (nicht-interaktiv) schreibt e2e/tests/<feature>.spec.ts und führt die Tests aus
       6. Prüfung: nur Dateien unter e2e/tests/ geändert? TypeScript ok?
       7. Workflow führt die gesamte Suite selbst aus (Kontrolllauf)
       8. Commit + Push auf den PR-Branch, Zusammenfassung als PR-Kommentar
       9. CI starten, Ergebnis ins JIRA-Ticket
  → startet gemini-review.yml (auch wenn Tests fehlschlagen)
```

Schlägt ein neuer Test fehl, weil die App von der Spezifikation abweicht, passt Claude den Test **nicht** an die App an. Der Test wird trotzdem committet, die CI am PR wird rot, und die Abweichung steht als Befund im PR-Kommentar und im Ticket. So bleibt ein echter Fehler sichtbar.

## Einmalige Einrichtung

### 1. Anthropic-API-Schlüssel erstellen

1. [platform.claude.com](https://platform.claude.com) (Claude Console) öffnen und anmelden.
2. Unter *Billing* Guthaben aufladen; unter *Limits* ein Monatslimit setzen.
3. Unter *API keys* → *Create key*, Name `github-ccp-tests`. Am besten in einem eigenen Workspace «ccp», damit die Kosten getrennt sichtbar sind.
4. Schlüssel kopieren (beginnt mit `sk-ant-`). Er wird nur einmal angezeigt.

> Die Läufe werden über die Claude-API abgerechnet, nicht über ein Claude-Abo.

### 2. GitHub-Secret

GitHub → `amatog/ccp` → *Settings → Secrets and variables → Actions → New repository secret*

- Name: `ANTHROPIC_API_KEY`
- Wert: der Schlüssel aus Schritt 1

### 3. Optional: Modell und Budget

Als Repository-**Variablen** (*Settings → Secrets and variables → Actions → Variables*):

| Variable | Bedeutung | Standard |
|---|---|---|
| `CLAUDE_MODEL` | Modell für Claude Code, z. B. `sonnet` oder `opus` | Standardmodell von Claude Code |
| `CLAUDE_MAX_BUDGET_USD` | Abbruch, wenn ein Lauf diesen Betrag erreicht | `5` |

## Tests starten

- **Automatisch:** am Ende jedes erfolgreichen Codex-Laufs.
- **Manuell** (z. B. für einen bestehenden PR oder nach Korrekturen): GitHub → *Actions → «Claude – Playwright-Tests schreiben» → Run workflow* → PR-Nummer. Mit «Danach Gemini-Review starten» lässt sich das Review mitstarten.

## Wo der Verlauf nachzulesen ist

| Ort | Was |
|---|---|
| Pull Request | Commit mit den Tests, Kommentar mit Abdeckung je AK, Befunden und Kontrolllauf |
| JIRA-Ticket | Start, Ergebnis (bestanden/fehlgeschlagen), geschätzte KI-Kosten, Links |
| GitHub → Actions → «Claude – Playwright-Tests schreiben» | Protokoll jedes Laufs |

## Sicherheit

- Claude Code läuft mit `--bare` (lädt keine Hooks, MCP-Server oder Einstellungen aus dem PR-Stand) und `--permission-mode dontAsk`: erlaubt sind nur Lesen, Schreiben von Dateien sowie `npx playwright test` und `npx tsc`. Alles andere wird abgelehnt.
- Im Claude-Schritt ist nur `ANTHROPIC_API_KEY` gesetzt; GitHub- und JIRA-Zugangsdaten sind dort nicht verfügbar.
- Werkzeuge und Regeln (`scripts/jira-codex.mjs`, `CLAUDE.md`, Auftragsvorlage) kommen aus `main`; aus dem PR-Stand wird nur die App gebaut und getestet.
- Änderungen ausserhalb von `e2e/tests/` brechen den Lauf ab, bevor etwas committet wird.
- Der Workflow führt die Suite unabhängig von Claude selbst aus; die Zahlen im Ticket stammen aus diesem Kontrolllauf.
- Claude committet nicht selbst und mergt nie. Freigabe und Merge bleiben beim Menschen.
