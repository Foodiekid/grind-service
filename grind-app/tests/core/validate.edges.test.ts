// Exact limits and branches of the data checks that the golden and property tests don't pin down: each value at its
// limit and just past it, each allowed value, each cap on list sizes. Found by mutation testing (Stryker).
import { describe, expect, it } from 'vitest';

import { BackupError, MAX_BACKUP_BYTES, validateBackup, type CleanRoute } from '@/core/validate/backup.ts';
import { sanitizeLog } from '@/core/validate/dailyLog.ts';
import { hash } from '@/core/validate/fields.ts';
import { sanitizeFavorite, sanitizeMeal } from '@/core/validate/food.ts';
import { sanitizeLab } from '@/core/validate/lab.ts';
import { numberInputError } from '@/core/validate/numberInput.ts';
import { sanitizeProfile } from '@/core/validate/profile.ts';
import { sanitizeSettings } from '@/core/validate/settings.ts';
import { sanitizeWorkout } from '@/core/validate/workout.ts';
import { chooseFirstDay, localeRegion, parseDateStr, SUNDAY_FIRST_REGIONS } from '@/core/util/dates.ts';

const now = new Date('2026-10-07T12:00:00.000Z');
const set = (weight: number, reps: number) => ({ weight, reps });
const workout = (sets: unknown[], extra: Record<string, unknown> = {}) =>
  sanitizeWorkout({ id: 'w1', date: '2026-10-07', exercises: [{ name: 'Squat', sets }], ...extra }, now);

describe('workouts', () => {
  it('keeps 1,000 reps and refuses 1,001', () => {
    expect(workout([set(100, 1000)])?.exercises[0]?.sets).toHaveLength(1);
    expect(workout([set(100, 1001)])).toBeNull();
  });

  it('allows 1,500 kg in kg but not 1,501, and 3,300 in lbs', () => {
    expect(workout([set(1500, 1)])).not.toBeNull();
    expect(workout([set(1501, 1)])).toBeNull();
    expect(workout([set(3300, 1)], { unit: 'lbs' })).not.toBeNull();
    expect(workout([set(1600, 1)], { unit: 'kg' })).toBeNull();
  });

  it('keeps weight 0 (body-weight sets) and refuses negative weights', () => {
    expect(workout([set(0, 12)])?.exercises[0]?.sets).toEqual([{ weight: 0, reps: 12, completed: true }]);
    expect(workout([set(-1, 5)])).toBeNull();
  });

  it('keeps at most 60 exercises', () => {
    const exercises = Array.from({ length: 70 }, (_, i) => ({ name: `Exercise ${String(i)}`, sets: [set(50, 5)] }));
    expect(sanitizeWorkout({ id: 'w1', date: '2026-10-07', exercises }, now)?.exercises).toHaveLength(60);
  });

  it('keeps at most 60 sets per exercise, and an exercise without sets is dropped', () => {
    expect(workout(Array.from({ length: 70 }, () => set(50, 5)))?.exercises[0]?.sets).toHaveLength(60);
    expect(sanitizeWorkout({ id: 'w1', date: '2026-10-07', exercises: [{ name: 'Squat' }] }, now)).toBeNull();
  });

  it('keeps durations from 0 to 1,440 minutes and reads anything else as 0', () => {
    expect(workout([set(50, 5)], { durationMinutes: 0 })?.durationMinutes).toBe(0);
    expect(workout([set(50, 5)], { durationMinutes: 1440 })?.durationMinutes).toBe(1440);
    expect(workout([set(50, 5)], { durationMinutes: 1441 })?.durationMinutes).toBe(0);
    expect(workout([set(50, 5)], { durationMinutes: -1 })?.durationMinutes).toBe(0);
  });

  it('keeps session times only as a pair, start before end, at most a day apart', () => {
    const times = (startedAt: string, endedAt: string) => workout([set(50, 5)], { startedAt, endedAt });
    expect(times('2026-10-07T10:00:00Z', '2026-10-07T11:00:00Z')?.startedAt).toBe('2026-10-07T10:00:00.000Z');
    expect(times('2026-10-07T10:00:00Z', '2026-10-08T10:00:00Z')?.endedAt).toBe('2026-10-08T10:00:00.000Z');
    expect(times('2026-10-07T10:00:00Z', '2026-10-08T10:00:01Z')).not.toHaveProperty('startedAt');
    expect(times('2026-10-07T11:00:00Z', '2026-10-07T10:00:00Z')).not.toHaveProperty('startedAt');
    expect(times('soon', '2026-10-07T10:00:00Z')).not.toHaveProperty('startedAt');
    expect(times('2026-10-07T10:00:00Z', 'later')).not.toHaveProperty('endedAt');
  });
});

