import { test, expect, Page, Download } from '@playwright/test';
import { login, loginAndOpenTransactions } from './helpers';

// CCP-18: Angezeigte Transaktionen als CSV exportieren.
// Jeder Test entspricht einem Akzeptanzkriterium aus der Spezifikation im JIRA-Ticket.

const KOPFZEILE = 'Datum;Händler;Kategorie;Betrag';

const ANNA_ANZAHL = 155;
const MARCO_ANZAHL = 146;

/** Exportierte CSV-Datei: Dateiname, Rohbytes und Zeilen (ohne BOM, an CRLF getrennt). */
interface CsvExport {
  filename: string;
  bytes: Buffer;
  text: string;
  /** Alle Zeilen inkl. Kopfzeile, ohne das leere Element nach dem letzten CRLF. */
  lines: string[];
}

/** Erwarteter Dateiname mit dem heutigen Datum in der Zeitzone Europe/Zurich. */
function expectedFilename(): string {
  const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Zurich' }).format(new Date());
  return `transaktionen_${today}.csv`;
}

/**
 * Setzt Von/Bis (ISO-Datum, leer = Feld leer lassen) und die Kategorie
 * (Kategoriename, leer = «Alle Kategorien») und sendet den Filter mit «Filtern» ab.
 */
async function filter(page: Page, from: string, to: string, category: string) {
  await page.getByTestId('filter-from').fill(from);
  await page.getByTestId('filter-to').fill(to);
  await page.getByTestId('filter-category').selectOption(category);
  await page.getByTestId('filter-submit').click();
  await expect(page).toHaveURL(/\/transactions\?/);
}

/** Liest den Inhalt eines Downloads vollständig als Buffer. */
async function readDownload(download: Download): Promise<Buffer> {
  const stream = await download.createReadStream();
  if (!stream) {
    throw new Error('Download liefert keinen Inhalt.');
  }
  const chunks: Buffer[] = [];
  for await (const chunk of stream) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  return Buffer.concat(chunks);
}

/**
 * Klickt «Als CSV exportieren», wartet auf den Download und zerlegt die Datei.
 * Prüft dabei die gemeinsamen Formatregeln: BOM, CRLF auch nach der letzten Zeile,
 * keine einzelnen CR/LF innerhalb der Zeilen.
 */
async function exportCsv(page: Page): Promise<CsvExport> {
  const exportButton = page.getByTestId('export-csv');
  await expect(exportButton).toBeVisible();
  await expect(exportButton).toBeEnabled();

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    exportButton.click(),
  ]);
  const bytes = await readDownload(download);

  // UTF-8 mit BOM (EF BB BF).
  expect(bytes.length).toBeGreaterThanOrEqual(3);
  expect([bytes[0], bytes[1], bytes[2]]).toEqual([0xef, 0xbb, 0xbf]);

  const text = bytes.subarray(3).toString('utf-8');
  expect(text.endsWith('\r\n')).toBe(true);

  const lines = text.split('\r\n');
  // Nach dem abschliessenden CRLF bleibt ein leeres Element übrig.
  expect(lines[lines.length - 1]).toBe('');
  lines.pop();
  for (const line of lines) {
    expect(line).not.toContain('\r');
    expect(line).not.toContain('\n');
  }

  return { filename: download.suggestedFilename(), bytes, text, lines };
}

/** Datenzeilen ohne Kopfzeile; prüft, dass die Kopfzeile exakt stimmt. */
function dataLines(csv: CsvExport): string[] {
  expect(csv.lines[0]).toBe(KOPFZEILE);
  return csv.lines.slice(1);
}

