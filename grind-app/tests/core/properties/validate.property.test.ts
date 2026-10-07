// Rules every data check must keep for any input at all: never crash, cleaning twice changes nothing, every value
// that comes out is inside its range, and sync never files a record under another record's key.
import fc from 'fast-check';
import { describe, expect, it } from 'vitest';

import { BackupError, sanitizeSyncedRecord, validateBackup, type RecordKind } from '@/core/validate/backup.ts';
import { sanitizeLog } from '@/core/validate/dailyLog.ts';
import { sanitizeFavorite, sanitizeMeal } from '@/core/validate/food.ts';
import { sanitizeLab } from '@/core/validate/lab.ts';
import { sanitizeProfile } from '@/core/validate/profile.ts';
import { LOG_RANGES, type LogField } from '@/core/validate/ranges.ts';
import { sanitizeSettings } from '@/core/validate/settings.ts';
import { sanitizeWorkout } from '@/core/validate/workout.ts';
import { runs } from '../../support/runs.ts';

const now = new Date('2026-10-07T12:00:00.000Z');
const LAB_KEYS = ['ldl', 'hdl', 'glucose', 'vitaminD'];
const SETTING_KEYS = ['grind_goals', 'grind_custom_ex', 'grind_weight_goal', 'grind_map_optin', 'grind_route_goal'];
const RUNS = runs(400);
const context = { now, labKeys: LAB_KEYS, settingKeys: SETTING_KEYS };

// Anything at all, including NaN, -0, Infinity, undefined, sparse arrays and odd keys.
const anything = fc.anything({ withBoxedValues: false, withNullPrototype: true, withObjectString: true, maxDepth: 4 });
// Plausible records, so the paths that keep data are exercised too (pure noise is almost always refused).
const number = fc.oneof(fc.double({ min: -10, max: 6000 }), fc.integer({ min: -5, max: 300 }), fc.constantFrom('', '12', '7.5', 'x', null));
const text = fc.oneof(fc.string({ maxLength: 20 }), fc.constantFrom('Squat', ' Oats ', '💪 Ünïcødé', '<b>x</b>', ''));
const date = fc.constantFrom('2026-10-07', '2024-02-29', '2026-02-30', '', 'x');
const id = fc.oneof(fc.stringMatching(/^[\w.-]{1,12}$/), fc.constantFrom('', 'has space', 'x'.repeat(81)));
const food = fc.record({ id, name: text, kcal: number, meal: fc.constantFrom('breakfast', 'snack', 'brunch'), protein: number, carbs: number, fat: number, qty: number, grams: number, sodium: number, time: fc.constantFrom('07:30', '24:00', ''), label: text, portion: text, source: fc.constantFrom('usda', 'other') }, { requiredKeys: [] });
const log = fc.record({ date, weight: number, steps: number, sleep: number, rhr: number, mood: number, protein: number, foods: fc.array(food, { maxLength: 5 }), notes: text, tags: fc.array(fc.constantFrom('sore', 'ill', 'happy'), { maxLength: 4 }), source: fc.constantFrom('manual', 'garmin') }, { requiredKeys: ['date'] });
const set = fc.record({ weight: number, reps: number, rpe: number, completed: fc.constantFrom(true, false, 'no') }, { requiredKeys: [] });
const workout = fc.record({ id, date, unit: fc.constantFrom('kg', 'lbs', 'st'), splitDay: text, durationMinutes: number, exercises: fc.array(fc.record({ name: text, sets: fc.array(set, { maxLength: 5 }) }), { maxLength: 4 }), startedAt: fc.constantFrom('2026-10-07T10:00:00Z', 'x'), endedAt: fc.constantFrom('2026-10-07T11:00:00Z', '2026-10-09T11:00:00Z') }, { requiredKeys: ['id', 'date'] });
const lab = fc.record({ id, date, labName: text, biomarkers: fc.dictionary(fc.constantFrom(...LAB_KEYS, 'other'), number), qualifiers: fc.dictionary(fc.constantFrom(...LAB_KEYS), fc.constantFrom('<', '>', '=')), reportedDate: date }, { requiredKeys: ['id', 'date'] });
const favorite = fc.record({ id, name: text, kcal: number, protein: number, g: number, serving: text, pt: fc.array(fc.tuple(text, number), { maxLength: 4 }) }, { requiredKeys: ['id'] });
const meal = fc.record({ id, name: text, items: fc.array(fc.record({ name: text, kcal: number, fat: number }), { maxLength: 4 }) }, { requiredKeys: ['id'] });
const settings = fc.record({ values: fc.dictionary(fc.constantFrom(...SETTING_KEYS, 'grind_other'), fc.constantFrom('{}', '[]', 'null', '', 'lose', 'bulk', '1', 'yes', 'not json')) });

