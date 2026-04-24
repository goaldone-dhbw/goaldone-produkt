declare global {
  interface Window {
    __env?: Record<string, string>;
  }
}

export const environment = {
  production: false,
  issuerUri: window.__env?.['issuerUri'] ?? 'https://sso.goaldone.de',
  clientId: window.__env?.['clientId'] ?? 'YOUR_ZITADEL_CLIENT_ID',
  apiBasePath: 'http://localhost:8080',
  logLevel: 'DEBUG',
};
