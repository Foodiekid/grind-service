/** Allowed values and plausible ranges for everything GRIND stores. Ported from the web app's `backup.js`. */

/** Daily log fields and their plausible range; anything outside is dropped, never clamped. */
export const LOG_RANGES = {
  weight: [20, 400],
  steps: [0, 200000],
  sleep: [0, 24],
  rhr: [25, 250],
  resprate: [3, 60],
  bodyfat: [1, 70],
  calories: [0, 20000],
  waterGlasses: [0, 40],
  waterMl: [0, 20000],
  protein: [0, 1000],
  carbs: [0, 2000],
  fat: [0, 1000],
  mood: [1, 10],
  hrv: [1, 500],
  recovery: [0, 100],
  strain: [0, 21],
  spo2: [50, 100],
  workoutMin: [0, 1440],
} as const satisfies Record<string, readonly [number, number]>;

export type LogField = keyof typeof LOG_RANGES;

/** Log fields stored as whole numbers. */
export const WHOLE_LOG_FIELDS: ReadonlySet<LogField> = new Set([
  'waterGlasses', 'waterMl', 'protein', 'steps', 'rhr', 'mood', 'recovery', 'workoutMin',
]);

export const LAB_RANGE = [0, 100000] as const;

export const RECORD_SOURCES = ['manual', 'demo', 'apple-health', 'shortcut', 'whoop', 'csv', 'pdf', 'oura'] as const;
export type RecordSource = (typeof RECORD_SOURCES)[number];

export const FOOD_SOURCES = ['usda', 'built-in', 'off', 'label', 'estimate', 'chain', 'indb', 'fndds'] as const;
export type FoodSource = (typeof FOOD_SOURCES)[number];

export const JOURNAL_TAGS = ['sore', 'ill', 'travel', 'alcohol', 'caffeine', 'stress'] as const;
export type JournalTag = (typeof JOURNAL_TAGS)[number];

export const MEALS = ['breakfast', 'lunch', 'dinner', 'snack'] as const;
export type Meal = (typeof MEALS)[number];

/** Optional nutrition details of a food, with their upper limits (sodium in mg, the rest in g). */
export const NUTRITION_EXTRAS = [['fiber', 200], ['sugar', 1000], ['sodium', 50000], ['satFat', 500]] as const;
export type NutritionExtra = (typeof NUTRITION_EXTRAS)[number][0];

/** Macros of one food, with their upper limits in grams. */
export const FOOD_MACROS = [['protein', 500], ['carbs', 1000], ['fat', 500]] as const;
export type Macro = (typeof FOOD_MACROS)[number][0];

const isOneOf = <T extends string>(values: readonly T[]) =>
  (value: unknown): value is T => typeof value === 'string' && (values as readonly string[]).includes(value);

export const isRecordSource = isOneOf(RECORD_SOURCES);
export const isFoodSource = isOneOf(FOOD_SOURCES);
export const isJournalTag = isOneOf(JOURNAL_TAGS);
export const isMeal = isOneOf(MEALS);