const checks = {
  log: { arbitrary: log, clean: (raw: unknown) => sanitizeLog(raw) },
  workout: { arbitrary: workout, clean: (raw: unknown) => sanitizeWorkout(raw, now) },
  lab: { arbitrary: lab, clean: (raw: unknown) => sanitizeLab(raw, LAB_KEYS, now) },
  favorite: { arbitrary: favorite, clean: (raw: unknown) => sanitizeFavorite(raw, now) },
  meal: { arbitrary: meal, clean: (raw: unknown) => sanitizeMeal(raw, now) },
  profile: { arbitrary: fc.record({ name: text, goal: text, avatarUrl: fc.constantFrom('data:image/png;base64,iVBORw0KGgo=', 'https://x', null) }), clean: (raw: unknown) => sanitizeProfile(raw) },
  settings: { arbitrary: settings, clean: (raw: unknown) => sanitizeSettings(raw, SETTING_KEYS) },
} as const;

describe.each(Object.entries(checks))('%s', (_, { arbitrary, clean }) => {
  it('never crashes, whatever arrives', () => {
    fc.assert(fc.property(fc.oneof(anything, arbitrary), (raw) => {
      expect(() => clean(raw)).not.toThrow();
    }), RUNS);
  });

  it('changes nothing when cleaning data that is already clean', () => {
    fc.assert(fc.property(arbitrary, (raw) => {
      const once = clean(raw);
      if (once !== null) expect(clean(once)).toStrictEqual(once);
    }), RUNS);
  });

  it('never returns an undefined field (missing fields are absent, not undefined)', () => {
    fc.assert(fc.property(arbitrary, (raw) => {
      const hasUndefined = (value: unknown): boolean => value !== null && typeof value === 'object'
        && Object.values(value).some((inner) => inner === undefined || hasUndefined(inner));
      expect(hasUndefined(clean(raw))).toBe(false);
    }), RUNS);
  });
});

describe('values that come out are inside their ranges', () => {
  it('daily logs: every measure in range, foods 0 to 5,000 kcal as whole numbers, at most 120 foods', () => {
    fc.assert(fc.property(log, (raw) => {
      const out = sanitizeLog(raw);
      if (out === null) return;
      for (const [key, [low, high]] of Object.entries(LOG_RANGES) as [LogField, readonly [number, number]][]) {
        const value = out[key];
        if (value !== undefined) {
          expect(value).toBeGreaterThanOrEqual(low);
          expect(value).toBeLessThanOrEqual(high);
        }
      }
      for (const entry of out.foods ?? []) {
        expect(Number.isInteger(entry.kcal) && entry.kcal >= 0 && entry.kcal <= 5000).toBe(true);
      }
      expect((out.foods ?? []).length).toBeLessThanOrEqual(120);
    }), RUNS);
  });

  it('workouts: every set within the unit\'s limit, whole reps, RPE 1 to 10 in half steps', () => {
    fc.assert(fc.property(workout, (raw) => {
      const out = sanitizeWorkout(raw, now);
      if (out === null) return;
      const max = out.unit === 'lbs' ? 3300 : 1500;
      for (const exercise of out.exercises) {
        expect(exercise.sets.length).toBeGreaterThan(0);
        for (const s of exercise.sets) {
          expect(s.weight >= 0 && s.weight <= max && Number.isInteger(s.reps) && s.reps >= 0 && s.reps <= 1000).toBe(true);
          if (s.rpe !== undefined) expect(s.rpe >= 1 && s.rpe <= 10 && Number.isInteger(s.rpe * 2)).toBe(true);
        }
      }
    }), RUNS);
  });
});

describe('sync', () => {
  const kinds: RecordKind[] = ['dailyLogs', 'workouts', 'labs', 'favoriteFoods', 'savedMeals', 'profile', 'settings'];

  it('never accepts a record filed under another record\'s key', () => {
    fc.assert(fc.property(fc.constantFrom(...kinds), fc.oneof(id, date, fc.constantFrom('main', 'current_user')), fc.oneof(log, workout, lab, favorite, meal, settings, anything), (kind, key, value) => {
      const out = sanitizeSyncedRecord(kind, key, value, context) as Record<string, unknown> | null;
      if (out === null) return;
      const ownKey = kind === 'dailyLogs' ? out.date : kind === 'profile' ? 'current_user' : kind === 'settings' ? 'main' : out.id;
      expect(ownKey).toBe(key);
    }), runs(1000));
  });
});

describe('backup files', () => {
  it('either returns a checked backup or refuses with a BackupError, never anything else', () => {
    fc.assert(fc.property(fc.oneof(anything, fc.record({ format: fc.constantFrom('grind-backup', 'x'), version: fc.constantFrom(1, 2, 3, 1.5), dailyLogs: fc.array(log, { maxLength: 3 }), workouts: fc.array(workout, { maxLength: 3 }) }, { requiredKeys: [] })), (doc) => {
      try {
        const result = validateBackup(doc, { ...context, cleanRoute: () => null });
        expect(result.rejected).toBeGreaterThanOrEqual(0);
      } catch (error) {
        expect(error).toBeInstanceOf(BackupError);
      }
    }), RUNS);
  });
});
