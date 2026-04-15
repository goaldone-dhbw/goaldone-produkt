module.exports = {
  preset: 'jest-preset-angular',
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  globalSetup: 'jest-preset-angular/global-setup',
  testPathIgnorePatterns: ['<rootDir>/node_modules/', '<rootDir>/dist/', '<rootDir>/src/app/api/'],
  collectCoverage: true,
  coverageReporters: ['html', 'lcov', 'text-summary', 'json-summary'],
  coverageDirectory: '<rootDir>/coverage',
};
