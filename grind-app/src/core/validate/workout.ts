/** A strength workout: exercises, their sets, and the session's clock times. From `backup.js` (`sanitizeWorkout`). */

import { isValidDateStr, type DateStr } from '../util/dates.ts';
import type { WeightUnit } from '../util/units.ts';
import { createdAt, field, idOk, isObject, str, textOr } from './fields.ts';
import { isRecordSource, type RecordSource } from './ranges.ts';

export interface WorkoutSet {
  weight: number;
  reps: number;
  completed: boolean;
  /** How hard the set felt, 1 to 10 in half steps; absent when not given (missing is not zero). */
  rpe?: number;
}

export interface Exercise {
  name: string;
  sets: WorkoutSet[];
}

export interface Workout {
  id: string;
  date: DateStr;
  splitDay: string;
  durationMinutes: number;
  unit: WeightUnit;
  exercises: Exercise[];
  createdAt: string;
  source?: RecordSource;
  /** ISO times, kept only as a valid pair: start before end, at most a day apart. */
  startedAt?: string;
  endedAt?: string;
}

const DAY_MS = 864e5;
const MAX_REPS = 1000;

function cleanSet(raw: unknown, maxWeight: number): WorkoutSet | null {
  const weight = Number(field(raw, 'weight'));
  const reps = Number(field(raw, 'reps'));
  const isPlausibleWeight = Number.isFinite(weight) && weight >= 0 && weight <= maxWeight;
  const isPlausibleReps = Number.isFinite(reps) && reps >= 0 && reps <= MAX_REPS;
  if (!isPlausibleWeight || !isPlausibleReps) return null;
  const set: WorkoutSet = { weight, reps: Math.round(reps), completed: field(raw, 'completed') !== false };
  const rpe = Number(field(raw, 'rpe'));
  if (rpe >= 1 && rpe <= 10) set.rpe = Math.round(rpe * 2) / 2;
  return set;
}

export function sanitizeWorkout(raw: unknown, now: Date): Workout | null {
  if (!isObject(raw) || !idOk(raw.id) || typeof raw.date !== 'string' || !isValidDateStr(raw.date)) return null;
  const unit: WeightUnit = raw.unit === 'lbs' ? 'lbs' : 'kg';
  const maxWeight = unit === 'lbs' ? 3300 : 1500;
  const exercises: Exercise[] = [];
  for (const entry of Array.isArray(raw.exercises) ? raw.exercises.slice(0, 60) : []) {
    const name = str(field(entry, 'name'), 80).trim();
    if (name === '') continue;
    const rawSets = field(entry, 'sets');
    const sets = (Array.isArray(rawSets) ? rawSets.slice(0, 60) : [])
      .map((set) => cleanSet(set, maxWeight))
      .filter((set): set is WorkoutSet => set !== null);
    if (sets.length > 0) exercises.push({ name, sets });
  }
  if (exercises.length === 0) return null;
  const duration = Number(raw.durationMinutes);
  const out: Workout = {
    id: raw.id,
    date: raw.date,
    splitDay: textOr(str(raw.splitDay, 80), 'Workout'),
    durationMinutes: Number.isFinite(duration) && duration >= 0 && duration <= 1440 ? Math.round(duration) : 0,
    unit,
    exercises,
    createdAt: createdAt(raw.createdAt, now),
  };
  if (isRecordSource(raw.source)) out.source = raw.source;
  if (typeof raw.startedAt === 'string' && typeof raw.endedAt === 'string') {
    const start = Date.parse(raw.startedAt);
    const end = Date.parse(raw.endedAt);
    // Date.parse gives NaN for anything unreadable, and every comparison with NaN is false.
    const isValidSession = end > start && end - start <= DAY_MS;
    if (isValidSession) {
      out.startedAt = new Date(start).toISOString();
      out.endedAt = new Date(end).toISOString();
    }
  }
  return out;
}
