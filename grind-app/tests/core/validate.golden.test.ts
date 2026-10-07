// The port of the web app's backup.js and syncSanitize.js must clean every record exactly as the original did: same
// fields kept, same rounding, same records refused (scripts/golden/validate.mjs runs the original to make the
// fixture). The only differences are recorded in the fixture itself: a date that isn't text is refused.
import { describe, expect, it } from 'vitest';

import raw from '../golden/fixtures/validate.json';
import { decode } from '../golden/decode.ts';
import { BackupError, sanitizeSyncedRecord, validateBackup, type RecordKind } from '@/core/validate/backup.ts';
import { sanitizeLog } from '@/core/validate/dailyLog.ts';
import { sanitizeFavorite, sanitizeMeal } from '@/core/validate/food.ts';
import { sanitizeLab } from '@/core/validate/lab.ts';
import { sanitizeProfile } from '@/core/validate/profile.ts';
import { sanitizeSettings } from '@/core/validate/settings.ts';
import { sanitizeWorkout } from '@/core/validate/workout.ts';

interface Case { input: unknown; output: unknown }
interface SyncCase { kind: RecordKind; key: string; value: unknown; output: unknown }
interface BackupCase { doc: unknown; result?: unknown; error?: string }
interface Fixture {
  now: string;
  labKeys: string[];
  settingKeys: string[];
  deviations: number;
  log: Case[];
  workout: Case[];
  lab: Case[];
  favorite: Case[];
  meal: Case[];
  profile: Case[];
  settings: Case[];
  sync: SyncCase[];
  backups: BackupCase[];
}

const fixture = decode(raw) as Fixture;
const now = new Date(fixture.now);
const context = { now, labKeys: fixture.labKeys, settingKeys: fixture.settingKeys };

const sanitizers: Record<string, (input: unknown) => unknown> = {
  log: sanitizeLog,
  workout: (input) => sanitizeWorkout(input, now),
  lab: (input) => sanitizeLab(input, fixture.labKeys, now),
  favorite: (input) => sanitizeFavorite(input, now),
  meal: (input) => sanitizeMeal(input, now),
  profile: sanitizeProfile,
  settings: (input) => sanitizeSettings(input, fixture.settingKeys),
};

describe.each(Object.keys(sanitizers))('%s', (name) => {
  const cases = fixture[name as 'log'];
  const sanitize = sanitizers[name] as (input: unknown) => unknown;

  it(`cleans all ${String(cases.length)} cases exactly like the web app`, () => {
    cases.forEach(({ input, output }, index) => {
      expect(sanitize(input), `case ${String(index)}: ${(JSON.stringify(input) as string | undefined)?.slice(0, 300) ?? 'undefined'}`).toStrictEqual(output);
    });
  });
});

describe('records from sync', () => {
  it(`accepts or refuses all ${String(fixture.sync.length)} like the web app, including records filed under the wrong key`, () => {
    fixture.sync.forEach(({ kind, key, value, output }, index) => {
      expect(sanitizeSyncedRecord(kind, key, value, context), `case ${String(index)} (${kind} ${key})`).toStrictEqual(output);
    });
  });
});

describe('backup files', () => {
  // Routes are ported in slice 3; these files carry none, so no route is ever cleaned.
  const noRoutes = () => {
    throw new Error('no routes expected in these backups');
  };

  it.each(fixture.backups.map((c, i) => [i, c] as const))('backup %i gives the same result or message', (_, backupCase) => {
    const check = () => validateBackup(backupCase.doc, { ...context, cleanRoute: noRoutes });
    if (backupCase.error) {
      expect(check).toThrow(BackupError);
      expect(check).toThrow(backupCase.error);
    } else {
      expect(check()).toStrictEqual({ ...(backupCase.result as object), routes: [] });
    }
  });
});

it('records how many deliberate differences the fixture holds', () => {
  expect(fixture.deviations).toBeGreaterThan(0);
});
