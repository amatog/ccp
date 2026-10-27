import { test, expect } from '@playwright/test';
import { login, loginAndOpenTransactions } from './helpers';

// CCP-30: Fusszeile mit Copyright-Hinweis auf Login- und Transaktionsseite.

const FOOTER_TEXT = '© 2026 Credit Card Portal';

test.describe('CCP-30 Fusszeile mit Copyright-Hinweis', () => {
  test('AK-1: Login-Seite zeigt Footer mit «© 2026 Credit Card Portal»', async ({ page }) => {
    await page.goto('/login');

    const footer = page.getByTestId('copyright-footer');
    await expect(footer).toHaveCount(1);
    await expect(footer).toBeVisible();
    await expect(footer).toHaveText(FOOTER_TEXT);
    // Die Fusszeile muss ein <footer>-Element sein.
    expect(await footer.evaluate((el) => el.tagName.toLowerCase())).toBe('footer');
  });

  test('AK-2: Transaktionsseite zeigt nach dem Login denselben Footer', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');

    const footer = page.getByTestId('copyright-footer');
    await expect(footer).toHaveCount(1);
    await expect(footer).toBeVisible();
    await expect(footer).toHaveText(FOOTER_TEXT);
    expect(await footer.evaluate((el) => el.tagName.toLowerCase())).toBe('footer');
  });

  test('AK-3: Login, Transaktionstabelle, Filter und Export funktionieren unverändert', async ({ page }) => {
    // Fehlerhafter Login zeigt weiterhin die Fehlermeldung
    await login(page, 'anna', 'falsches-passwort');
    await expect(page.getByTestId('login-error')).toBeVisible();

    // Gültiger Login führt zur Transaktionsübersicht
    await loginAndOpenTransactions(page, 'anna');
    await expect(page.getByTestId('customer-name')).toHaveText('Anna Muster');
    await expect(page.getByTestId('card-number')).toHaveText('**** **** **** 4242');
    await expect(page.getByTestId('balance-total')).toHaveText("-1'382.07 CHF");
    await expect(page.getByTestId('transaction-row')).toHaveCount(155);

    // Zeitraumfilter ab 01.10.2026
    await page.getByTestId('filter-from').fill('2026-10-01');
    await page.getByTestId('filter-submit').click();
    await expect(page.getByTestId('transaction-row')).toHaveCount(6);
    await expect(page.getByTestId('filter-summary')).toContainText('-628.95 CHF');
    await expect(page.getByTestId('filter-reset')).toBeVisible();

    // Export bleibt verfügbar
    await expect(page.getByTestId('export-csv')).toBeVisible();
    await expect(page.getByTestId('export-csv')).toBeEnabled();

    // Footer bleibt auch mit aktivem Filter sichtbar
    await expect(page.getByTestId('copyright-footer')).toHaveText(FOOTER_TEXT);
  });
});
