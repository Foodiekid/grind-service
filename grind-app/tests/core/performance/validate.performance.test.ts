// Heavy-user budgets for the data checks: a synced record is cleaned in well under a millisecond, and a 5-year backup
// (2,000 days with foods, 1,500 workouts) meets the background-job budget (it runs in a Web Worker with progress
// during an import, never on the main thread). Budgets are on p95 and p99, measured on a laptop; phones are about 4x
// slower.
import { describe, expect, it } from 'vitest';

import { sanitizeSyncedRecord, validateBackup } from '@/core/validate/backup.ts';
import { budget, describePercentiles, measure } from '../../support/perf.ts';

const now = new Date('2026-10-07T12:00:00.000Z');
const context = { now, labKeys: ['ldl', 'hdl'], settingKeys: ['grind_goals'] };
const pad = (n: number): string => String(n).padStart(2, '0');
const day = (i: number): string => {
  const d = new Date(Date.UTC(2021, 0, 1 + i));
  return `${String(d.getUTCFullYear())}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
};

const log = (i: number) => ({
  date: day(i), weight: 80 + (i % 50) / 10, steps: 8000 + i, sleep: 7.5, rhr: 52, mood: 7, notes: 'Felt good',
  tags: ['sore'],
  foods: Array.from({ length: 15 }, (_, f) => ({ id: `f${String(i)}_${String(f)}`, name: `Food ${String(f)}`, kcal: 120 + f, protein: 8.5, carbs: 12.3, fat: 4.1, meal: 'lunch', time: '12:30', grams: 150 })),
});
const workout = (i: number) => ({
  id: `w${String(i)}`, date: day(i), unit: 'kg', splitDay: 'Push', durationMinutes: 62, createdAt: '2026-01-01T10:00:00Z',
  exercises: Array.from({ length: 6 }, (_, e) => ({ name: `Exercise ${String(e)}`, sets: Array.from({ length: 4 }, () => ({ weight: 80, reps: 8, rpe: 8 })) })),
});

describe('data checks at heavy-user scale', () => {
  it('cleans one synced day with 15 foods: p95 under 0.5 ms, p99 under 1 ms', () => {
    const record = log(100);
    const result = measure(() => sanitizeSyncedRecord('dailyLogs', record.date, record, context), 500);
    console.info(describePercentiles('sync one day', result));
    expect(result.p95).toBeLessThan(budget(0.5));
    expect(result.p99).toBeLessThan(budget(1));
  });

  it('checks a 5-year backup (2,000 days, 30,000 foods, 1,500 workouts): p95 under 500 ms, p99 under 1 s', () => {
    const backup = {
      format: 'grind-backup', version: 2,
      dailyLogs: Array.from({ length: 2000 }, (_, i) => log(i)),
      workouts: Array.from({ length: 1500 }, (_, i) => workout(i)),
    };
    const result = measure(() => validateBackup(backup, { ...context, cleanRoute: () => null }), 20, 3);
    console.info(describePercentiles('5-year backup', result));
    expect(result.p95).toBeLessThan(budget(500));
    expect(result.p99).toBeLessThan(budget(1000));
  });
});
