declare global {
  interface Window {
    __env?: Record<string, string>;
  }
}

export const environment = {
  production: true,
  issuerUri: window.__env?.['issuerUri'] ?? 'https://sso.goaldone.de',
  clientId: window.__env?.['clientId'] ?? 'YOUR_ZITADEL_CLIENT_ID',
  apiBasePath: '/api',  // reverse-proxied in prod; adjust if needed
  logLevel: 'WARN',
};