describe('daily logs', () => {
  const food = (extra: Record<string, unknown>) => sanitizeLog({ date: '2026-10-07', foods: [{ name: 'Egg', kcal: 78, ...extra }] })?.foods?.[0];

  it('gives entries without an id the same id the web app does', () => {
    const log = sanitizeLog({ date: '2026-10-07', foods: [{ name: 'Egg', kcal: 78 }, { name: 'Oats 💪', kcal: 389.4 }] });
    expect(log?.foods?.map((f) => f.id)).toEqual(['f_0_66892166', 'f_1_98654164']);
  });

  it('hashes text like the web app (the same numbers as Java\'s String.hashCode)', () => {
    expect(hash('')).toBe(0);
    expect(hash('ab')).toBe(3105);
    expect(hash('hello')).toBe(99162322);
  });

  it('accepts only HH:MM times, nothing before or after', () => {
    expect(food({ time: '07:30' })?.time).toBe('07:30');
    expect(food({ time: '23:59' })?.time).toBe('23:59');
    expect(food({ time: '07:30x' })).not.toHaveProperty('time');
    expect(food({ time: 'x07:30' })).not.toHaveProperty('time');
    expect(food({ time: '24:00' })).not.toHaveProperty('time');
  });

  it('keeps grams from 1 to 10,000 (rounded), and drops what rounds to 0 or exceeds it', () => {
    expect(food({ grams: 10000 })?.grams).toBe(10000);
    expect(food({ grams: 10000.4 })).not.toHaveProperty('grams');
    expect(food({ grams: 0.5 })?.grams).toBe(1);
    expect(food({ grams: 0.4 })).not.toHaveProperty('grams');
    for (const missing of [null, undefined, '', 'abc', Number.NaN]) expect(food({ grams: missing })).not.toHaveProperty('grams');
  });

  it('keeps at most 120 foods a day', () => {
    const foods = Array.from({ length: 130 }, (_, i) => ({ name: `Food ${String(i)}`, kcal: 10 }));
    expect(sanitizeLog({ date: '2026-10-07', foods })?.foods).toHaveLength(120);
  });
});

describe('saved foods', () => {
  const favorite = (extra: Record<string, unknown>) => sanitizeFavorite({ id: 'f1', name: 'Rice', kcal: 130, ...extra }, now);

  it('keeps serving grams up to 5,000 and drops what rounds to 0 or exceeds it, with their portions', () => {
    expect(favorite({ g: 5000 })?.g).toBe(5000);
    expect(favorite({ g: 5000.1 })).not.toHaveProperty('g');
    expect(favorite({ g: 0.04, pt: [['cup', 200]] })).not.toHaveProperty('pt');
    expect(favorite({ g: null })).not.toHaveProperty('g');
  });

  it('keeps portions from 0.05 to 5,000 g, at most 8 of them', () => {
    const portions = (pt: unknown) => favorite({ g: 100, pt })?.pt;
    expect(portions([['big', 5000], ['too big', 5000.1], ['tiny', 0.04], ['ok', 0.05]])).toEqual([['big', 5000], ['ok', 0.1]]);
    expect(portions(Array.from({ length: 10 }, (_, i) => [`p${String(i)}`, 10]))).toHaveLength(8);
  });

  it('stamps a missing creation time with now', () => {
    expect(favorite({})?.createdAt).toBe('2026-10-07T12:00:00.000Z');
  });
});

describe('saved meals', () => {
  it('keeps at most 20 items', () => {
    const items = Array.from({ length: 25 }, (_, i) => ({ name: `Item ${String(i)}`, kcal: 10 }));
    expect(sanitizeMeal({ id: 'm1', name: 'Lunch', items }, now)?.items).toHaveLength(20);
  });
});

describe('labs', () => {
  it('marks a value as edited only when the backup says exactly true', () => {
    const edited = (value: unknown) => sanitizeLab({ id: 'l1', date: '2026-10-07', biomarkers: { ldl: 90 }, details: { ldl: { edited: value } } }, ['ldl'], now)?.details?.ldl?.edited;
    expect(edited(true)).toBe(true);
    for (const other of [false, 'true', 1, undefined]) expect(edited(other)).toBe(false);
  });

  it('keeps each of the four qualifiers and nothing else', () => {
    const qualifier = (q: string) => sanitizeLab({ id: 'l1', date: '2026-10-07', biomarkers: { ldl: 90 }, qualifiers: { ldl: q } }, ['ldl'], now)?.qualifiers;
    for (const q of ['<', '>', '≤', '≥']) expect(qualifier(q)).toEqual({ ldl: q });
    expect(qualifier('=')).toBeUndefined();
    expect(qualifier('')).toBeUndefined();
  });
});

