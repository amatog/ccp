import { test, expect, Page } from '@playwright/test';
import { loginAndOpenTransactions } from './helpers';

// CCP-13: Ungewöhnlich hohe Belastungen (DEBIT > CHF 500.00) mit Warnsymbol markieren.

const TOOLTIP = 'Hoher Betrag – bitte prüfen';

function row(page: Page, id: string) {
  return page.locator(`[data-transaction-id="${id}"]`);
}

function warningIn(page: Page, id: string) {
  return row(page, id).getByTestId('tx-amount').getByTestId('high-amount-warning');
}

test.describe('CCP-13 Warnsymbol bei hohen Belastungen', () => {
  test('AK-1: Belastung knapp über der Schwelle (L-3002, CHF 500.05) wird markiert', async ({ page }) => {
    await loginAndOpenTransactions(page, 'lena');
    await expect(row(page, 'L-3002')).toBeVisible();
    await expect(warningIn(page, 'L-3002')).toHaveCount(1);
  });

  test('AK-2: Belastung genau auf der Schwelle (L-3001, CHF 500.00) wird nicht markiert', async ({ page }) => {
    await loginAndOpenTransactions(page, 'lena');
    await expect(row(page, 'L-3001')).toBeVisible();
    await expect(row(page, 'L-3001').getByTestId('high-amount-warning')).toHaveCount(0);
  });

  test('AK-3: Gutschriften werden nie markiert (L-3003, CHF 750.00)', async ({ page }) => {
    await loginAndOpenTransactions(page, 'lena');
    await expect(row(page, 'L-3003')).toBeVisible();
    await expect(row(page, 'L-3003')).toHaveAttribute('data-transaction-type', 'CREDIT');
    await expect(row(page, 'L-3003').getByTestId('high-amount-warning')).toHaveCount(0);
  });

  test('AK-4: Warnsymbol hat Tooltip und zugänglichen Namen', async ({ page }) => {
    await loginAndOpenTransactions(page, 'lena');
    const warning = warningIn(page, 'L-3002');
    await expect(warning).toHaveAttribute('title', TOOLTIP);
    await expect(warning).toHaveAttribute('aria-label', TOOLTIP);
  });

  test('AK-5: Genau L-3002 und L-3004 sind markiert', async ({ page }) => {
    await loginAndOpenTransactions(page, 'lena');
    await expect(page.getByTestId('transaction-row')).toHaveCount(5);
    await expect(page.getByTestId('high-amount-warning')).toHaveCount(2);
    await expect(warningIn(page, 'L-3002')).toHaveCount(1);
    await expect(warningIn(page, 'L-3004')).toHaveCount(1);
    await expect(row(page, 'L-3005')).toBeVisible();
    await expect(row(page, 'L-3005').getByTestId('high-amount-warning')).toHaveCount(0);
  });

  for (const user of ['anna', 'marco']) {
    test(`AK-6: Keine Markierung für bestehende Kundin bzw. Kunden (${user})`, async ({ page }) => {
      await loginAndOpenTransactions(page, user);
      await expect(page.getByTestId('transaction-row').first()).toBeVisible();
      await expect(page.getByTestId('high-amount-warning')).toHaveCount(0);
    });
  }

  test('AK-7: Markierung bleibt nach dem Zeitraumfilter erhalten', async ({ page }) => {
    await loginAndOpenTransactions(page, 'lena');
    await page.getByTestId('filter-from').fill('2026-10-05');
    await page.getByTestId('filter-to').fill('2026-10-10');
    await page.getByTestId('filter-submit').click();

    const rows = page.getByTestId('transaction-row');
    await expect(rows).toHaveCount(2);
    await expect(row(page, 'L-3002')).toBeVisible();
    await expect(row(page, 'L-3003')).toBeVisible();
    await expect(page.getByTestId('high-amount-warning')).toHaveCount(1);
    await expect(warningIn(page, 'L-3002')).toHaveCount(1);
    await expect(row(page, 'L-3003').getByTestId('high-amount-warning')).toHaveCount(0);
  });

  test('AK-8: Betrag bleibt neben dem Warnsymbol lesbar', async ({ page }) => {
    await loginAndOpenTransactions(page, 'lena');
    await expect(row(page, 'L-3002').getByTestId('tx-amount')).toContainText('-500.05 CHF');
    await expect(row(page, 'L-3004').getByTestId('tx-amount')).toContainText("-1'200.00 CHF");
  });

  test('AK-9: Kopfbereich für Lena stimmt', async ({ page }) => {
    await loginAndOpenTransactions(page, 'lena');
    await expect(page.getByTestId('customer-name')).toHaveText('Lena Probst');
    await expect(page.getByTestId('card-number')).toHaveText('**** **** **** 7070');
    await expect(page.getByTestId('balance-total')).toHaveText("-1'462.45 CHF");
    await expect(page.getByTestId('count-debits')).toHaveText('4 Belastungen');
    await expect(page.getByTestId('count-credits')).toHaveText('1 Gutschrift');
  });
});
