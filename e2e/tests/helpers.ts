import { Page, expect } from '@playwright/test';

/** Demo-Passwort der fiktiven Kunden (siehe application.properties). */
export const DEMO_PASSWORD = process.env.CCP_DEMO_PASSWORD ?? 'demo1234';

/** Meldet einen Demo-Kunden an und wartet auf die Transaktionsübersicht. */
export async function login(page: Page, username: string, password = DEMO_PASSWORD) {
  await page.goto('/login');
  await page.getByTestId('login-username').fill(username);
  await page.getByTestId('login-password').fill(password);
  await page.getByTestId('login-submit').click();
}

export async function loginAndOpenTransactions(page: Page, username: string) {
  await login(page, username);
  await expect(page).toHaveURL(/\/transactions$/);
  await expect(page.getByTestId('transactions-table')).toBeVisible();
  // Bei langsamer Verbindung (Tests gegen den Server) ist die Tabelle sichtbar, bevor die
  // Skripte am Seitenende geladen sind. Erst danach reagiert die Seite auf Eingaben.
  await page.waitForLoadState('load');
}
