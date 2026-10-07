// Golden fixtures for core/util: runs the web app's original util.js over edge cases and writes the answers to
// tests/golden/fixtures/util.json, which the TypeScript port must reproduce (tests/core/util.golden.test.ts).
//
// Run only when the cases change:  GRIND_WEB_APP=~/Documents/Grind node scripts/golden/util.mjs
// The fixture is committed, so the tests never need the web app.

import { writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const webApp = process.env.GRIND_WEB_APP ?? join(homedir(), 'Documents', 'Grind');
const util = await import(pathToFileURL(join(webApp, 'src/js/lib/util.js')).href);

const setLocale = (language) =>
  Object.defineProperty(globalThis, 'navigator', { value: { language }, configurable: true });

export const TIME_ZONES = ['America/Chicago', 'Europe/London', 'Australia/Sydney', 'Asia/Kolkata', 'Pacific/Apia'];
const DATE_TEXTS = ['2026-10-07', '2024-02-29', '2023-02-29', '2026-02-30', '2026-13-01', '2026-2-3', '', null, '2011-12-30', 'x'];
const ADD_DAYS = [
  ['2026-03-07', 1], ['2026-03-08', 1], ['2026-03-28', 2], ['2026-10-03', 2], ['2026-11-01', 1], ['2026-12-31', 1],
  ['2024-02-28', 1], ['2026-01-31', 30], ['2026-03-01', -1], ['2011-12-29', 1], ['2011-12-29', 2], ['2026-10-07', -400],
];
const WEEK_DATES = ['2026-10-07', '2026-10-04', '2026-10-05', '2026-10-10', '2026-01-01'];
const LOCALES = ['en-US', 'en-GB', 'de-DE', 'ar-SA', 'fa-IR', 'he-IL', 'en-IN', 'ja-JP', 'pt-BR', 'xx-invalid', 'my-MM'];

const byZone = {};
for (const zone of TIME_ZONES) {
  process.env.TZ = zone;
  setLocale('en-US');
  byZone[zone] = {
    parse: DATE_TEXTS.map((text) => [text, util.parseDateStr(text) ? util.localDateStr(util.parseDateStr(text)) : null]),
    addDays: ADD_DAYS.map(([date, delta]) => [date, delta, util.addDays(date, delta)]),
  };
}

process.env.TZ = 'America/Chicago';
const locales = LOCALES.map((language) => {
  setLocale(language);
  return {
    locale: language,
    firstDayOfWeek: util.firstDayOfWeek(),
    weightUnit: util.defaultWeightUnit(),
    imperialVolume: util.usesImperialVolume(),
    startOfWeek: WEEK_DATES.map((date) => [date, util.startOfWeekStr(util.parseDateStr(date))]),
  };
});

const weights = [0, 0.1, 1, 2.5, 20, 60.33, 82.5, 100, 142.86, 250, null];
const fixture = {
  source: 'web app src/js/lib/util.js (v5.41.3)',
  timeZones: byZone,
  locales,
  toDisplayWeight: weights.flatMap((kg) => ['kg', 'lbs'].map((unit) => [kg, unit, util.toDisplayWeight(kg, unit)])),
  fromDisplayWeight: [0, 1, 5, 45, 135, 181.9, 225.5, 315, 500, null]
    .flatMap((value) => ['kg', 'lbs'].map((unit) => [value, unit, util.fromDisplayWeight(value, unit)])),
  formatVolume: [0, 1, 250, 499.5, 999, 1000, 1234, 2500, 3785.41]
    .flatMap((ml) => [true, false].map((imperial) => [ml, imperial, util.formatVolume(ml, imperial)])),
  toNum: ['', null, undefined, '12', ' 7 ', '3.5', 'abc', '1e3', 'Infinity', '-0', 0, 42]
    .map((value) => [value === undefined ? '__undefined__' : value, util.toNum(value)]),
  median: [[], [5], [3, 1], [1, 9, 4], [2, 2, 8, 6], [-1, 0.5, 10, 3, 3]].map((values) => [values, util.median(values)]),
  movingAverage: [[[1, 2, 3, 4, 5, 6], 3], [[10], 5], [[2, 4, 6, 8, 10, 12, 14], 5], [[], 5]]
    .map(([values, window]) => [values, window, util.movingAverage(values, window)]),
  clamp: [[5, 0, 10], [-3, 0, 10], [12, 0, 10], [7.5, 7.5, 7.5]].map(([n, min, max]) => [n, min, max, util.clamp(n, min, max)]),
};

const out = new URL('../../tests/golden/fixtures/util.json', import.meta.url);
writeFileSync(out, `${JSON.stringify(fixture, null, 2)}\n`);
console.log(`wrote ${out.pathname}`);
