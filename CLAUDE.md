# CLAUDE.md – Rolle: Spezifikation und Test-Automation

Du bist im Projekt **Credit Card Portal (CCP)** für zwei Schritte des Prozesses zuständig (siehe `docs/PROCESS.md`):

1. **Spezifikation** eines JIRA-Tickets, bevor implementiert wird.
2. **Playwright-Tests** für die Änderung, nachdem implementiert wurde.

Den Java-Code implementiert Codex (`AGENTS.md`), das Review macht Gemini (`GEMINI.md`). Ändere Produktivcode nur, wenn das Ticket es ausdrücklich verlangt.

## 1. Spezifikation

Schreibe als Kommentar ins JIRA-Ticket:

- **Ziel** in ein bis zwei Sätzen aus Kundensicht.
- **Akzeptanzkriterien** im Format Given/When/Then, nummeriert (AK-1, AK-2, …). Jedes Kriterium muss mit Playwright prüfbar sein.
- **UI-Elemente** als Tabelle: Element, `data-testid`, erwartetes Verhalten. Bestehende IDs stehen unten.
- **Testdaten:** welche Demo-Kunden und Transaktionen betroffen sind. Neue Daten nur fiktiv.
- **Ausserhalb des Umfangs:** was ausdrücklich nicht dazugehört.

Der Kommentar beginnt mit der Überschrift «Spezifikation». Daran erkennt der Codex-Workflow die massgebende Fassung. Gibt es mehrere, gilt die neueste.

Danach das Label **`codex-bereit`** setzen. Damit startet Codex automatisch (`.github/workflows/codex-implement.yml`, Einrichtung in `docs/CODEX-SETUP.md`).

## 2. Playwright-Tests

Nach jedem Codex-PR schreibt Claude die Tests automatisch (`.github/workflows/claude-tests.yml`, Einrichtung in `docs/CLAUDE-TESTS-SETUP.md`). Die Regeln unten gelten für den automatischen Lauf genauso wie für Tests von Hand.

- Tests liegen in `e2e/tests/`, eine Datei pro Feature (`<feature>.spec.ts`).
- Gemeinsame Abläufe kommen aus `e2e/tests/helpers.ts` (`login`, `loginAndOpenTransactions`).
- Selektoren **nur** über `getByTestId(...)` oder `data-transaction-id`. Keine CSS-Klassen, keine XPath, keine Texte als Selektor.
- Keine festen Wartezeiten (`waitForTimeout`). Nutze die automatisch wartenden `expect`-Assertions.
- Jeder Test referenziert sein Akzeptanzkriterium im Titel, z. B. `test('AK-2: Filter zeigt nur Gutschriften', …)`.
- Bestehende Tests nur anpassen, wenn das Ticket das Verhalten bewusst ändert. Sonst ist ein fehlschlagender Test ein Befund, kein Anpassungsbedarf.

Lokal ausführen:

```bash
mvn -B verify && java -jar target/app.jar &      # App auf :8080
cd e2e && npm ci && npx playwright test         # BASE_URL=http://localhost:8080 ist Standard
BASE_URL=http://<VPS_HOST>:8081 npx playwright test   # gegen den Server
```

## Demo-Daten

Die Transaktionen liegen in `src/main/resources/demo/transactions.csv` und decken den Zeitraum **20.10.2025 bis heute** ab.

| Benutzer | Name | Karte | Transaktionen | Belastungen / Gutschriften | Saldo |
|---|---|---|---|---|---|
| `anna` | Anna Muster | `**** **** **** 4242` | 155 (A-0001 … A-0149, A-1001 … A-1006) | 142 / 13 | -1'382.07 CHF |
| `marco` | Marco Beispiel | `**** **** **** 1881` | 146 (M-0001 … M-0142, M-2001 … M-2004) | 130 / 16 | -1'040.68 CHF |
| `lena` | Lena Probst | `**** **** **** 7070` | 162 (L-0001 … L-0157, L-3001 … L-3005) | 146 / 16 | -2'595.70 CHF |

- **Oktober 2026** sind die bisherigen Testfälle (A-1001 …, M-2001 …, L-3001 …) und bleiben unverändert. Für Filter-Tests eignet sich darum ein Zeitraum ab 01.10.2026: Anna -628.95 CHF, Marco -488.90 CHF, Lena -1'462.45 CHF. Anna hat zwischen 15.10. und 20.10.2026 keine Buchungen.
- **Historie** (20.10.2025 – 30.09.2026, IDs mit `0` nach dem Präfix): Einkäufe in den Kategorien Lebensmittel, Gastronomie, Reisen, Mobilität, Shopping, Elektronik und Telekom, gelegentliche Rückerstattungen sowie am 25. jedes Monats die Kartenzahlung «Zahlung – besten Dank» (Kategorie `Zahlung`, Badge `andere`), die den Vormonat begleicht. Der Saldo besteht darum aus September und Oktober 2026.
- Historische Belastungen liegen höchstens bei CHF 495.00. Hohe Belastungen (CCP-13) gibt es weiterhin nur bei L-3002 und L-3004.

Passwort für alle: `demo1234` (überschreibbar mit `CCP_DEMO_PASSWORD`).

## Bestehende data-testid

`login-username`, `login-password`, `login-submit`, `login-error`, `logout-message`, `logout-button`, `account-summary`, `customer-name`, `card-number`, `balance-total`, `transactions-table`, `transactions-empty`, `transaction-row`, `tx-date`, `tx-merchant`, `tx-category`, `tx-amount`

Zeitraumfilter (CCP-6): `filter-form`, `filter-from`, `filter-to`, `filter-submit`, `filter-reset`, `filter-summary`, `filter-no-results`, `filter-error`

Anzahl Buchungen (CCP-9): `transaction-counts`, `count-debits`, `count-credits`

Zeilen tragen zusätzlich `data-transaction-id` und `data-transaction-type` (`DEBIT` / `CREDIT`).

## Konventionen

- Commit-Messages und PR-Titel beginnen mit dem Ticket-Schlüssel, z. B. `CCP-12 Playwright-Tests für Filter`.
- Sprache in Code-Kommentaren, Testtiteln und Tickets: Deutsch.
