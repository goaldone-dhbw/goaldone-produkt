declare global {
  interface Window {
    __env?: Record<string, string>;
  }
}

// Guard against window not being defined (in Node.js/test environments)
const getWindowEnv = () => {
  if (typeof window !== 'undefined') {
    return window.__env;
  }
  return undefined;
};

export const environment = {
  production: false,
  issuerUri: getWindowEnv()?.['issuerUri'] ?? 'https://sso.goaldone.de',
  clientId: getWindowEnv()?.['clientId'] ?? 'YOUR_ZITADEL_CLIENT_ID',
  apiBasePath: 'http://localhost:8080',
  logLevel: 'DEBUG',
};
