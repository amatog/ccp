# Playwright-Tests automatisch durch Claude – Einrichtung

Ziel: Sobald Codex einen Pull Request eröffnet hat, schreibt Claude die Playwright-Tests zur Spezifikation, führt sie gegen die frisch gebaute App aus und committet sie auf den PR-Branch. Danach prüft Gemini den PR inklusive Tests.

```
codex-implement.yml eröffnet PR
  → startet claude-tests.yml (PR-Nummer, Ticket) – jeder Job auf einem frischen Runner:

    prepare   JIRA-Zugang          Spezifikation + Auftrag bereitlegen            kein PR-Code
    generate  Anthropic-Schlüssel  Claude schreibt e2e/tests/<feature>.spec.ts    kein PR-Code, keine Shell
    verify    keine Geheimnisse    App aus PR-Stand starten, Suite ausführen      hier läuft PR-Code
    fix       Anthropic-Schlüssel  nur bei Fehlschlägen: Korrekturrunde           kein PR-Code, keine Shell
    verify2   keine Geheimnisse    erneute Ausführung                             hier läuft PR-Code
    publish   GitHub + JIRA        Commit auf PR-Branch, PR-Kommentar, CI starten kein PR-Code
                                   → Gemini-Review starten, Ergebnis ins Ticket
```

Bausteine: `claude-generate.yml` (Claude) und `e2e-verify.yml` (Ausführung). Claude kann selbst keine Befehle ausführen; die Testergebnisse aus `verify` bekommt es in der Korrekturrunde als Datei.

Schlägt ein neuer Test fehl, weil die App von der Spezifikation abweicht, passt Claude den Test **nicht** an die App an. Der Test wird trotzdem committet, die CI am PR wird rot, und die Abweichung steht als Befund im PR-Kommentar und im Ticket. So bleibt ein echter Fehler sichtbar.

Schlägt das Erzeugen selbst fehl (z. B. API-Fehler), wird nichts committet und kein Gemini-Review gestartet; das Ticket erhält eine Fehlermeldung.

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
- **Manuell** (z. B. für einen bestehenden PR): GitHub → *Actions → «Claude – Playwright-Tests schreiben» → Run workflow* → PR-Nummer. «Danach Gemini-Review starten» ist standardmässig an.

## Wo der Verlauf nachzulesen ist

| Ort | Was |
|---|---|
| Pull Request | Commit mit den Tests, Kommentar mit Abdeckung je AK, Befunden und Kontrolllauf |
| JIRA-Ticket | Start, Ergebnis (bestanden/fehlgeschlagen), geschätzte KI-Kosten, Links |
| GitHub → Actions → «Claude – Playwright-Tests schreiben» | Protokoll jedes Laufs |

## Sicherheit

- **PR-Code läuft nie zusammen mit Geheimnissen.** App und Tests werden nur in `verify`/`verify2` ausgeführt, und dort ist kein einziges Geheimnis gesetzt. Jeder Job läuft auf einem frischen Runner, nichts bleibt zwischen Jobs bestehen.
- Claude Code läuft mit `--bare` (keine Hooks, MCP-Server oder Einstellungen aus dem PR-Stand), `--permission-mode dontAsk` und **ohne Shell**: erlaubt sind nur Lesen und das Schreiben von Dateien.
- Im Claude-Job ist nur `ANTHROPIC_API_KEY` gesetzt; GitHub- und JIRA-Zugangsdaten gibt es dort nicht. Pro Lauf gilt eine Budget-Obergrenze.
- Werkzeuge und Regeln (`scripts/jira-codex.mjs`, `CLAUDE.md`, Auftragsvorlagen) kommen aus `main`. Nur von `main` aus startbar, nur für PRs aus diesem Repository.
- Änderungen ausserhalb von `e2e/tests/` brechen den Lauf ab – geprüft im Claude-Job und erneut vor dem Commit.
- Die Zahlen im Ticket stammen aus dem Kontrolllauf des Workflows, nicht aus Claudes Angaben.
- Claude committet nicht selbst und mergt nie. Freigabe und Merge bleiben beim Menschen.
