/**
 * Shared rules for cleaning data that comes from outside the app (a backup file, a sync download, an import). Ported
 * from the web app's `backup.js`; golden tests check the port gives identical results.
 *
 * Input is `unknown` on purpose: nothing from outside is trusted to have the right shape.
 */

/** Any object's fields, read safely. Arrays count as objects, as in the web app. */
export type Raw = Record<string, unknown>;

export const isObject = (value: unknown): value is Raw => value !== null && typeof value === 'object';

/** A field of `value`, or undefined when `value` isn't an object (a number or string has no fields worth reading). */
export const field = (value: unknown, key: string): unknown => (isObject(value) ? value[key] : undefined);

/** Text cut to `max` characters, or '' for anything that isn't text. */
export const str = (value: unknown, max: number): string => (typeof value === 'string' ? value.slice(0, max) : '');

export const inRange = (value: unknown, [low, high]: readonly [number, number]): value is number =>
  typeof value === 'number' && Number.isFinite(value) && value >= low && value <= high;

/** Ids made by the app: letters, digits, `_`, `.` and `-`, at most 80 characters. */
export const idOk = (value: unknown): value is string => typeof value === 'string' && /^[\w.-]{1,80}$/.test(value);

/** `Number(value)` when the field is present and within [min, max], else undefined. Missing is not zero. */
export function presentNumber(value: unknown, min: number, max: number): number | undefined {
  if (value == null) return undefined;
  const n = Number(value);
  return Number.isFinite(n) && n >= min && n <= max ? n : undefined;
}

export const roundTo1 = (n: number): number => Math.round(n * 10) / 10;

/** The web app's 32-bit string hash, used to give food entries without an id a stable one. */
export const hash = (text: string): number =>
  [...text].reduce((h, c) => ((h << 5) - h + c.charCodeAt(0)) | 0, 0);

/** Time an object was created: the stored text (cut to 40 characters), else now. */
export const createdAt = (value: unknown, now: Date): string =>
  typeof value === 'string' ? value.slice(0, 40) : now.toISOString();
