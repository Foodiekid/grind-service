/** A day's log: body measures, foods eaten, journal notes and tags. From the web app's `backup.js` (`sanitizeLog`). */

import { isValidDateStr, type DateStr } from '../util/dates.ts';
import { field, hash, idOk, inRange, isObject, presentNumber, roundTo1, str } from './fields.ts';
import { copyExtras, copyMacros, foodKcal, type Macros, type NutritionExtras } from './food.ts';
import {
  FOOD_MACROS, LOG_RANGES, WHOLE_LOG_FIELDS, isFoodSource, isJournalTag, isMeal, isRecordSource,
  type FoodSource, type JournalTag, type LogField, type Meal, type RecordSource,
} from './ranges.ts';

export interface FoodEntry extends Macros, NutritionExtras {
  id: string;
  name: string;
  kcal: number;
  meal: Meal;
  /** `HH:MM`, 24-hour. */
  time?: string;
  label?: string;
  /** Servings eaten, 0.25 to 20. */
  qty?: number;
  source?: FoodSource;
  portion?: string;
  grams?: number;
}

export type DailyLog = Partial<Record<LogField, number>> & {
  date: DateStr;
  foods?: FoodEntry[];
  notes?: string;
  tags?: JournalTag[];
  source?: RecordSource;
};

/** Daily totals are capped at the log's own limits. */
const DAY_MACRO_LIMITS = { protein: 1000, carbs: 2000, fat: 1000 } as const;

function cleanFood(raw: unknown, index: number): FoodEntry | null {
  const name = str(field(raw, 'name'), 120).trim();
  const kcal = foodKcal(field(raw, 'kcal'));
  if (!name || kcal === undefined || !isObject(raw)) return null;
  const meal = raw.meal;
  const food: FoodEntry = {
    id: idOk(raw.id) ? raw.id : `f_${index}_${Math.abs(hash(name + kcal))}`,
    name,
    kcal: Math.round(kcal),
    meal: isMeal(meal) ? meal : 'snack',
  };
  copyMacros(raw, food);
  if (typeof raw.time === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(raw.time)) food.time = raw.time;
  const label = str(raw.label, 30).trim();
  if (label) food.label = label;
  const qty = presentNumber(raw.qty, 0.25, 20);
  if (qty !== undefined) food.qty = qty;
  if (isFoodSource(raw.source)) food.source = raw.source;
  copyExtras(raw, food);
  const portion = str(raw.portion, 60).trim();
  if (portion) food.portion = portion;
  const grams = Number(raw.grams);
  if (raw.grams != null && Number.isFinite(grams) && grams > 0 && grams <= 10000) food.grams = Math.round(grams);
  return food;
}

export function sanitizeLog(raw: unknown): DailyLog | null {
  if (!isObject(raw) || typeof raw.date !== 'string' || !isValidDateStr(raw.date)) return null;
  const out: DailyLog = { date: raw.date };
  for (const [key, range] of Object.entries(LOG_RANGES) as [LogField, readonly [number, number]][]) {
    // Note: as in the web app, an empty string reads as 0 here (Number('') is 0).
    const value = raw[key] == null ? null : Number(raw[key]);
    if (value != null && inRange(value, range)) out[key] = WHOLE_LOG_FIELDS.has(key) ? Math.round(value) : value;
  }
  if (Array.isArray(raw.foods)) {
    const foods: FoodEntry[] = [];
    for (const entry of raw.foods.slice(0, 120)) {
      const food = cleanFood(entry, foods.length);
      if (food) foods.push(food);
    }
    if (foods.length) {
      out.foods = foods;
      out.calories = Math.min(20000, foods.reduce((sum, food) => sum + food.kcal, 0));
      for (const [key] of FOOD_MACROS) {
        const withValue = foods.filter((food) => food[key] != null);
        if (withValue.length) {
          const total = withValue.reduce((sum, food) => sum + (food[key] ?? 0), 0);
          out[key] = Math.min(DAY_MACRO_LIMITS[key], roundTo1(total));
        }
      }
    }
  }
  if (typeof raw.notes === 'string' && raw.notes) out.notes = str(raw.notes, 2000);
  if (Array.isArray(raw.tags)) {
    const tags = [...new Set(raw.tags.filter(isJournalTag))];
    if (tags.length) out.tags = tags;
  }
  if (isRecordSource(raw.source)) out.source = raw.source;
  return out;
}