describe('profile', () => {
  const avatar = (avatarUrl: string) => sanitizeProfile({ avatarUrl })?.avatarUrl;
  const png = 'data:image/png;base64,';

  it('accepts inline images with 0, 1 or 2 padding characters, nothing before the prefix', () => {
    expect(avatar(`${png}iVBORw0KGgo`)).toBe(`${png}iVBORw0KGgo`);
    expect(avatar(`${png}iVBORw0KGg=`)).toBe(`${png}iVBORw0KGg=`);
    expect(avatar(`${png}iVBORw0KG==`)).toBe(`${png}iVBORw0KG==`);
    expect(avatar(`${png}iVBORw0K===`)).toBeNull();
    expect(avatar(`x${png}iVBORw0KGgo`)).toBeNull();
    expect(avatar('data:image/gif;base64,R0lGOD')).toBeNull();
  });

  it('accepts images shorter than 600,000 characters', () => {
    expect(avatar(png + 'A'.repeat(600000 - png.length - 1))).not.toBeNull();
    expect(avatar(png + 'A'.repeat(600000 - png.length))).toBeNull();
  });
});

describe('settings', () => {
  const keys = ['grind_weight_goal', 'grind_map_optin', 'grind_map_style', 'grind_custom_ex', 'grind_goals', 'grind_fuel', 'grind_splits', 'grind_progress'];
  const check = (values: Record<string, unknown>) => sanitizeSettings({ values }, keys);

  it('accepts every allowed value of the plain settings, and nothing else', () => {
    for (const goal of ['lose', 'maintain', 'gain']) expect(check({ grind_weight_goal: goal })?.values).toEqual({ grind_weight_goal: goal });
    for (const optin of ['1', '0']) expect(check({ grind_map_optin: optin })).not.toBeNull();
    for (const style of ['auto', 'dark', 'light', 'satellite', 'outdoor']) expect(check({ grind_map_style: style })).not.toBeNull();
    expect(check({ grind_map_style: 'Satellite' })).toBeNull();
    expect(check({ grind_weight_goal: '' })).toBeNull();
  });

  it('refuses an object where a list is expected, and a list where an object is expected', () => {
    expect(check({ grind_custom_ex: '[]' })).not.toBeNull();
    expect(check({ grind_custom_ex: '{}' })).toBeNull();
    expect(check({ grind_goals: '[]' })).toBeNull();
    expect(check({ grind_goals: 'null' })).not.toBeNull();
    expect(check({ grind_goals: '5' })).toBeNull();
    expect(check({ grind_goals: '{oops' })).toBeNull();
  });

  it('accepts a value of exactly 100,000 characters and refuses one more', () => {
    const json = (length: number) => `{"a":"${'x'.repeat(length - 8)}"}`;
    expect(json(100000)).toHaveLength(100000);
    expect(check({ grind_goals: json(100000) })).not.toBeNull();
    expect(check({ grind_goals: json(100001) })).toBeNull();
  });

  it('refuses a block over 350,000 characters in total', () => {
    const json = (length: number) => `{"a":"${'x'.repeat(length - 8)}"}`;
    expect(check({ grind_goals: json(100000), grind_fuel: json(100000), grind_splits: json(100000), grind_progress: json(50000) })).not.toBeNull();
    expect(check({ grind_goals: json(100000), grind_fuel: json(100000), grind_splits: json(100000), grind_progress: json(50001) })).toBeNull();
  });
});

describe('number fields', () => {
  it('treats an empty field as missing whatever the limits, and accepts a value equal to the minimum', () => {
    expect(numberInputError('', { min: 1 })).toBe('');
    expect(numberInputError('0.25', { min: 0.25 })).toBe('');
    expect(numberInputError('5', { min: 6, unit: '' })).toBe('Too low — the minimum is 6.');
  });
});

describe('dates', () => {
  it('rejects years 0 to 99, which JavaScript would read as 1900 to 1999', () => {
    for (const text of ['0000-01-01', '0050-01-01', '0099-12-31']) expect(parseDateStr(text)).toBeNull();
  });

  it('accepts only the whole text as a date, nothing before or after it', () => {
    expect(parseDateStr('2026-10-07')).not.toBeNull();
    expect(parseDateStr('2026-10-07x')).toBeNull();
    expect(parseDateStr('x2026-10-07')).toBeNull();
    expect(parseDateStr('2026-10-07T00:00')).toBeNull();
  });

  it('keeps year 100 and later as written, like the web app', () => {
    expect(parseDateStr('0100-01-01')?.getFullYear()).toBe(100);
    expect(parseDateStr('2026-10-07')?.getFullYear()).toBe(2026);
  });
});

