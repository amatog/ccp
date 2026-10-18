# Change- und Testprozess

Jede Änderung am Credit Card Portal folgt demselben Ablauf. Die KI-Agents reden nicht direkt miteinander: JIRA und GitHub sind die gemeinsame Ablage, und jeder Schritt hinterlässt ein nachvollziehbares Artefakt.

```
JIRA-Ticket (CCP-n)
  → 1. Claude   Spezifikation + Akzeptanzkriterien + data-testid-Liste   → Kommentar im Ticket, Label «codex-bereit»
  → 2. Codex    automatisch: Implementierung auf feature/CCP-n-…          → Pull Request + Kommentar im Ticket
  → 3. Claude   automatisch: Playwright-Tests schreiben und ausführen    → Commit auf den PR-Branch + Kommentar
  → 4. CI       mvn verify + Playwright gegen lokale App                  → Status am PR
  → 5. Gemini   automatisch: Code-Review inkl. Tests gegen die Spezifikation → Review-Kommentar + Empfehlung im Ticket
  → 6. Mensch   Freigabe und Merge nach main
  → 7. Deploy   GitHub Actions: Build → VPS (Port 8081) → Health-Check → Playwright gegen VPS
  → 8. Report   scripts/jira-report.mjs schreibt Resultat ins Ticket
```

## Rollen

| Rolle | Wer | Liefert | Datei mit Anweisungen |
|---|---|---|---|
| Spezifikation & Tests | Claude | Akzeptanzkriterien (Given/When/Then), Playwright-Tests | `CLAUDE.md` |
| Implementierung | Codex | Java/Thymeleaf-Code, Unit-Tests, Pull Request | `AGENTS.md` |
| Review | Gemini | Review-Kommentar mit Befunden nach Schweregrad | `GEMINI.md` |
| Freigabe | Mensch | Approve + Merge | – |
| Reporting | Skript | JIRA-Kommentar mit Testresultat | `scripts/jira-report.mjs` |

## Konventionen

- **Ticket-Schlüssel überall:** Branch `feature/CCP-12-kurzbeschreibung`, Commit-Messages und PR-Titel beginnen mit `CCP-12`. Daran erkennt der JIRA-Report das Ticket.
- **Testbare UI:** Jedes neue UI-Element, das geprüft werden soll, bekommt ein `data-testid`. Die Spezifikation legt die IDs fest, Codex setzt sie, Claude testet sie. Bestehende IDs werden nicht umbenannt.
- **Nur fiktive Daten:** Keine echten Personen, keine echten Kartennummern. Karten erscheinen nur maskiert (`**** **** **** 4242`).
- **Kein direkter Push auf `main`** für Feature-Arbeit. Alles läuft über Pull Requests.

## Pipelines

| Workflow | Auslöser | Was passiert |
|---|---|---|
| `.github/workflows/ci.yml` | Pull Request, manuell, vom Codex-Workflow | Build, Unit-Tests, App lokal starten, Playwright |
| `.github/workflows/codex-implement.yml` | JIRA-Label `codex-bereit` (über JIRA-Automation), manuell | Spezifikation laden → Codex implementiert → Pull Request → Meldung ins Ticket. Einrichtung: [`CODEX-SETUP.md`](CODEX-SETUP.md) |
| `.github/workflows/claude-tests.yml` | Ende von `codex-implement.yml`, manuell (PR-Nummer) | Spezifikation laden → Claude schreibt Tests (ohne Shell) → Ausführung in getrenntem Job ohne Geheimnisse → ggf. Korrekturrunde → Commit auf PR-Branch → CI + Gemini-Review starten → Ergebnis ins Ticket. Bausteine: `claude-generate.yml`, `e2e-verify.yml`. Einrichtung: [`CLAUDE-TESTS-SETUP.md`](CLAUDE-TESTS-SETUP.md) |
| `.github/workflows/gemini-review.yml` | Ende von `claude-tests.yml`, manuell (PR-Nummer) | Spezifikation laden → Gemini prüft nach `GEMINI.md` → Review-Kommentar am PR → Empfehlung ins Ticket. Einrichtung: [`GEMINI-SETUP.md`](GEMINI-SETUP.md) |
| `.github/workflows/deploy.yml` | Push auf `main`, manuell | Build → Deploy auf VPS → Health-Check → Playwright gegen VPS → JIRA-Report |

## Benötigte GitHub-Secrets

| Secret | Zweck | Status |
|---|---|---|
| `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY` | Deployment per SSH | eingerichtet |
| `JIRA_BASE_URL`, `JIRA_EMAIL`, `JIRA_API_TOKEN` | Testreport und Codex-Meldungen ins Ticket | eingerichtet (erfolgreich getestet mit CCP-5 am 16.10.2026) |
| `OPENAI_API_KEY` | Codex-Workflow | eingerichtet (erster Lauf mit CCP-6 am 18.10.2026) |
| `GEMINI_API_KEY` | Gemini-Review | eingerichtet (erster Lauf mit PR #2 am 18.10.2026) |
| `ANTHROPIC_API_KEY` | Playwright-Tests durch Claude | offen (siehe [`CLAUDE-TESTS-SETUP.md`](CLAUDE-TESTS-SETUP.md)) |

## Zielumgebung

- Hostinger-VPS, Ubuntu 24.04, Dienst `ccp` (systemd), Benutzer `deploy`
- App unter `http://<VPS_HOST>:8081`, Health-Check unter `/actuator/health`
- Port 8080 ist auf dem Server von Jenkins belegt – nicht verwenden.
- Der Benutzer `deploy` darf per sudo ausschliesslich `systemctl restart ccp` und `systemctl status ccp` ausführen.
