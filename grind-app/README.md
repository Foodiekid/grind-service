# GRIND — frontend (iOS + Android app)

React 19 + TypeScript + Vite, wrapped as native iOS and Android apps by Capacitor 8. Everything the user sees, all
on-device logic, the encrypted local database and the native plugins. Design: `../docs/grind-architecture.md`.

**Status (2026-10-06):** step 1b skeleton — builds, lints, tests pass; runs on the iOS Simulator (iPhone 17, iOS 27);
Android project generated but not built (no Android SDK yet). Next: porting the web app's business logic into `src/core/`, screen by screen.

```bash
npm install            # once
npm run dev            # browser at http://localhost:5173 (UI only; native plugins need the app)
npm run check          # typecheck + lint + tests
npm run ios            # build, sync, open Xcode → Run on a simulator
npm run android        # build, sync, open Android Studio (needs the Android SDK)
```

```
frontend/
├── public/              static files served as-is (icons, fonts if ever)
├── src/
│   ├── app/             app shell: entry (main.tsx), tab navigation, theme, providers, error boundary
│   ├── screens/         one folder per screen: today · train · routes · fuel · journal · trends · labs · profile
│   ├── components/      shared UI: rings, sheets, steppers, segmented control, charts, press physics
│   ├── core/            PURE business logic (no React / I/O), tested against the web app
│   ├── data/            SQLite + SQLCipher: migrations/ (numbered SQL), repositories/, write queue, outbox, recycle bin
│   ├── crypto/          key hierarchy + envelope encryption (WebCrypto): master key, KEK, per-record DEK
│   ├── sync/            outbox → upload (presign → PUT → commit) and pull; talks to backend/api/openapi.yaml
│   ├── native/          thin wrappers over Capacitor plugins: GPS, BLE (ring), haptics, notifications, health, keychain
│   ├── styles/          tokens.css (copy of the web app's src/css/tokens.css) + global styles, cascade layers
│   └── assets/foods/    bundled food databases (from the web app's src/data/*.json: USDA, Indian, restaurant)
├── tests/
│   ├── unit/            core/ known-answer tests (ported from the web app's tests/)
│   ├── golden/          same inputs through the web-app JS and this TS → same outputs
│   └── fixtures/        small sample files (synthetic data only — never a real person's health data)
├── ios/  android/       native projects (Capacitor 8; iOS uses Swift Package Manager — no CocoaPods)
```

Rules: screens hold no business rules; every write that should sync puts its outbox row in the same transaction;
guests make no network calls (Supabase and the API client are loaded only after sign-in); never log health values.
