/**
 * Settings that travel with the account (routines, custom exercises, goals, fuel, weight goal, progression, maps):
 * raw stored strings, each checked for its shape. The block is all or nothing: one bad value drops the whole block,
 * so settings are never half-applied. From `backup.js` (`sanitizeSettings`).
 */

import { isObject } from './fields.ts';

export interface SettingsBlock {
  key: 'main';
  values: Record<string, string>;
}

/** One value may not exceed this, so the whole block stays far below the sync size limit. */
const MAX_SETTING_CHARS = 100000;
const MAX_SETTINGS_TOTAL = 350000;

/** JSON settings and the shape their value must have. */
const SHAPE: Readonly<Record<string, 'object' | 'array'>> = {
  grind_goals: 'object',
  grind_fuel: 'object',
  grind_splits: 'object',
  grind_custom_ex: 'array',
  grind_weight_target: 'object',
  grind_progress: 'object',
  grind_route_goal: 'object',
};

/** Plain-text settings and their only allowed values. */
const PLAIN: Readonly<Record<string, readonly string[]>> = {
  grind_weight_goal: ['lose', 'maintain', 'gain'],
  grind_map_optin: ['1', '0'],
  grind_map_style: ['auto', 'dark', 'light', 'satellite', 'outdoor'],
};

function jsonHasShape(text: string, shape: 'object' | 'array' | undefined): boolean {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return false;
  }
  // Only JSON objects, arrays and null (typeof null is 'object').
  if (typeof parsed !== 'object') return false;
  if (shape === 'array' && !Array.isArray(parsed)) return false;
  if (shape === 'object' && Array.isArray(parsed)) return false;
  return true;
}

/** @param keys the settings GRIND knows; any other key is ignored */
export function sanitizeSettings(raw: unknown, keys: readonly string[]): SettingsBlock | null {
  const source = isObject(raw) ? raw.values : undefined;
  if (!isObject(source) || Array.isArray(source)) return null;
  const values: Record<string, string> = {};
  for (const key of keys) {
    const value = source[key];
    if (value == null) continue;
    if (typeof value !== 'string' || value.length > MAX_SETTING_CHARS) return null;
    const allowed = PLAIN[key];
    if (allowed) {
      if (!allowed.includes(value)) return null;
    } else if (value !== '' && !jsonHasShape(value, SHAPE[key])) {
      return null;
    }
    values[key] = value;
  }
  if (Object.values(values).reduce((total, value) => total + value.length, 0) > MAX_SETTINGS_TOTAL) return null;
  return Object.keys(values).length > 0 ? { key: 'main', values } : null;
}
