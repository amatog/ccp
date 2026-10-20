import { test, expect } from '@playwright/test';
import { loginAndOpenTransactions } from './helpers';

test.describe('Transaktionsübersicht', () => {
  test('zeigt Karteninhaber und maskierte Kartennummer', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await expect(page.getByTestId('customer-name')).toHaveText('Anna Muster');
    await expect(page.getByTestId('card-number')).toHaveText('**** **** **** 4242');
  });

  test('listet die Transaktionen des Kunden, neueste zuerst', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    const rows = page.getByTestId('transaction-row');
    await expect(rows).toHaveCount(155);
    await expect(rows.first()).toHaveAttribute('data-transaction-id', 'A-1006');
    await expect(rows.first().getByTestId('tx-merchant')).toHaveText('Swisscom');
    await expect(rows.last()).toHaveAttribute('data-transaction-id', 'A-0001');
    await expect(rows.last().getByTestId('tx-date')).toHaveText('22.10.2025');
  });

  test('Belastungen sind negativ, Gutschriften positiv', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    const debit = page.locator('[data-transaction-id="A-1005"]');
    const credit = page.locator('[data-transaction-id="A-1003"]');
    await expect(debit.getByTestId('tx-amount')).toHaveText('-349.00 CHF');
    await expect(credit.getByTestId('tx-amount')).toHaveText('49.90 CHF');
  });

  test('Saldo entspricht der Summe der Transaktionen', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await expect(page.getByTestId('balance-total')).toHaveText("-1'040.68 CHF");
  });

  test('Kunden sehen nur ihre eigenen Transaktionen', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await expect(page.getByTestId('customer-name')).toHaveText('Marco Beispiel');
    await expect(page.getByTestId('transactions-table')).not.toContainText('Migros Zürich');
    await expect(page.getByTestId('transaction-row')).toHaveCount(146);
  });
});
