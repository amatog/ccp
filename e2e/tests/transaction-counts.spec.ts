import { test, expect } from '@playwright/test';
import { loginAndOpenTransactions } from './helpers';

// CCP-9: Anzahl Belastungen und Gutschriften im Kopfbereich anzeigen.
// Jeder Test entspricht einem Akzeptanzkriterium aus der Spezifikation im JIRA-Ticket.
// AK-5 (Einzahl/Mehrzahl inkl. 0) ist laut Spezifikation per Unit-Test abgedeckt.

test.describe('CCP-9 Anzahl Belastungen und Gutschriften', () => {
  test('AK-1: Anzahl für Anna', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');

    await expect(page.getByTestId('count-debits')).toHaveText('142 Belastungen');
    await expect(page.getByTestId('count-credits')).toHaveText('13 Gutschriften');
  });

  test('AK-2: Anzahl für Marco', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');

    await expect(page.getByTestId('count-debits')).toHaveText('130 Belastungen');
    await expect(page.getByTestId('count-credits')).toHaveText('16 Gutschriften');
  });

  test('AK-3: Zeitraumfilter ändert die Anzahl nicht', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await page.getByTestId('filter-from').fill('2026-10-06');
    await page.getByTestId('filter-to').fill('2026-10-06');
    await page.getByTestId('filter-submit').click();
    await expect(page).toHaveURL(/\/transactions\?/);

    // Der Filter greift (nur A-1003) …
    await expect(page.getByTestId('transaction-row')).toHaveCount(1);
    // … die Anzahl im Kopfbereich bezieht sich weiterhin auf das ganze Konto.
    await expect(page.getByTestId('count-debits')).toHaveText('142 Belastungen');
    await expect(page.getByTestId('count-credits')).toHaveText('13 Gutschriften');
  });

  test('AK-4: Anzeige im Kopfbereich', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    const summary = page.getByTestId('account-summary');

    await expect(summary.getByTestId('transaction-counts')).toBeVisible();
    await expect(summary.getByTestId('count-debits')).toBeVisible();
    await expect(summary.getByTestId('count-credits')).toBeVisible();
    await expect(summary.getByTestId('balance-total')).toHaveText("-1'382.07 CHF");
  });
});
