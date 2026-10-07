/**
 * Units. GRIND stores metric (kg, ml); conversion happens only for display and input. Ported from the web app's
 * `util.js`; rounding matches it exactly (golden tests), so a value shown in lbs and typed back lands on the same kg.
 */

import { localeRegion } from './dates.ts';

export type WeightUnit = 'kg' | 'lbs';

export const KG_PER_LB = 0.45359237;
export const ML_PER_OZ = 29.5735;
/** Energy in one kilogram of body weight change, the usual planning figure. */
export const KCAL_PER_KG = 7700;

const IMPERIAL_REGIONS = new Set(['US', 'LR', 'MM']);

export function defaultWeightUnit(locale: string): WeightUnit {
  return IMPERIAL_REGIONS.has(localeRegion(locale)) ? 'lbs' : 'kg';
}

export function usesImperialVolume(locale: string): boolean {
  return IMPERIAL_REGIONS.has(localeRegion(locale));
}

/** Kilograms shown in the user's unit: lbs to one decimal, kg unchanged. */
export function toDisplayWeight(kg: number | null, unit: WeightUnit): number | null {
  if (kg == null) return null;
  return unit === 'lbs' ? Math.round((kg / KG_PER_LB) * 10) / 10 : kg;
}

/** A typed weight back to kilograms: from lbs to two decimals, kg unchanged. */
export function fromDisplayWeight(value: number | null, unit: WeightUnit): number | null {
  if (value == null) return null;
  return unit === 'lbs' ? Math.round(value * KG_PER_LB * 100) / 100 : value;
}

/** `250 ml`, `1.5 L` or `8 oz`. */
export function formatVolume(ml: number, imperial: boolean): string {
  if (imperial) return `${String(Math.round(ml / ML_PER_OZ))} oz`;
  return ml >= 1000 ? `${String(+(ml / 1000).toFixed(2))} L` : `${String(Math.round(ml))} ml`;
}
