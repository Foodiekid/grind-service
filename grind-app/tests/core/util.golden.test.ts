// The TypeScript port of the web app's util.js must give the same answers, in every time zone and locale in the
// fixture (scripts/golden/util.mjs runs the original JS to make it).
import { afterAll, describe, expect, it } from 'vitest';

import fixture from '../golden/fixtures/util.json';
import { testableZones } from '../support/timeZone.ts';
import { addDays, firstDayOfWeek, localDateStr, parseDateStr, startOfWeekStr } from '@/core/util/dates.ts';
import { clamp, median, movingAverage, toNum } from '@/core/util/numbers.ts';
import {
  defaultWeightUnit,
  formatVolume,
  fromDisplayWeight,
  toDisplayWeight,
  usesImperialVolume,
  type WeightUnit,
} from '@/core/util/units.ts';

const originalZone = process.env.TZ;

/** JSON has no -0 (it writes 0), so the fixture can't tell them apart; neither do these comparisons. */
const json = (value: unknown): unknown => (Object.is(value, -0) ? 0 : value);

const date = (text: string): Date => {
  const parsed = parseDateStr(text);
  if (!parsed) throw new Error(`fixture date ${text} is not a date`);
  return parsed;
};
afterAll(() => {
  process.env.TZ = originalZone;
});

const zones = testableZones(Object.keys(fixture.timeZones) as (keyof typeof fixture.timeZones)[]);

describe.each(zones.map((zone) => [zone, fixture.timeZones[zone]] as const))('dates in %s', (zone, cases) => {
  it('parses exactly the real calendar dates', () => {
    process.env.TZ = zone;
    for (const [text, expected] of cases.parse) {
      const parsed = parseDateStr(text);
      expect(parsed ? localDateStr(parsed) : null, String(text)).toBe(expected);
    }
  });

  it('adds days like the web app, across daylight-saving changes', () => {
    process.env.TZ = zone;
    for (const [date, delta, expected] of cases.addDays) {
      expect(addDays(date as string, delta as number), `${String(date)} ${String(delta)}`).toBe(expected);
    }
  });
});

describe.each(fixture.locales)('locale $locale', (locale) => {
  it('picks the same week start and units', () => {
    process.env.TZ = 'America/Chicago';
    const firstDay = firstDayOfWeek(locale.locale);
    expect(firstDay).toBe(locale.firstDayOfWeek);
    expect(defaultWeightUnit(locale.locale)).toBe(locale.weightUnit);
    expect(usesImperialVolume(locale.locale)).toBe(locale.imperialVolume);
    for (const [dateText, expected] of locale.startOfWeek) {
      expect(startOfWeekStr(date(dateText as string), firstDay), String(dateText)).toBe(expected);
    }
  });
});

describe('units and numbers', () => {
  it('converts weights with the same rounding', () => {
    for (const [kg, unit, expected] of fixture.toDisplayWeight) {
      expect(toDisplayWeight(kg as number | null, unit as WeightUnit)).toBe(expected);
    }
    for (const [value, unit, expected] of fixture.fromDisplayWeight) {
      expect(fromDisplayWeight(value as number | null, unit as WeightUnit)).toBe(expected);
    }
  });

  it('formats volumes the same way', () => {
    for (const [ml, imperial, expected] of fixture.formatVolume) {
      expect(formatVolume(ml as number, imperial as boolean)).toBe(expected);
    }
  });

  it('reads numbers, medians, averages and limits the same way', () => {
    for (const [value, expected] of fixture.toNum) {
      expect(json(toNum(value === '__undefined__' ? undefined : value)), String(value)).toBe(expected);
    }
    for (const [values, expected] of fixture.median) {
      expect(median(values as number[])).toBe(expected);
    }
    for (const [values, window, expected] of fixture.movingAverage) {
      expect(movingAverage(values as number[], window as number)).toEqual(expected);
    }
    for (const [n, min, max, expected] of fixture.clamp) {
      expect(clamp(n as number, min as number, max as number)).toBe(expected);
    }
  });
});
