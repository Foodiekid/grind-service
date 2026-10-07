import type { CapacitorConfig } from '@capacitor/cli';

// Same app id as the old Android shell (~/Documents/Grind-android): this app replaces it on the phone.
const config: CapacitorConfig = {
  appId: 'com.grindandtrain.app',
  appName: 'GRIND',
  webDir: 'dist',
  // Files ship inside the app; no remote server URL, so a guest makes no network requests (privacy rule P1).
  android: {
    allowMixedContent: false,
    webContentsDebuggingEnabled: false,
    // Kept from the old shell: the background-geolocation plugin's fix for updates stopping after 5 min in background.
    useLegacyBridge: true,
  },
  ios: {
    contentInset: 'never',
    limitsNavigationsToAppBoundDomains: true,
  },
  plugins: {
    // Encrypted SQLite (SQLCipher). The key lives in Keychain / Keystore. Wired up with the local database.
    CapacitorSQLite: {
      iosDatabaseLocation: 'Library/CapacitorDatabase',
      iosIsEncryption: true,
      iosKeychainPrefix: 'grind',
      androidIsEncryption: true,
    },
    SplashScreen: { launchAutoHide: true, backgroundColor: '#000000' },
    BluetoothLe: {
      displayStrings: {
        scanning: 'Looking for your Oura ring…',
        cancel: 'Cancel',
        availableDevices: 'Rings nearby',
        noDeviceFound: 'No ring found — keep it close to the phone',
      },
    },
  },
};

export default config;