describe('week start and backups', () => {
  it('uses the week start Intl reports when it is a weekday from 1 to 7, else the region', () => {
    expect(chooseFirstDay(1, 'US')).toBe(1);
    expect(chooseFirstDay(6, 'DE')).toBe(6);
    expect(chooseFirstDay(7, 'DE')).toBe(7);
    for (const odd of [undefined, 0, 8, 1.5, Number.NaN]) {
      expect(chooseFirstDay(odd, 'DE')).toBe(1);
      expect(chooseFirstDay(odd, 'US')).toBe(7);
    }
  });

  it('starts the week on Sunday in exactly the web app\'s Sunday regions when Intl can\'t say', () => {
    const sunday = ['US', 'CA', 'MX', 'JP', 'BR', 'IN', 'IL', 'AU', 'PH', 'KR', 'TW', 'HK', 'ZA', 'SA', 'AR', 'CO', 'PE', 'VE'];
    expect([...SUNDAY_FIRST_REGIONS].sort()).toEqual([...sunday].sort());
    for (const region of sunday) expect(chooseFirstDay(undefined, region)).toBe(7);
    for (const region of ['GB', 'DE', 'FR', 'NG', '']) expect(chooseFirstDay(undefined, region)).toBe(1);
  });

  it('reads no region from a locale it can\'t parse', () => {
    expect(localeRegion('xx-invalid-!!')).toBe('');
    expect(localeRegion('en-GB')).toBe('GB');
  });

  it('caps backups at 60 MiB and names its error', () => {
    expect(MAX_BACKUP_BYTES).toBe(62914560);
    expect(new BackupError('x').name).toBe('BackupError');
  });
});

describe('backups', () => {
  const route = (raw: unknown): CleanRoute | null => {
    const id = typeof raw === 'object' && raw !== null && 'id' in raw ? raw.id : undefined;
    return typeof id === 'string' ? { row: { id }, points: [] } : null;
  };
  const context = { now, labKeys: ['ldl'], settingKeys: ['grind_map_style'], cleanRoute: route };
  const profile = { name: 'A' };
  const check = (doc: Record<string, unknown>) => validateBackup({ profile, ...doc }, context);
  const junk = (count: number) => Array.from({ length: count }, () => null);

  it('accepts format versions 1 and 2 (or none), and refuses anything else', () => {
    for (const version of [undefined, null, 1, 2]) expect(() => check({ version })).not.toThrow();
    for (const version of [0, 3, 1.5, '1']) expect(() => check({ version })).toThrow(/newer \(or unknown\) version/);
  });

  it('accepts each list at its limit and refuses one record more', () => {
    const limits = { workouts: 5000, dailyLogs: 5000, labs: 1000, favorites: 2000, meals: 500, routes: 2000 };
    for (const [list, limit] of Object.entries(limits)) {
      expect(check({ [list]: junk(limit) }).rejected).toBe(limit);
      expect(() => check({ [list]: junk(limit + 1) })).toThrow('Backup is larger than the supported limits.');
    }
  });

  it('counts every dropped record, per list', () => {
    const result = check({
      workouts: [{ id: 'w1', date: '2026-10-07', exercises: [{ name: 'Squat', sets: [set(50, 5)] }] }, ...junk(1)],
      dailyLogs: [{ date: '2026-10-07' }, ...junk(2)],
      labs: [{ id: 'l1', date: '2026-10-07', biomarkers: { ldl: 90 } }, ...junk(3)],
      favorites: [{ id: 'f1', name: 'Rice', kcal: 130 }, ...junk(4)],
      meals: [{ id: 'm1', name: 'Lunch', items: [{ name: 'Egg', kcal: 78 }] }, ...junk(5)],
      routes: [{ id: 'r1' }, ...junk(6)],
    });
    expect(result.rejected).toBe(1 + 2 + 3 + 4 + 5 + 6);
  });

  it('keeps the first of two routes with the same id and counts the second as dropped', () => {
    const result = check({ routes: [{ id: 'r1' }, { id: 'r1' }, { id: 'r2' }, null] });
    expect(result.routes.map((r) => r.row.id)).toEqual(['r1', 'r2']);
    expect(result.rejected).toBe(2);
  });

  it('restores settings only when there are some and the app knows settings', () => {
    expect(check({ settings: { grind_map_style: 'dark' } }).settings).toEqual({ grind_map_style: 'dark' });
    expect(check({}).settings).toBeNull();
    expect(validateBackup({ profile, settings: { grind_map_style: 'dark' } }, { ...context, settingKeys: [] }).settings).toBeNull();
  });
});