test.describe('CCP-18 Angezeigte Transaktionen als CSV exportieren', () => {
  test('AK-1: Export ohne Filter', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');

    const csv = await exportCsv(page);

    expect(csv.filename).toBe(expectedFilename());
    expect(dataLines(csv)).toHaveLength(ANNA_ANZAHL);
  });

  test('AK-2: Dateiformat exakt (Zeitraumfilter, Umlaute)', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '2026-10-01', '2026-10-31', '');

    const csv = await exportCsv(page);

    expect([csv.bytes[0], csv.bytes[1], csv.bytes[2]]).toEqual([0xef, 0xbb, 0xbf]);
    expect(csv.text).toBe(
      [
        'Datum;Händler;Kategorie;Betrag',
        '14.10.2026;Swisscom;Telekom;-65.00',
        '12.10.2026;Digitec Galaxus;Elektronik;-349.00',
        '09.10.2026;Restaurant Seeblick;Gastronomie;-118.50',
        '06.10.2026;Zalando Rückerstattung;Shopping;49.90',
        '04.10.2026;SBB Online;Reisen;-62.00',
        '02.10.2026;Migros Zürich;Lebensmittel;-84.35',
      ].join('\r\n') + '\r\n',
    );
  });

  test('AK-3: Zeitraum und Kategorie kombiniert', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filter(page, '2026-10-01', '2026-10-31', 'Reisen');

    const csv = await exportCsv(page);

    expect(dataLines(csv)).toEqual([
      '08.10.2026;Hotel Alpenblick Gutschrift;Reisen;80.00',
      '05.10.2026;Hotel Alpenblick;Reisen;-420.00',
    ]);
  });

  test('AK-4: Beträge ohne Tausendertrennzeichen', async ({ page }) => {
    await loginAndOpenTransactions(page, 'lena');
    await filter(page, '2026-10-01', '2026-10-31', '');

    const csv = await exportCsv(page);
    const rows = dataLines(csv);

    expect(rows).toHaveLength(5);
    expect(rows).toContain('13.10.2026;Elektro Zentrum Baden;Elektronik;-1200.00');
    expect(rows).toContain('10.10.2026;Reisebüro Horizont Gutschrift;Reisen;750.00');
    for (const row of rows) {
      const fields = row.split(';');
      const amount = fields[fields.length - 1];
      expect(amount).not.toContain("'");
      expect(row).not.toContain('CHF');
    }
  });

  test('AK-5: Zeilenzahl entspricht der Tabelle', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filter(page, '', '', 'Mobilität');

    const tableRows = page.getByTestId('transaction-row');
    await expect(tableRows).toHaveCount(30);
    const tableCount = await tableRows.count();

    const csv = await exportCsv(page);
    const rows = dataLines(csv);

    expect(rows).toHaveLength(tableCount);
    expect(rows).toHaveLength(30);
    expect(rows[0]).toBe('11.10.2026;Tankstelle Aare;Mobilität;-92.70');
  });

  test('AK-6: Button deaktiviert ohne Treffer', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '2026-10-01', '2026-10-10', 'Elektronik');

    await expect(page.getByTestId('filter-no-results')).toBeVisible();
    await expect(page.getByTestId('transaction-row')).toHaveCount(0);

    const exportButton = page.getByTestId('export-csv');
    await expect(exportButton).toBeVisible();
    await expect(exportButton).toBeDisabled();
    await expect(exportButton).toHaveAttribute('title', 'Keine Transaktionen zum Exportieren');
  });

  test('AK-7: Ungültiger Filter exportiert die angezeigte, ungefilterte Liste', async ({ page }) => {
    await login(page, 'anna');
    await expect(page).toHaveURL(/\/transactions$/);

    await page.goto('/transactions?category=Haustiere');
    await expect(page.getByTestId('filter-error')).toHaveText('Unbekannte Kategorie.');
    await expect(page.getByTestId('export-csv')).toBeEnabled();
    await expect(page.getByTestId('transaction-row')).toHaveCount(ANNA_ANZAHL);

    const csv = await exportCsv(page);

    expect(dataLines(csv)).toHaveLength(ANNA_ANZAHL);
  });

  test('AK-8: Export nur für Angemeldete und nur eigene Daten', async ({ browser, page }) => {
    // Nicht angemeldet: frischer Browser-Kontext ohne Session.
    const anonymousContext = await browser.newContext();
    try {
      const anonymousPage = await anonymousContext.newPage();
      // Würde hier eine Datei geliefert, schlüge page.goto mit «Download is starting» fehl.
      await anonymousPage.goto('/transactions/export');
      await expect(anonymousPage).toHaveURL(/\/login/);
      await expect(anonymousPage.getByTestId('login-username')).toBeVisible();
    } finally {
      await anonymousContext.close();
    }

    // Marco angemeldet: nur seine eigenen Transaktionen.
    await loginAndOpenTransactions(page, 'marco');
    const csv = await exportCsv(page);

    expect(dataLines(csv)).toHaveLength(MARCO_ANZAHL);
  });

  test('AK-9: Filter bleibt nach dem Export erhalten', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filter(page, '', '', 'Reisen');

    const summary = page.getByTestId('filter-summary');
    await expect(summary).toBeVisible();
    const urlBefore = page.url();
    const summaryBefore = (await summary.textContent()) ?? '';
    expect(summaryBefore.trim()).not.toBe('');

    await exportCsv(page);

    await expect(page).toHaveURL(urlBefore);
    await expect(page.getByTestId('filter-category')).toHaveValue('Reisen');
    await expect(page.getByTestId('filter-summary')).toHaveText(summaryBefore.trim());
  });
});
