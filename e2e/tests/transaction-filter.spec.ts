import { test, expect, Page } from '@playwright/test';
import { login, loginAndOpenTransactions } from './helpers';

// CCP-6: Transaktionen nach Zeitraum filtern.
// Jeder Test entspricht einem Akzeptanzkriterium aus der Spezifikation im JIRA-Ticket.

/** Setzt Von/Bis (ISO-Datum, leer = Feld leer lassen) und sendet den Filter ab. */
async function filter(page: Page, from: string, to: string) {
  await page.getByTestId('filter-from').fill(from);
  await page.getByTestId('filter-to').fill(to);
  await page.getByTestId('filter-submit').click();
  await expect(page).toHaveURL(/\/transactions\?/);
}

/** Prüft, dass genau diese Transaktionen in genau dieser Reihenfolge angezeigt werden. */
async function expectTransactionIds(page: Page, ids: string[]) {
  const rows = page.getByTestId('transaction-row');
  await expect(rows).toHaveCount(ids.length);
  for (const [i, id] of ids.entries()) {
    await expect(rows.nth(i)).toHaveAttribute('data-transaction-id', id);
  }
}

/** Anna hat 155 Transaktionen (20.10.2025 bis heute); die neuesten sind die Oktober-Buchungen. */
const ANNA_ANZAHL = 155;
const ANNA_NEUESTE = ['A-1006', 'A-1005', 'A-1004', 'A-1003', 'A-1002', 'A-1001'];
const ANNA_AELTESTE = 'A-0001';

/** Prüft, dass alle Transaktionen von Anna ungefiltert angezeigt werden, neueste zuerst. */
async function expectAlleAnna(page: Page) {
  const rows = page.getByTestId('transaction-row');
  await expect(rows).toHaveCount(ANNA_ANZAHL);
  for (const [i, id] of ANNA_NEUESTE.entries()) {
    await expect(rows.nth(i)).toHaveAttribute('data-transaction-id', id);
  }
  await expect(rows.last()).toHaveAttribute('data-transaction-id', ANNA_AELTESTE);
}

test.describe('CCP-6 Transaktionen nach Zeitraum filtern', () => {
  test('AK-1: Zeitraum mit Von und Bis', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '2026-10-04', '2026-10-09');

    await expectTransactionIds(page, ['A-1004', 'A-1003', 'A-1002']);
    await expect(page.getByTestId('filter-summary'))
      .toHaveText('3 Transaktionen im Zeitraum, Summe: -130.60 CHF');
  });

  test('AK-2: Grenzen sind inklusive', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '2026-10-06', '2026-10-06');

    await expectTransactionIds(page, ['A-1003']);
    await expect(page.getByTestId('filter-summary'))
      .toHaveText('1 Transaktion im Zeitraum, Summe: 49.90 CHF');
  });

  test('AK-3: Nur Von-Datum', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '2026-10-10', '');

    await expectTransactionIds(page, ['A-1006', 'A-1005']);
    await expect(page.getByTestId('filter-summary'))
      .toHaveText('2 Transaktionen im Zeitraum, Summe: -414.00 CHF');
  });

  test('AK-4: Nur Bis-Datum', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '', '2026-10-05');

    await expectTransactionIds(page, ['A-1002', 'A-1001']);
    await expect(page.getByTestId('filter-summary'))
      .toHaveText('2 Transaktionen im Zeitraum, Summe: -146.35 CHF');
  });

  test('AK-5: Zeitraum ohne Treffer', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '2026-10-15', '2026-10-20');

    await expect(page.getByTestId('transactions-table')).toHaveCount(0);
    await expect(page.getByTestId('transaction-row')).toHaveCount(0);
    await expect(page.getByTestId('filter-no-results'))
      .toHaveText('Keine Transaktionen im gewählten Zeitraum.');
    await expect(page.getByTestId('filter-summary')).toHaveCount(0);
  });

  test('AK-6: Von liegt nach Bis', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '2026-10-10', '2026-10-05');

    await expect(page.getByTestId('filter-error'))
      .toHaveText('Das Von-Datum darf nicht nach dem Bis-Datum liegen.');
    await expectAlleAnna(page);
    await expect(page.getByTestId('filter-summary')).toHaveCount(0);
  });

  test('AK-7: Ungültiges Datum in der URL', async ({ page }) => {
    await login(page, 'anna');
    await expect(page).toHaveURL(/\/transactions$/);

    const response = await page.goto('/transactions?from=abc');
    expect(response?.status()).toBe(200);
    await expect(page.getByTestId('filter-error'))
      .toHaveText('Ungültiges Datum. Bitte im Format TT.MM.JJJJ wählen.');
    await expectAlleAnna(page);
  });

  test('AK-8: Filter bleibt sichtbar und lässt sich zurücksetzen', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await expect(page.getByTestId('filter-reset')).toHaveCount(0);

    await filter(page, '2026-10-04', '2026-10-09');
    await expect(page.getByTestId('filter-from')).toHaveValue('2026-10-04');
    await expect(page.getByTestId('filter-to')).toHaveValue('2026-10-09');

    await page.getByTestId('filter-reset').click();
    await expect(page).toHaveURL(/\/transactions$/);
    await expect(page.getByTestId('filter-from')).toHaveValue('');
    await expect(page.getByTestId('filter-to')).toHaveValue('');
    await expectAlleAnna(page);
    await expect(page.getByTestId('filter-summary')).toHaveCount(0);
  });

  test('AK-9: Saldo bleibt Gesamtsaldo', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filter(page, '2026-10-01', '2026-10-05');

    await expectTransactionIds(page, ['M-2002', 'M-2001']);
    await expect(page.getByTestId('balance-total')).toHaveText("-1'040.68 CHF");
  });

  test('AK-10: Datenschutz bleibt gewahrt', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filter(page, '2026-10-01', '2026-10-31');

    await expectTransactionIds(page, ['M-2004', 'M-2003', 'M-2002', 'M-2001']);
    await expect(page.locator('[data-transaction-id^="A-"]')).toHaveCount(0);
  });
});
