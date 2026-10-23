import { test, expect, Page } from '@playwright/test';
import { loginAndOpenTransactions } from './helpers';

/** Setzt den Zeitraumfilter (Datum im ISO-Format JJJJ-MM-TT) und optional die Kategorie. */
async function filtern(page: Page, from: string, to: string, category?: string) {
  await page.getByTestId('filter-from').fill(from);
  await page.getByTestId('filter-to').fill(to);
  if (category !== undefined) {
    await page.getByTestId('filter-category').selectOption(category);
  }
  await page.getByTestId('filter-submit').click();
  await expect(page).toHaveURL(/[?&]from=/);
}

/** Prüft die drei Summen im Kopfbereich. */
async function erwarteSummen(page: Page, debits: string, credits: string, net: string) {
  const summary = page.getByTestId('account-summary');
  await expect(summary.getByTestId('sum-debits')).toHaveText(debits);
  await expect(summary.getByTestId('sum-credits')).toHaveText(credits);
  await expect(summary.getByTestId('sum-net')).toHaveText(net);
}

test.describe('CCP-20 Summen der Belastungen, Gutschriften und Saldo im Zeitraum', () => {
  test('AK-1: Summen ohne Filter', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    const summary = page.getByTestId('account-summary');
    await expect(summary.getByTestId('period-totals')).toBeVisible();
    await expect(summary.getByTestId('sum-debits')).toBeVisible();
    await expect(summary.getByTestId('sum-credits')).toBeVisible();
    await expect(summary.getByTestId('sum-net')).toBeVisible();
    await erwarteSummen(page, "10'386.51 CHF", "9'004.44 CHF", "1'382.07 CHF");
  });

  test('AK-2: Summen im Zeitraum, Kontowerte bleiben', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filtern(page, '2026-10-01', '2026-10-31');
    await expect(page.getByTestId('transaction-row')).toHaveCount(6);
    await erwarteSummen(page, '678.85 CHF', '49.90 CHF', '628.95 CHF');
    await expect(page.getByTestId('balance-total')).toHaveText("-1'382.07 CHF");
    await expect(page.getByTestId('count-debits')).toHaveText('142 Belastungen');
    await expect(page.getByTestId('count-credits')).toHaveText('13 Gutschriften');
  });

  test('AK-3: Zeitraum ohne Gutschriften', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filtern(page, '2026-10-01', '2026-10-05');
    await expect(page.getByTestId('transaction-row')).toHaveCount(2);
    await erwarteSummen(page, '476.20 CHF', '0.00 CHF', '476.20 CHF');
  });

  test('AK-4: Negativer Saldo, wenn Gutschriften überwiegen', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filtern(page, '2026-10-06', '2026-10-06');
    const rows = page.getByTestId('transaction-row');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toHaveAttribute('data-transaction-id', 'A-1003');
    await erwarteSummen(page, '0.00 CHF', '49.90 CHF', '-49.90 CHF');
  });

  test('AK-5: Tausendertrennzeichen', async ({ page }) => {
    await loginAndOpenTransactions(page, 'lena');
    await filtern(page, '2026-10-01', '2026-10-31');
    await expect(page.getByTestId('transaction-row')).toHaveCount(5);
    await erwarteSummen(page, "2'212.45 CHF", '750.00 CHF', "1'462.45 CHF");
  });

  test('AK-6: Zeitraum ohne Treffer', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filtern(page, '2026-10-15', '2026-10-20');
    await expect(page.getByTestId('filter-no-results')).toBeVisible();
    await erwarteSummen(page, '0.00 CHF', '0.00 CHF', '0.00 CHF');
  });

  test('AK-7: Kategoriefilter wirkt mit', async ({ page }) => {
    await loginAndOpenTransactions(page, 'lena');
    await filtern(page, '2026-10-01', '2026-10-31', 'Reisen');
    const rows = page.getByTestId('transaction-row');
    await expect(rows).toHaveCount(2);
    await expect(page.locator('[data-transaction-id="L-3003"]')).toBeVisible();
    await expect(page.locator('[data-transaction-id="L-3002"]')).toBeVisible();
    await erwarteSummen(page, '500.05 CHF', '750.00 CHF', '-249.95 CHF');
  });

  test('AK-8: Ungültiger Filter zeigt das ganze Konto', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filtern(page, '2026-10-10', '2026-10-01');
    await expect(page.getByTestId('filter-error')).toBeVisible();
    await erwarteSummen(page, "10'619.48 CHF", "9'578.80 CHF", "1'040.68 CHF");
  });

  test('AK-9: Zurücksetzen', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filtern(page, '2026-10-01', '2026-10-31');
    await erwarteSummen(page, '678.85 CHF', '49.90 CHF', '628.95 CHF');
    await page.getByTestId('filter-reset').click();
    await expect(page).toHaveURL(/\/transactions$/);
    await erwarteSummen(page, "10'386.51 CHF", "9'004.44 CHF", "1'382.07 CHF");
  });
});
