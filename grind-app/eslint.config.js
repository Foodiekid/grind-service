import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';

export default tseslint.config(
  { ignores: ['dist', 'ios', 'android', 'coverage', 'node_modules', 'reports', '.stryker-tmp'] },
  {
    files: ['**/*.{ts,tsx}'],
    // Type-aware rules: unhandled promises, unsafe any, impossible conditions, unchecked nulls.
    extends: [js.configs.recommended, ...tseslint.configs.strictTypeChecked],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser },
      parserOptions: { projectService: true, tsconfigRootDir: import.meta.dirname },
    },
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
      // No hidden clock or randomness: callers pass `now` and random bytes, so every result can be repeated in a test.
      'no-restricted-syntax': ['error',
        { selector: "CallExpression[callee.object.name='Date'][callee.property.name='now']", message: 'Pass `now` in instead of reading the clock.' },
        { selector: "NewExpression[callee.name='Date'][arguments.length=0]", message: 'Pass `now` in instead of reading the clock.' },
        { selector: "CallExpression[callee.object.name='Math'][callee.property.name='random']", message: 'Pass random values in; core stays deterministic.' },
      ],
      // A new union member (record kind, unit, reason) can't be silently left unhandled.
      '@typescript-eslint/switch-exhaustiveness-check': 'error',
      // Missing is not zero: `if (count)` or `if (name)` would treat 0 or '' as missing. Compare explicitly.
      '@typescript-eslint/strict-boolean-expressions': ['error', { allowString: false, allowNumber: false, allowNullableObject: true }],
    },
  },
  {
    files: ['*.config.{ts,js}', 'tests/**/*.{ts,tsx}'],
    languageOptions: { globals: { ...globals.node } },
  },
  {
    // Performance tests print their percentiles; tests never see user data, so this can't leak anything.
    files: ['tests/core/performance/**/*.ts'],
    rules: { 'no-console': 'off' },
  },
);
