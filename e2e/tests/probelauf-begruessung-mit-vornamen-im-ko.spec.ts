import { test, expect } from '@playwright/test';
import { loginAndOpenTransactions } from './helpers';

// CCP-31: Begrüssung mit Vornamen im Kopfbereich der Transaktionsseite.

test.describe('CCP-31 Begrüssung mit Vornamen', () => {
  test('AK-1: Anna wird mit «Guten Tag, Anna» über der Kontoübersicht begrüsst', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');

    const greeting = page.getByTestId('greeting');
    await expect(greeting).toBeVisible();
    await expect(greeting).toHaveText('Guten Tag, Anna');

    // Begrüssung steht im <main> direkt vor der Kontoübersicht.
    const position = await greeting.evaluate((el) => ({
      parent: el.parentElement?.tagName.toLowerCase() ?? null,
      next: el.nextElementSibling?.getAttribute('data-testid') ?? null,
    }));
    expect(position.parent).toBe('main');
    expect(position.next).toBe('account-summary');
  });

  const weitereKunden = [
    { username: 'marco', expected: 'Guten Tag, Marco' },
    { username: 'lena', expected: 'Guten Tag, Lena' },
  ];

  for (const { username, expected } of weitereKunden) {
    test(`AK-2: Begrüssung für ${username} lautet «${expected}»`, async ({ page }) => {
      await loginAndOpenTransactions(page, username);
      await expect(page.getByTestId('greeting')).toHaveText(expected);
    });
  }

  test('AK-3: Begrüssung bleibt beim Filtern und Zurücksetzen erhalten', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await expect(page.getByTestId('greeting')).toHaveText('Guten Tag, Anna');

    // Filter ab 01.10.2026 anwenden
    await page.getByTestId('filter-from').fill('2026-10-01');
    await page.getByTestId('filter-submit').click();
    await expect(page).toHaveURL(/from=2026-10-01/);
    await expect(page.getByTestId('filter-summary')).toBeVisible();
    await expect(page.getByTestId('greeting')).toBeVisible();
    await expect(page.getByTestId('greeting')).toHaveText('Guten Tag, Anna');

    // Filter zurücksetzen
    await page.getByTestId('filter-reset').click();
    await expect(page).toHaveURL(/\/transactions$/);
    await expect(page.getByTestId('filter-reset')).toHaveCount(0);
    await expect(page.getByTestId('greeting')).toBeVisible();
    await expect(page.getByTestId('greeting')).toHaveText('Guten Tag, Anna');
  });

  const kunden = [
    { username: 'anna', fullName: 'Anna Muster', card: '**** **** **** 4242', balance: "-1'382.07 CHF" },
    { username: 'marco', fullName: 'Marco Beispiel', card: '**** **** **** 1881', balance: "-1'040.68 CHF" },
    { username: 'lena', fullName: 'Lena Probst', card: '**** **** **** 7070', balance: "-2'595.70 CHF" },
  ];

  for (const { username, fullName, card, balance } of kunden) {
    test(`AK-4: Bestehende Anzeige für ${username} bleibt unverändert`, async ({ page }) => {
      await loginAndOpenTransactions(page, username);

      await expect(page.getByTestId('account-summary')).toBeVisible();
      await expect(page.getByTestId('customer-name')).toHaveText(fullName);
      await expect(page.getByTestId('card-number')).toHaveText(card);
      await expect(page.getByTestId('balance-total')).toHaveText(balance);

      await expect(page.getByTestId('transactions-table')).toBeVisible();
      await expect(page.getByTestId('transaction-row').first()).toBeVisible();

      await expect(page.getByTestId('filter-form')).toBeVisible();
      await expect(page.getByTestId('filter-from')).toBeVisible();
      await expect(page.getByTestId('filter-to')).toBeVisible();
      await expect(page.getByTestId('filter-submit')).toBeVisible();

      await expect(page.getByTestId('export-csv')).toBeEnabled();
    });
  }
});
