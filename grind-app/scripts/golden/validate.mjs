// Golden fixtures for core/validate: runs the web app's original backup.js and syncSanitize.js over hand-picked edge
// cases plus seeded random records, and writes tests/golden/fixtures/validate.json.
//
// Run only when the cases change:  GRIND_WEB_APP=~/Documents/Grind node scripts/golden/validate.mjs

import { writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

import { encode } from './encoding.mjs';

const NOW = '2026-10-07T12:00:00.000Z';
// The original stamps createdAt with new Date(); pin that clock so its answers are repeatable.
const RealDate = Date;
globalThis.Date = class extends RealDate {
  constructor(...args) { super(...(args.length ? args : [NOW])); }
  static now() { return RealDate.parse(NOW); }
};

const webApp = process.env.GRIND_WEB_APP ?? join(homedir(), 'Documents', 'Grind');
const load = (path) => import(pathToFileURL(join(webApp, path)).href);
const backup = await load('src/js/shell/backup.js');
const { sanitizeForSync } = await load('src/cloud/syncSanitize.js');
const { BIOMARKER_KEYS } = await load('src/js/lib/biomarkerEngine.js');
const { SETTING_KEYS } = await load('src/cloud/localStores.js');

// Seeded random numbers (mulberry32): the same cases on every run.
let seed = 20261007;
const random = () => {
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};
const pick = (values) => values[Math.floor(random() * values.length)];
const maybe = (p) => random() < p;

const NUMBERS = [0, -1, 0.25, 0.5, 1, 5, 7.3, 7.75, 10, 10.5, 11, 20, 24.5, 25, 60, 100, 200.04, 400, 401, 500, 1000,
  1440, 1441, 1500, 1501, 3300, 5000, 5001, 10000, 20000, 200001, Number.NaN, Infinity, -Infinity, -0];
const NUMBER_TEXT = ['', ' ', '12', '7.5', 'abc', '1e3', '0x10', '-3'];
const ODD = [null, undefined, true, false, [], [5], {}];
// Mostly plausible values (so the paths that keep data are exercised too), with a steady share of awkward ones.
const usually = (good, bad, p = 0.7) => () => (maybe(p) ? pick(good) : pick(bad));
const PLAUSIBLE = [0.5, 1, 5, 7.3, 7.75, 10, 24.5, 52, 60, 82.5, 100, 140, 250];
const anyNumber = () => (maybe(0.55) ? pick(PLAUSIBLE) : maybe(0.6) ? pick(NUMBERS) : maybe(0.6) ? pick(NUMBER_TEXT) : pick(ODD));
const DATES = ['2026-10-07', '2024-02-29', '2023-02-29', '2026-02-30', '2026-2-3', '', ['2026-10-07'], 20261007, null];
const anyDate = usually(['2026-10-07', '2026-10-06', '2024-02-29'], DATES);
const IDS = ['w1', 'a.b-c_1', 'has space', '', 'x'.repeat(80), 'x'.repeat(81), 5, null, 'f_0_123'];
const anyId = usually(['w1', 'a.b-c_1', 'x'.repeat(80), 'f_0_123', 'lab-2026'], IDS);
const TEXT = ['Squat', '  Bench Press  ', '', '   ', 'x'.repeat(200), '<script>alert(1)</script>', 'Ünïcødé 💪', 42, null];
const anyText = usually(['Squat', '  Bench Press  ', 'Ünïcødé 💪', 'Oats'], TEXT);
const TIMES = ['07:30', '23:59', '24:00', '7:30', '12:60', 'noon', 730];
const ISO = ['2026-10-07T10:00:00Z', '2026-10-07T11:15:00.000Z', '2026-10-08T10:00:01Z', '2026-10-07T09:00:00+02:00', 'yesterday', '', 5];

const fields = (spec) => Object.fromEntries(Object.entries(spec).filter(() => maybe(0.75)).map(([key, make]) => [key, make()]));
const listOf = (make, max) => Array.from({ length: Math.floor(random() * max) }, make);

const randomFood = () => fields({
  id: anyId, name: anyText, kcal: anyNumber, meal: () => pick(['breakfast', 'lunch', 'dinner', 'snack', 'brunch', 3]),
  protein: anyNumber, carbs: anyNumber, fat: anyNumber, time: () => pick(TIMES), label: anyText, qty: anyNumber,
  source: () => pick(['usda', 'off', 'label', 'myfitnesspal', null]), fiber: anyNumber, sugar: anyNumber, sodium: anyNumber,
  satFat: anyNumber, portion: anyText, grams: anyNumber,
});
const randomLog = () => ({
  ...fields(Object.fromEntries(Object.keys(backup.LOG_RANGES).map((key) => [key, anyNumber]))),
  date: anyDate(),
  ...fields({
    foods: () => (maybe(0.8) ? listOf(() => (maybe(0.9) ? randomFood() : pick(ODD)), 6) : pick(ODD)),
    notes: anyText, tags: () => listOf(() => pick(['sore', 'ill', 'travel', 'stress', 'sore', 'happy', 3]), 5),
    source: () => pick(['manual', 'oura', 'apple-health', 'garmin', null]),
  }),
});
const setNumber = () => (maybe(0.75) ? pick(PLAUSIBLE) : anyNumber());
const randomSet = () => (maybe(0.9) ? fields({ weight: setNumber, reps: setNumber, completed: () => pick([true, false, 'no', undefined]), rpe: anyNumber }) : pick(ODD));
const randomWorkout = () => ({
  id: anyId(), date: anyDate(),
  ...fields({
    unit: () => pick(['kg', 'lbs', 'LBS', null]), splitDay: anyText, durationMinutes: anyNumber,
    exercises: () => listOf(() => (maybe(0.9) ? { name: anyText(), sets: listOf(randomSet, 5) } : pick(ODD)), 4),
    createdAt: () => pick(['2026-10-01T08:00:00Z', 'x'.repeat(60), 5]), source: () => pick(['manual', 'whoop', 'strava']),
    startedAt: () => pick(ISO), endedAt: () => pick(ISO),
  }),
});
const randomLab = () => ({
  id: anyId(), date: anyDate(),
  ...fields({
    labName: anyText,
    biomarkers: () => (maybe(0.85) ? Object.fromEntries(listOf(() => [pick([...BIOMARKER_KEYS, 'unknown_marker']), anyNumber()], 6)) : pick(ODD)),
    qualifiers: () => Object.fromEntries(listOf(() => [pick(BIOMARKER_KEYS), pick(['<', '>', '≤', '≥', '=', 5])], 3)),
    details: () => Object.fromEntries(listOf(() => [pick(BIOMARKER_KEYS), maybe(0.8) ? fields({ raw: anyText, unit: () => pick(['mg/dL', 'x'.repeat(30)]), ev: anyText, edited: () => pick([true, false, 'yes']) }) : pick(ODD)], 3)),
    reportedDate: anyDate, source: () => pick(['pdf', 'csv', 'lab']), createdAt: () => pick(['2026-10-01T08:00:00Z', 7]),
  }),
});
const randomPortions = () => listOf(() => pick([['1 cup', 240], ['slice', '30'], ['x'.repeat(70), 12.34], ['big', 6000], [5, 10], 'cup', ['zero', 0]]), 10);
const randomFavorite = () => ({
  id: anyId(),
  ...fields({
    name: anyText, kcal: anyNumber, source: () => pick(['usda', 'chain', 'other']), protein: anyNumber, carbs: anyNumber,
    fat: anyNumber, serving: anyText, fiber: anyNumber, sodium: anyNumber, g: anyNumber, pt: () => (maybe(0.8) ? randomPortions() : pick(ODD)),
    createdAt: () => pick(['2026-10-01T08:00:00Z', 7]),
  }),
});
const randomMeal = () => ({
  id: anyId(),
  ...fields({
    name: anyText,
    items: () => (maybe(0.85) ? listOf(() => (maybe(0.9) ? fields({ name: anyText, kcal: anyNumber, protein: anyNumber, fat: anyNumber, source: () => pick(['usda', 'x']), sugar: anyNumber }) : pick(ODD)), 5) : pick(ODD)),
    createdAt: () => pick(['2026-10-01T08:00:00Z', 7]),
  }),
});
const AVATARS = ['data:image/png;base64,iVBORw0KGgo=', 'data:image/gif;base64,R0lGOD==', 'https://example.com/a.png', 'data:image/webp;base64,UklG$$', null];
const randomProfile = () => (maybe(0.9) ? fields({ name: anyText, goal: anyText, avatarUrl: () => pick(AVATARS) }) : pick(ODD));
const SETTING_VALUES = ['{}', '[]', 'null', '', '{"Push":["Bench Press"]}', 'not json', '5', '"text"', 'lose', 'gain', 'bulk', '1', '0', 'yes',
  'satellite', 'neon', 7, null];
const GOOD_SETTING = { grind_goals: '{"steps":10000}', grind_fuel: '{}', grind_splits: '{"Push":["Bench Press"]}', grind_custom_ex: '[]',
  grind_weight_target: 'null', grind_weight_goal: 'lose', grind_progress: '{}', grind_route_goal: '', grind_map_optin: '1', grind_map_style: 'auto' };
const settingValue = (key) => (maybe(0.85) ? (GOOD_SETTING[key] ?? '{}') : pick(SETTING_VALUES));
const randomSettings = () => ({ values: maybe(0.9) ? Object.fromEntries(listOf(() => { const key = pick([...SETTING_KEYS, 'grind_unknown']); return [key, settingValue(key)]; }, 4)) : pick(ODD) });

const FUZZ = 150;
const run = (make, sanitize) => Array.from({ length: FUZZ }, () => {
  const input = make();
  return { input, output: sanitize(input) };
});

// Where the port deliberately differs: the web app accepted a non-text date that only looked right as text (for
// example ['2026-10-07']) and stored it as is; the port refuses it. The fixture records the port's expected answer.
let deviations = 0;
const strictDates = (input, output) => {
  if (output && 'date' in output && typeof output.date !== 'string') { deviations += 1; return null; }
  if (output && 'reportedDate' in output && typeof output.reportedDate !== 'string') {
    deviations += 1;
    const { reportedDate: _dropped, ...rest } = output;
    return rest;
  }
  return output;
};
const runStrict = (make, sanitize) => run(make, sanitize).map(({ input, output }) => ({ input, output: strictDates(input, output) }));

const HAND = {
  log: [
    { date: '2026-10-07', weight: 82.5, steps: '9000.6', sleep: 7.25, rhr: 52, mood: 7.6, foods: [{ name: 'Oats', kcal: 389.4, protein: 16.9, meal: 'breakfast', time: '07:30' }, { name: 'Egg', kcal: 78, protein: 6.3 }], tags: ['sore', 'sore', 'happy'] },
    { date: '2026-10-07', steps: '' },
    { date: '2026-10-07', foods: [{ name: 'Huge', kcal: 5000, protein: 500 }, { name: 'Huge', kcal: 5000, protein: 500 }, { name: 'Huge', kcal: 5000, protein: 500 }, { name: 'Huge', kcal: 5000, protein: 500 }, { name: 'Huge', kcal: 5000, protein: 500 }] },
  ],
  workout: [
    { id: 'w1', date: '2026-10-06', unit: 'kg', exercises: [{ name: 'Squat', sets: [{ weight: 100, reps: 5, rpe: 8 }, { weight: 100, reps: 5, rpe: 7.3 }, { weight: 100, reps: 5, rpe: 11 }, { weight: 100, reps: 5, rpe: 'x' }, { weight: 100, reps: 5 }] }] },
    { id: 'w2', date: '2026-10-06', unit: 'lbs', exercises: [{ name: 'Deadlift', sets: [{ weight: 3300, reps: 1 }, { weight: 3301, reps: 1 }] }], startedAt: '2026-10-06T10:00:00Z', endedAt: '2026-10-07T10:00:00Z' },
  ],
};

const SYNC_KINDS = [['dailyLogs', 'dailyLogs'], ['workouts', 'workouts'], ['healthData', 'labs'], ['favoriteFoods', 'favoriteFoods'], ['savedMeals', 'savedMeals'], ['profile', 'profile'], ['settings', 'settings']];
const sync = [];
for (const [store, kind] of SYNC_KINDS) {
  const make = { dailyLogs: randomLog, workouts: randomWorkout, healthData: randomLab, favoriteFoods: randomFavorite, savedMeals: randomMeal, profile: randomProfile, settings: randomSettings }[store];
  for (let i = 0; i < 25; i += 1) {
    const value = make();
    const ownKey = { dailyLogs: value?.date, profile: 'current_user', settings: 'main' }[store] ?? value?.id;
    const key = (maybe(0.75) ? ownKey : pick(['current_user', 'main', 'other', '2026-10-07'])) ?? 'other';
    sync.push({ kind, key: String(key), value, output: strictDates(value, sanitizeForSync(store, String(key), value)) });
  }
}

const backups = [
  {}, [], 'text', { format: 'other' }, { version: 3 }, { version: 1.5 }, { encrypted: true },
  { format: 'grind-backup', version: 2, settings: { grind_splits: '{"Push":["Bench Press"]}', grind_weight_goal: 'gain', grind_custom_ex: '[]', grind_unknown: 'x' } },
  { format: 'grind-backup', version: 2, dailyLogs: [{ date: '2026-10-01', weight: 80 }], settings: { grind_splits: 'not json', grind_weight_goal: 'gain' } },
  { format: 'grind-backup', version: 2, settings: { grind_weight_goal: 'bulk' } },
  { format: 'grind-backup', version: 2, dailyLogs: [{ date: '2026-10-01', weight: 80 }], settings: { grind_goals: `{"a":"${'x'.repeat(99990)}"}` } },
  { format: 'grind-backup', version: 2, dailyLogs: [{ date: '2026-10-01', weight: 80 }], settings: { grind_goals: `{"a":"${'x'.repeat(99993)}"}` } },
  { version: 2, dailyLogs: { a: { date: '2026-10-01', weight: 80 }, b: { date: 'bad' } }, workouts: Array.from({ length: 6 }, randomWorkout), labs: Array.from({ length: 4 }, randomLab), favorites: Array.from({ length: 4 }, randomFavorite), meals: Array.from({ length: 3 }, randomMeal), profile: { name: 'Sam' } },
  { workouts: Array.from({ length: 5001 }, () => ({})) },
].map((doc) => {
  try { return { doc, result: backup.validateBackup(doc, BIOMARKER_KEYS, SETTING_KEYS) }; } catch (e) { return { doc, error: e.message }; }
});

const fixture = {
  source: 'web app src/js/shell/backup.js + src/cloud/syncSanitize.js (v5.41.3)',
  now: NOW,
  labKeys: BIOMARKER_KEYS,
  settingKeys: SETTING_KEYS,
  log: [...HAND.log.map((input) => ({ input, output: backup.sanitizeLog(input) })), ...runStrict(randomLog, backup.sanitizeLog)],
  workout: [...HAND.workout.map((input) => ({ input, output: backup.sanitizeWorkout(input) })), ...runStrict(randomWorkout, backup.sanitizeWorkout)],
  lab: runStrict(randomLab, (input) => backup.sanitizeLab(input, BIOMARKER_KEYS)),
  favorite: run(randomFavorite, backup.sanitizeFavorite),
  meal: run(randomMeal, backup.sanitizeMeal),
  profile: run(randomProfile, backup.sanitizeProfile),
  settings: run(randomSettings, (input) => backup.sanitizeSettings(input, SETTING_KEYS)),
  sync,
  backups,
};
fixture.deviations = deviations;

const out = new URL('../../tests/golden/fixtures/validate.json', import.meta.url);
writeFileSync(out, `${JSON.stringify(encode(fixture))}\n`);
const kept = (cases) => cases.filter((c) => c.output).length;
console.log(`wrote ${out.pathname}: log ${kept(fixture.log)}/${fixture.log.length} kept, workout ${kept(fixture.workout)}/${fixture.workout.length}, lab ${kept(fixture.lab)}/${fixture.lab.length}, favorite ${kept(fixture.favorite)}/${fixture.favorite.length}, meal ${kept(fixture.meal)}/${fixture.meal.length}, settings ${kept(fixture.settings)}/${fixture.settings.length}, sync ${kept(sync)}/${sync.length}, deliberate differences ${deviations}`);
