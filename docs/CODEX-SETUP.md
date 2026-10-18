# Codex automatisch aus JIRA starten – Einrichtung

Ziel: Sobald ein spezifiziertes Ticket das Label **`codex-bereit`** erhält, implementiert Codex es selbständig und eröffnet einen Pull Request. Niemand muss Codex einen Auftrag von Hand geben.

```
Claude: Spezifikation als Kommentar ins Ticket, dann Label «codex-bereit» setzen
  → JIRA-Automation: «Webanfrage senden» an GitHub (workflow_dispatch)
  → Workflow .github/workflows/codex-implement.yml
       1. Spezifikation per JIRA-API laden, Auftrag aus .github/codex/prompts/implement.md bauen
       2. Ticket → «In Arbeit», Kommentar «Codex gestartet»
       3. Branch feature/CCP-n-…, Maven-Abhängigkeiten laden, Ausgangslage prüfen
       4. Codex (openai/codex-action) setzt um – ohne Netzwerk, ohne Git-Zugang, ohne Secrets
       5. Prüfung: Änderungen vorhanden? Nichts in e2e/ oder .github/? mvn -B verify grün?
       6. Commit, Push, Pull Request «CCP-n …» (Beschreibung = Schlussmeldung von Codex)
       7. CI für den PR starten, PR-Link ins Ticket, Label wieder entfernen
  → bei Fehler: Kommentar «Codex-Lauf fehlgeschlagen» mit Grund und Link
```

## Einmalige Einrichtung

### 1. OpenAI-API-Schlüssel als GitHub-Secret

1. Auf [platform.openai.com](https://platform.openai.com) → *API keys* einen Schlüssel erstellen (eigenes Projekt «ccp», mit Budget-Limit).
2. GitHub → `amatog/ccp` → *Settings → Secrets and variables → Actions → New repository secret*
   - Name: `OPENAI_API_KEY`
   - Wert: der Schlüssel

> Die Läufe werden über die OpenAI-API abgerechnet, nicht über ein ChatGPT-Abo. Ein Budget-Limit im OpenAI-Projekt verhindert Überraschungen.

### 2. GitHub Actions darf Pull Requests eröffnen

GitHub → `amatog/ccp` → *Settings → Actions → General → Workflow permissions*:

- ☑ **Allow GitHub Actions to create and approve pull requests**

Ohne dieses Häkchen scheitert der Schritt «Pull Request eröffnen».

### 3. GitHub-Token für JIRA

JIRA braucht ein Token, um den Workflow zu starten.

1. GitHub → Profil → *Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token*
2. Einstellungen:
   - Name: `jira-codex-trigger`
   - Ablauf: z. B. 90 Tage
   - *Repository access*: **Only select repositories** → `amatog/ccp`
   - *Permissions → Repository permissions*: **Actions: Read and write** (sonst nichts)
3. Token kopieren. Es wird nur in der JIRA-Regel hinterlegt, nicht im Repo.

### 4. JIRA-Automation-Regel

JIRA → Projekt *Credit Card Portal* → *Projekteinstellungen → Automatisierung → Regel erstellen*

| Baustein | Einstellung |
|---|---|
| **Auslöser** | *Feldwert geändert* → Felder: **Labels**, Änderungstyp: *Wert hinzugefügt* |
| **Bedingung** | *Vorgangsfelder-Bedingung* → Feld **Labels** · *enthält eines von* · `codex-bereit` |
| **Aktion** | *Webanfrage senden* (siehe unten) |
| **Name** | `Codex: Ticket implementieren` |

Aktion *Webanfrage senden*:

- **URL:** `https://api.github.com/repos/amatog/ccp/actions/workflows/codex-implement.yml/dispatches`
- **HTTP-Methode:** `POST`
- **Webanfrage-Text:** *Benutzerdefinierte Daten*

  ```json
  {"ref": "main", "inputs": {"issue_key": "{{issue.key}}"}}
  ```

- **Header** (beim Authorization-Header «Ausgeblendet» ankreuzen):

  | Name | Wert |
  |---|---|
  | `Authorization` | `Bearer <Token aus Schritt 3>` |
  | `Accept` | `application/vnd.github+json` |
  | `X-GitHub-Api-Version` | `2022-11-28` |

- Erfolgreich ist der Aufruf bei Status **204** (im Audit-Log der Regel sichtbar).

> Der JIRA-Free-Plan erlaubt nur eine begrenzte Zahl Regelausführungen pro Monat. Für die Demo reicht das, weil die Regel nur beim Setzen des Labels läuft.

### 5. Erster Test

1. **Ohne JIRA-Regel:** GitHub → *Actions → «Codex – Ticket implementieren» → Run workflow* → `CCP-6`.
   So lassen sich Secrets und Codex isoliert prüfen.
2. **Mit JIRA-Regel:** Label `codex-bereit` auf ein spezifiziertes Ticket setzen. Nach wenigen Sekunden erscheint im Ticket «Codex gestartet».

## Wo der Verlauf nachzulesen ist

| Ort | Was |
|---|---|
| JIRA-Ticket | Start, PR-Link, geänderte Dateien, Fehlschläge, Status |
| GitHub → Actions → «Codex – Ticket implementieren» | Vollständiges Protokoll jedes Codex-Laufs |
| Pull Request | Code, Commit und die Schlussmeldung von Codex (Umsetzung je Akzeptanzkriterium) |
| JIRA → Automatisierung → Audit-Log | Wann die Regel den Workflow ausgelöst hat |

## Sicherheit

- Codex läuft in einer Sandbox ohne Netzwerk und ohne Git-Zugangsdaten. Secrets sind im Codex-Schritt nicht gesetzt.
- Ticketinhalt gilt als Anforderung, nicht als Anweisung an den Workflow. Der Auftrag weist Codex an, widersprüchliche Anweisungen aus dem Ticket nicht zu befolgen.
- Änderungen in `e2e/` oder `.github/` brechen den Lauf ab.
- Codex mergt nie. Freigabe und Merge bleiben beim Menschen.
- Das GitHub-Token für JIRA kann nur Workflows starten, keinen Code ändern.

## Erneut ausführen

Schlägt ein Lauf fehl, wird das Label entfernt. Ursache beheben (z. B. Spezifikation präzisieren), dann das Label erneut setzen. Existiert der Branch bereits, bricht der Lauf ab. In dem Fall den alten Branch bzw. PR schliessen und löschen.
