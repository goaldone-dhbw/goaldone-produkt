#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

// Load .env file if it exists
const envPath = path.join(__dirname, '..', '..', '.env');
if (fs.existsSync(envPath)) {
  const envContent = fs.readFileSync(envPath, 'utf8');
  envContent.split('\n').forEach((line) => {
    const [key, ...valueParts] = line.split('=');
    if (key && key.trim() && !line.startsWith('#')) {
      const value = valueParts.join('=').trim();
      if (!process.env[key.trim()]) {
        process.env[key.trim()] = value;
      }
    }
  });
}

// Read environment variables with fallbacks
const clientId =
  process.env.ZITADEL_CLIENT_ID ||
  process.env.VITE_ZITADEL_CLIENT_ID ||
  'YOUR_ZITADEL_CLIENT_ID';

const isProd = process.env.NODE_ENV === 'production';
const issuerUri =
  process.env.OIDC_ISSUER_URI ||
  process.env.VITE_OIDC_ISSUER_URI ||
  process.env.ZITADEL_ISSUER_URI ||
  process.env.VITE_ZITADEL_ISSUER_URI ||
  (isProd ? 'https://sso.goaldone.de' : 'https://sso.dev.goaldone.de');

const apiBasePath =
  process.env.API_BASE_PATH ||
  process.env.VITE_API_BASE_PATH ||
  (isProd ? '/api/v1' : 'http://localhost:8080/api/v1');

// Generate env.js content
const envJsContent = `(function(window) {
  window.__env = {
    clientId: '${clientId}',
    issuerUri: '${issuerUri}',
    apiBasePath: '${apiBasePath}',
  };
})(window);
`;

// Write to src/assets/env.js
const outputPath = path.join(__dirname, '..', 'src', 'assets', 'env.js');
fs.writeFileSync(outputPath, envJsContent, 'utf8');

console.log(`✓ Generated ${outputPath}`);
console.log(`  clientId: ${clientId}`);
console.log(`  issuerUri: ${issuerUri}`);
console.log(`  apiBasePath: ${apiBasePath}`);
