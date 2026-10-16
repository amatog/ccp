import { test, expect } from '@playwright/test';
import { login } from './helpers';

test.describe('Anmeldung', () => {
  test('nicht angemeldete Besucher landen auf der Login-Seite', async ({ page }) => {
    await page.goto('/transactions');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByTestId('login-submit')).toBeVisible();
  });

  test('falsches Passwort zeigt eine Fehlermeldung', async ({ page }) => {
    await login(page, 'anna', 'falsches-passwort');
    await expect(page).toHaveURL(/\/login\?error/);
    await expect(page.getByTestId('login-error')).toBeVisible();
  });

  test('Abmelden führt zurück zur Login-Seite', async ({ page }) => {
    await login(page, 'anna');
    await page.getByTestId('logout-button').click();
    await expect(page).toHaveURL(/\/login\?logout/);
    await expect(page.getByTestId('logout-message')).toBeVisible();
  });
});
