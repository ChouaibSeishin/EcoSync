import tseslint from 'typescript-eslint';
export default [
  { ignores: ['dist/**', 'node_modules/**', 'playwright-report/**', 'test-results/**'] },
  ...tseslint.configs.recommended,
  { files: ['**/*.{ts,tsx}'], languageOptions: { globals: { window: 'readonly', document: 'readonly', fetch: 'readonly', URLSearchParams: 'readonly', RequestInit: 'readonly', Response: 'readonly', console: 'readonly', process: 'readonly', setTimeout: 'readonly' } } },
];
