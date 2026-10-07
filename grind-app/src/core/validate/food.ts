/** Foods: saved favourites, saved meals, and the pieces daily log food entries share with them. From `backup.js`. */

import { createdAt, field, idOk, isObject, presentNumber, roundTo1, str } from './fields.ts';
import { FOOD_MACROS, NUTRITION_EXTRAS, isFoodSource, type FoodSource, type Macro, type NutritionExtra } from './ranges.ts';

export type Macros = Partial<Record<Macro, number>>;
export type NutritionExtras = Partial<Record<NutritionExtra, number>>;

/** A serving size: label and grams, e.g. `['1 cup', 240]`. */
export type Portion = [label: string, grams: number];

export interface FavoriteFood extends Macros, NutritionExtras {
  id: string;
  name: string;
  kcal: number;
  source: FoodSource;
  serving?: string;
  /** Grams the macros refer to. */
  g?: number;
  pt?: Portion[];
  createdAt: string;
}

export interface MealItem extends Macros, NutritionExtras {
  name: string;
  kcal: number;
  source?: FoodSource;
}

export interface SavedMeal {
  id: string;
  name: string;
  items: MealItem[];
  createdAt: string;
}

/** Kilocalories of one food: a number from 0 to 5000, rounded, else undefined. */
export function foodKcal(value: unknown): number | undefined {
  const kcal = Number(value);
  return Number.isFinite(kcal) && kcal >= 0 && kcal <= 5000 ? kcal : undefined;
}

/** Copies protein, carbs and fat that are present and plausible, to one decimal. */
export function copyMacros(from: unknown, to: Macros): void {
  for (const [key, max] of FOOD_MACROS) {
    const value = presentNumber(field(from, key), 0, max);
    if (value !== undefined) to[key] = roundTo1(value);
  }
}

/** Copies fibre, sugar, sodium (whole mg) and saturated fat that are present and plausible. */
export function copyExtras(from: unknown, to: NutritionExtras): void {
  for (const [key, max] of NUTRITION_EXTRAS) {
    const value = presentNumber(field(from, key), 0, max);
    if (value !== undefined) to[key] = key === 'sodium' ? Math.round(value) : roundTo1(value);
  }
}

function cleanPortions(raw: unknown): Portion[] | null {
  if (!Array.isArray(raw)) return null;
  const portions = raw
    .slice(0, 8)
    .filter((p): p is [string, unknown] =>
      Array.isArray(p) && typeof p[0] === 'string' && Number.isFinite(+p[1]) && +p[1] > 0 && +p[1] <= 5000)
    .map(([label, grams]): Portion => [label.slice(0, 60), roundTo1(+(grams as number))]);
  return portions.length ? portions : null;
}

export function sanitizeFavorite(raw: unknown, now: Date): FavoriteFood | null {
  if (!isObject(raw) || !idOk(raw.id)) return null;
  const name = str(raw.name, 120).trim();
  const kcal = foodKcal(raw.kcal);
  if (!name || kcal === undefined) return null;
  const out: FavoriteFood = {
    id: raw.id,
    name,
    kcal: Math.round(kcal),
    source: isFoodSource(raw.source) ? raw.source : 'estimate',
    createdAt: '',
  };
  copyMacros(raw, out);
  const serving = str(raw.serving, 60).trim();
  if (serving) out.serving = serving;
  copyExtras(raw, out);
  const grams = Number(raw.g);
  if (raw.g != null && Number.isFinite(grams) && grams > 0 && grams <= 5000) {
    out.g = roundTo1(grams);
    const portions = cleanPortions(raw.pt);
    if (portions) out.pt = portions;
  }
  out.createdAt = createdAt(raw.createdAt, now);
  return out;
}

export function sanitizeMeal(raw: unknown, now: Date): SavedMeal | null {
  if (!isObject(raw) || !idOk(raw.id)) return null;
  const name = str(raw.name, 40).trim();
  if (!name || !Array.isArray(raw.items)) return null;
  const items: MealItem[] = [];
  for (const entry of raw.items.slice(0, 20)) {
    const itemName = str(field(entry, 'name'), 120).trim();
    const kcal = foodKcal(field(entry, 'kcal'));
    if (!itemName || kcal === undefined) continue;
    const item: MealItem = { name: itemName, kcal: Math.round(kcal) };
    copyMacros(entry, item);
    const source = field(entry, 'source');
    if (isFoodSource(source)) item.source = source;
    copyExtras(entry, item);
    items.push(item);
  }
  if (!items.length) return null;
  return { id: raw.id, name, items, createdAt: createdAt(raw.createdAt, now) };
}
