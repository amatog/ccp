# Gemini-Review automatisch starten – Einrichtung

Ziel: Sobald Codex einen Pull Request eröffnet hat, prüft Gemini ihn selbständig gegen die Spezifikation im JIRA-Ticket. Das Review erscheint als Kommentar am Pull Request, die Empfehlung im Ticket.

```
codex-implement.yml eröffnet PR
  → startet gemini-review.yml (PR-Nummer, Ticket)
       1. PR ermitteln, Ticket aus Eingabe oder PR-Titel
       2. main (Werkzeuge, Regeln) und den PR (nur Prüfgegenstand, Ordner pr/) auschecken
       3. Unterlagen bereitlegen: Spezifikation aus JIRA, PR-Beschreibung, Diff (.gemini-review/)
       4. Gemini CLI (google-github-actions/run-gemini-cli) prüft nach GEMINI.md –
          nur Lesewerkzeuge, keine Shell, kein GitHub-Zugang
       5. Workflow veröffentlicht das Review als PR-Kommentar
       6. Empfehlung + Link als Kommentar ins JIRA-Ticket
  → bei Fehler: Kommentar «Gemini-Review fehlgeschlagen» im Ticket
```

## Einmalige Einrichtung

### 1. Gemini-API-Schlüssel erstellen

1. [aistudio.google.com](https://aistudio.google.com) mit einem Google-Konto öffnen.
2. *Get API key → Create API key*, bei Bedarf ein neues Google-Cloud-Projekt «ccp» anlegen lassen.
3. Schlüssel kopieren. Wie beim OpenAI-Schlüssel nicht in Chats oder Dateien ablegen.

> Die kostenlose Stufe genügt für die Demo. Google kann Eingaben dieser Stufe zur Produktverbesserung verwenden. Für dieses Projekt mit fiktiven Daten ist das unkritisch; für echten Firmencode die kostenpflichtige Stufe oder Vertex AI verwenden.

### 2. GitHub-Secret

GitHub → `amatog/ccp` → *Settings → Secrets and variables → Actions → New repository secret*

- Name: `GEMINI_API_KEY`
- Wert: der Schlüssel aus Schritt 1

### 3. Optional: Modell festlegen

Ohne Angabe nimmt die Gemini CLI ihr Standardmodell. Ein bestimmtes Modell lässt sich als Repository-**Variable** (nicht Secret) setzen: *Settings → Secrets and variables → Actions → Variables → New repository variable*, Name `GEMINI_MODEL`.

## Review starten

- **Automatisch:** am Ende jedes erfolgreichen Codex-Laufs.
- **Manuell** (z. B. nach neuen Commits oder für bestehende PRs): GitHub → *Actions → «Gemini – Pull Request reviewen» → Run workflow* → PR-Nummer eingeben, Ticket optional.

## Wo der Verlauf nachzulesen ist

| Ort | Was |
|---|---|
| Pull Request | Review-Kommentar mit Empfehlung, Befunden und AK-Abdeckung |
| JIRA-Ticket | Empfehlung mit Link zum Review |
| GitHub → Actions → «Gemini – Pull Request reviewen» | Protokoll jedes Laufs |

## Sicherheit

- Gemini erhält nur die Werkzeuge `list_directory`, `read_file` und `grep_search`. Keine Shell, kein Schreiben, kein Netzwerk- oder GitHub-Zugriff.
- Im Gemini-Schritt ist nur der Gemini-API-Schlüssel gesetzt; GitHub- und JIRA-Zugangsdaten sind dort nicht verfügbar.
- Ticket, PR-Beschreibung und Diff gelten als Prüfgegenstand, nicht als Anweisung.
- Werkzeuge und Regeln (`scripts/jira-codex.mjs`, `GEMINI.md`, Review-Vorlage) kommen immer aus `main`. Der PR-Code liegt nur als Prüfgegenstand im Unterordner `pr/`; daraus wird nichts ausgeführt, und seine `GEMINI.md` wird umbenannt, damit Gemini sie nicht als Anweisung lädt.
- Das Review lässt sich nur von `main` aus starten.
- Gemini kommentiert nur. Freigabe und Merge bleiben beim Menschen.
