import { defineConfig } from '@playwright/test';
import { existsSync } from 'node:fs';
const chrome = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
export default defineConfig({
    testDir: './e2e', workers: 1, timeout: 60000,
    use: { locale: 'pl-PL', baseURL: 'http://127.0.0.1:8081', trace: 'retain-on-failure', launchOptions: {
        executablePath: process.env.BROWSER_EXECUTABLE ?? (existsSync(chrome) ? chrome : undefined)
    } },
    webServer: { command: 'python3 ../scripts/e2e-server.py', url: 'http://127.0.0.1:8081/api/competition', reuseExistingServer: false, timeout: 30000 }
});
