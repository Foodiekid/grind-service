// Rules that must hold for every input, checked on generated values (fast-check shrinks any failure to the
// smallest example). Complements the golden tests, which pin exact answers for chosen cases.
import fc from 'fast-check';
import { afterAll, describe, expect, it } from 'vitest';

import { addDays, firstDayOfWeek, isValidDateStr, localDateStr, parseDateStr, startOfWeekStr, type Weekday } from '@/core/util/dates.ts';
import { clamp, median, movingAverage, toNum } from '@/core/util/numbers.ts';
import { fromDisplayWeight, toDisplayWeight } from '@/core/util/units.ts';
import { runs } from '../../support/runs.ts';
import { testableZones } from '../../support/timeZone.ts';

// Zones with daylight saving both ways and a half-hour offset. Samoa is left out on purpose: it removed a whole day
// in 2011, so "+n then -n" can't return to the start there (covered by the golden tests).
const ZONES = testableZones(['America/Chicago', 'Europe/London', 'Australia/Sydney', 'Asia/Kolkata', 'UTC']);
const originalZone = process.env.TZ;
afterAll(() => {
  process.env.TZ = originalZone;
});

const pad = (n: number): string => String(n).padStart(2, '0');
/** Real calendar dates from 1950 to 2100, written as text. */
const dateText = fc
  .date({ min: new Date(Date.UTC(1950, 0, 1)), max: new Date(Date.UTC(2100, 11, 31)), noInvalidDate: true })
  .map((d) => `${String(d.getUTCFullYear())}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`);
/** Days between two calendar dates, counted on the calendar (no clocks involved). */
const dayNumber = (text: string): number => {
  const [y, m, d] = text.split('-').map(Number) as [number, number, number];
  return Date.UTC(y, m - 1, d) / 864e5;
};

describe.each(ZONES)('dates in %s', (zone) => {
  it('reads back every real date it writes', () => {
    process.env.TZ = zone;
    fc.assert(fc.property(dateText, (text) => {
      const parsed = parseDateStr(text);
      expect(parsed).not.toBeNull();
      expect(localDateStr(parsed as Date)).toBe(text);
    }), runs(100));
  });

  it('adds exactly n calendar days, and going back returns to the start', () => {
    process.env.TZ = zone;
    fc.assert(fc.property(dateText, fc.integer({ min: -3650, max: 3650 }), (text, n) => {
      const moved = addDays(text, n);
      expect(moved).not.toBeNull();
      expect(dayNumber(moved as string) - dayNumber(text)).toBe(n);
      expect(addDays(moved as string, -n)).toBe(text);
    }), runs(100));
  });

  it('starts every week on the chosen weekday, at most 6 days back', () => {
    process.env.TZ = zone;
    fc.assert(fc.property(dateText, fc.integer({ min: 1, max: 7 }), (text, firstDay) => {
      const start = startOfWeekStr(parseDateStr(text) as Date, firstDay as Weekday);
      const back = dayNumber(text) - dayNumber(start);
      expect(back).toBeGreaterThanOrEqual(0);
      expect(back).toBeLessThanOrEqual(6);
      expect((parseDateStr(start) as Date).getDay()).toBe(firstDay % 7);
    }), runs(100));
  });
});

describe('date text', () => {
  it('accepts only YYYY-MM-DD real dates, whatever the text', () => {
    fc.assert(fc.property(fc.string(), (text) => {
      if (isValidDateStr(text)) expect(text).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    }), runs(100));
  });

  it('gives no answer instead of a date for invalid text', () => {
    fc.assert(fc.property(fc.string().filter((t) => !isValidDateStr(t)), fc.integer(), (text, n) => {
      expect(addDays(text, n)).toBeNull();
    }), runs(100));
  });

  it('always picks a weekday from 1 to 7, for any locale text', () => {
    fc.assert(fc.property(fc.string(), (locale) => {
      expect(firstDayOfWeek(locale)).toBeGreaterThanOrEqual(1);
      expect(firstDayOfWeek(locale)).toBeLessThanOrEqual(7);
    }), runs(100));
  });
});

describe('units', () => {
  const kg = fc.double({ min: 0, max: 1500, noNaN: true, noDefaultInfinity: true });

  it('shows kilograms unchanged in kg', () => {
    fc.assert(fc.property(kg, (value) => {
      expect(toDisplayWeight(value, 'kg')).toBe(value);
      expect(fromDisplayWeight(value, 'kg')).toBe(value);
    }), runs(100));
  });

  it('lands within 0.03 kg after a trip through pounds (display rounding only)', () => {
    fc.assert(fc.property(kg, (value) => {
      const back = fromDisplayWeight(toDisplayWeight(value, 'lbs'), 'lbs') as number;
      expect(Math.abs(back - value)).toBeLessThanOrEqual(0.03);
    }), runs(100));
  });

  it('keeps missing weights missing', () => {
    expect(toDisplayWeight(null, 'lbs')).toBeNull();
    expect(fromDisplayWeight(null, 'lbs')).toBeNull();
  });
});

describe('numbers', () => {
  const finite = fc.double({ noNaN: true, noDefaultInfinity: true, min: -1e9, max: 1e9 });

  it('clamps into the range, and clamping twice changes nothing', () => {
    fc.assert(fc.property(finite, finite, finite, (n, a, b) => {
      const [low, high] = a <= b ? [a, b] : [b, a];
      const once = clamp(n, low, high);
      expect(once).toBeGreaterThanOrEqual(low);
      expect(once).toBeLessThanOrEqual(high);
      expect(clamp(once, low, high)).toBe(once);
    }), runs(100));
  });

  it('finds a median between the smallest and largest value, whatever the order', () => {
    fc.assert(fc.property(fc.array(finite, { minLength: 1, maxLength: 50 }), (values) => {
      const middle = median(values) as number;
      expect(middle).toBeGreaterThanOrEqual(Math.min(...values));
      expect(middle).toBeLessThanOrEqual(Math.max(...values));
      expect(median([...values].reverse())).toBe(middle);
    }), runs(100));
    expect(median([])).toBeNull();
  });

  it('averages each window within that window, one value per input', () => {
    fc.assert(fc.property(fc.array(finite, { maxLength: 60 }), fc.integer({ min: 1, max: 10 }), (values, window) => {
      const averages = movingAverage(values, window);
      expect(averages).toHaveLength(values.length);
      averages.forEach((average, i) => {
        const slice = values.slice(Math.max(0, i - window + 1), i + 1);
        expect(average).toBeGreaterThanOrEqual(Math.min(...slice) - 1e-6);
        expect(average).toBeLessThanOrEqual(Math.max(...slice) + 1e-6);
      });
    }), runs(100));
  });

  it('reads any finite number back as itself, and missing input as missing', () => {
    fc.assert(fc.property(finite, (n) => {
      expect(toNum(n)).toBe(n);
      // Text can't carry the sign of zero (String(-0) is '0'), so compare by value, where -0 equals 0.
      expect(toNum(String(n)) === n).toBe(true);
    }), runs(100));
    for (const missing of ['', null, undefined]) expect(toNum(missing)).toBeNull();
  });
});
