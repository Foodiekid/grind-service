import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';

export default tseslint.config(
  { ignores: ['dist', 'ios', 'android', 'coverage', 'node_modules'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.strict],
    languageOptions: { ecmaVersion: 2022, globals: { ...globals.browser } },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      // Never log from app code: health values must not reach a log. Use the redacting logger when one is needed.
      'no-console': 'error',
    },
  },
  {
    // core/ is pure business logic: no React, no Capacitor, no network, no storage.
    files: ['src/core/**/*.ts'],
    rules: {
      'no-restricted-imports': ['error', { patterns: ['react', 'react-dom', '@capacitor/*', '@capacitor-community/*', '@supabase/*', '@/data/*', '@/native/*', '@/sync/*', '@/app/*', '@/screens/*', '@/components/*'] }],
      'no-restricted-globals': ['error', 'fetch', 'localStorage', 'sessionStorage', 'indexedDB', 'document', 'window', 'navigator'],
    },
  },
  {
    files: ['*.config.{ts,js}', 'tests/**/*.{ts,tsx}'],
    languageOptions: { globals: { ...globals.node } },
  },
);
