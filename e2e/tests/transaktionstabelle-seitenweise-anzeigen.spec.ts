import { test, expect, Page, Download } from '@playwright/test';
import { login, loginAndOpenTransactions } from './helpers';

// CCP-23: Transaktionstabelle seitenweise anzeigen (Paginierung).
// Jeder Test entspricht einem Akzeptanzkriterium aus der Spezifikation im JIRA-Ticket.

/** Zeilen der aktuell angezeigten Seite. */
function rows(page: Page) {
  return page.getByTestId('transaction-row');
}

/** Prüft Anzahl Zeilen sowie erste und letzte Transaktions-ID der angezeigten Seite. */
async function expectPage(page: Page, count: number, firstId: string, lastId?: string) {
  await expect(rows(page)).toHaveCount(count);
  await expect(rows(page).first()).toHaveAttribute('data-transaction-id', firstId);
  if (lastId !== undefined) {
    await expect(rows(page).last()).toHaveAttribute('data-transaction-id', lastId);
  }
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
  await expect(page).toHaveURL(new RegExp(`category=${encodeURIComponent(category)}(&|$)`));
}

/** Meldet einen Kunden an und öffnet direkt eine URL der Transaktionsübersicht. */
async function loginAndGoto(page: Page, username: string, url: string) {
  await login(page, username);
  await expect(page).toHaveURL(/\/transactions$/);
  await page.goto(url);
  await expect(page.getByTestId('transactions-table')).toBeVisible();
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

test.describe('CCP-23 Transaktionstabelle seitenweise anzeigen', () => {
  test('AK-1: Erste Seite ohne Filter', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');

    await expectPage(page, 20, 'A-1006', 'A-0136');
    await expect(page.getByTestId('pagination')).toBeVisible();
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 1 von 8');
    await expect(page.getByTestId('pagination-prev')).toBeDisabled();
    await expect(page.getByTestId('pagination-next')).toBeEnabled();
  });

  test('AK-2: Weiter blättern', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 1 von 8');

    await page.getByTestId('pagination-next').click();

    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 2 von 8');
    await expect(page).toHaveURL(/[?&]page=2(&|$)/);
    await expectPage(page, 20, 'A-0135', 'A-0116');
    await expect(page.getByTestId('pagination-prev')).toBeEnabled();
    await expect(page.getByTestId('pagination-next')).toBeEnabled();
  });

  test('AK-3: Zurück blättern', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await page.getByTestId('pagination-next').click();
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 2 von 8');

    await page.getByTestId('pagination-prev').click();

    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 1 von 8');
    await expect(rows(page).first()).toHaveAttribute('data-transaction-id', 'A-1006');
  });

  test('AK-4: Letzte Seite', async ({ page }) => {
    await loginAndGoto(page, 'anna', '/transactions?page=8');

    await expectPage(page, 15, 'A-0015', 'A-0001');
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 8 von 8');
    await expect(page.getByTestId('pagination-next')).toBeDisabled();
    await expect(page.getByTestId('pagination-prev')).toBeEnabled();
  });

  test('AK-4: Letzte Seite bei Lena (Zusatz)', async ({ page }) => {
    await loginAndGoto(page, 'lena', '/transactions?page=9');

    await expect(rows(page)).toHaveCount(2);
    await expect(rows(page).nth(0)).toHaveAttribute('data-transaction-id', 'L-0002');
    await expect(rows(page).nth(1)).toHaveAttribute('data-transaction-id', 'L-0001');
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 9 von 9');
    await expect(page.getByTestId('pagination-next')).toBeDisabled();
  });

  test('AK-5: Filter bleibt beim Blättern erhalten', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filter(page, '', '', 'Mobilität');

    await expectPage(page, 20, 'M-2004');
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 1 von 2');

    await page.getByTestId('pagination-next').click();

    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 2 von 2');
    await expectPage(page, 10, 'M-0046', 'M-0003');
    await expect(page.getByTestId('pagination-next')).toBeDisabled();
    await expect(page.getByTestId('filter-category')).toHaveValue('Mobilität');
    await expect(page.getByTestId('filter-summary')).toContainText('30 Transaktionen');
  });

  test('AK-6: Filterwechsel springt auf Seite 1', async ({ page }) => {
    await loginAndGoto(page, 'anna', '/transactions?page=3');
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 3 von 8');

    await page.getByTestId('filter-category').selectOption('Gastronomie');
    await page.getByTestId('filter-submit').click();
    await expect(page).toHaveURL(/category=Gastronomie/);

    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 1 von 2');
    await expect(rows(page).first()).toHaveAttribute('data-transaction-id', 'A-1004');
    await expect(page).not.toHaveURL(/page=3/);
  });

  test('AK-7: Grenze bei 20 Transaktionen', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');

    // Genau 20 Transaktionen: keine Seitennavigation im DOM.
    await filter(page, '', '', 'Shopping');
    await expect(rows(page)).toHaveCount(20);
    await expect(page.getByTestId('pagination')).toHaveCount(0);

    // 21 Transaktionen: Seitennavigation mit zwei Seiten.
    await filter(page, '', '', 'Gastronomie');
    await expect(page.getByTestId('pagination')).toBeVisible();
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 1 von 2');

    await page.getByTestId('pagination-next').click();
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 2 von 2');
    await expect(rows(page)).toHaveCount(1);
    await expect(rows(page).first()).toHaveAttribute('data-transaction-id', 'A-0003');
  });

  test('AK-7: Keine Seitennavigation bei 6 Transaktionen ab 01.10.2026 (Zusatz)', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '2026-10-01', '', '');

    await expect(rows(page)).toHaveCount(6);
    await expect(page.getByTestId('pagination')).toHaveCount(0);
  });

  test('AK-8: Kennzahlen sind seitenunabhängig', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await expect(page.getByTestId('balance-total')).toHaveText("-1'382.07 CHF");
    await expect(page.getByTestId('count-debits')).toHaveText('142 Belastungen');
    await expect(page.getByTestId('count-credits')).toHaveText('13 Gutschriften');

    await page.getByTestId('pagination-next').click();
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 2 von 8');

    await expect(page.getByTestId('balance-total')).toHaveText("-1'382.07 CHF");
    await expect(page.getByTestId('count-debits')).toHaveText('142 Belastungen');
    await expect(page.getByTestId('count-credits')).toHaveText('13 Gutschriften');
  });

  test('AK-9: CSV-Export umfasst die ganze Auswahl', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filter(page, '', '', 'Mobilität');
    await page.getByTestId('pagination-next').click();
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 2 von 2');
    await expect(rows(page)).toHaveCount(10);

    const exportButton = page.getByTestId('export-csv');
    await expect(exportButton).toBeEnabled();
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      exportButton.click(),
    ]);
    const bytes = await readDownload(download);

    // UTF-8-BOM entfernen, an CRLF trennen, leeres Element nach dem letzten CRLF verwerfen.
    const text = bytes.subarray(3).toString('utf-8');
    const lines = text.split('\r\n').filter((line) => line !== '');
    const data = lines.slice(1);

    expect(data).toHaveLength(30);
    expect(data[0]).toBe('11.10.2026;Tankstelle Aare;Mobilität;-92.70');
  });

  test('AK-10: Ungültige Seitenzahl', async ({ page }) => {
    await loginAndGoto(page, 'anna', '/transactions?page=0');
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 1 von 8');
    await expect(page.getByTestId('filter-error')).toHaveCount(0);

    await page.goto('/transactions?page=abc');
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 1 von 8');
    await expect(page.getByTestId('filter-error')).toHaveCount(0);

    await page.goto('/transactions?page=99');
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 8 von 8');
    await expect(rows(page).last()).toHaveAttribute('data-transaction-id', 'A-0001');
  });

  test('AK-11: Händlersuche wirkt auf die angezeigte Seite', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 1 von 8');

    await page.getByTestId('merchant-search').fill('migros');

    await expect(page.getByTestId('merchant-search-count')).toHaveText('1 Treffer');
    await expect(page.getByTestId('pagination')).toBeVisible();
    await expect(page.getByTestId('pagination-info')).toHaveText('Seite 1 von 8');
  });
});
