/**
 * Checks a GRIND backup file (the web app's JSON export) before anything is restored, and cleans each record that
 * arrives from sync. From the web app's `backup.js` (`validateBackup`) and `cloud/syncSanitize.js`.
 *
 * Every record goes through its sanitizer: invalid ones are counted and dropped, settings are all or nothing, and a
 * file with nothing valid is refused with a message the user can act on.
 */

import { isObject } from './fields.ts';
import { sanitizeLog, type DailyLog } from './dailyLog.ts';
import { sanitizeFavorite, sanitizeMeal, type FavoriteFood, type SavedMeal } from './food.ts';
import { sanitizeLab, type LabPanel } from './lab.ts';
import { sanitizeProfile, type Profile } from './profile.ts';
import { sanitizeSettings } from './settings.ts';
import { sanitizeWorkout, type Workout } from './workout.ts';

/** Largest backup file accepted; routes ride along (up to 3,000 points each). */
export const MAX_BACKUP_BYTES = 60 * 1024 * 1024;

const LIMITS = { workouts: 5000, dailyLogs: 5000, labs: 1000, favorites: 2000, meals: 500, routes: 2000 } as const;

/** A cleaned route: its row (with an id) and its points. Ported with the routes (slice 3). */
export interface CleanRoute {
  row: { id: string };
  points: unknown[];
}

export interface BackupContext {
  now: Date;
  /** Biomarkers GRIND knows (labs keep only these). */
  labKeys: readonly string[];
  /** Settings GRIND knows; without any, a backup's settings are ignored. */
  settingKeys: readonly string[];
  /** Cleans one route, or null when it is invalid. */
  cleanRoute: (raw: unknown) => CleanRoute | null;
}

export interface CheckedBackup {
  workouts: Workout[];
  dailyLogs: DailyLog[];
  labs: LabPanel[];
  favorites: FavoriteFood[];
  meals: SavedMeal[];
  routes: CleanRoute[];
  profile: Profile | null;
  settings: Record<string, string> | null;
  /** Records dropped as invalid or duplicate. */
  rejected: number;
}

/** Why a backup can't be restored; the message is shown to the user as it is. */
export class BackupError extends Error {
  override name = 'BackupError';
}

const list = (value: unknown): unknown[] => (Array.isArray(value) ? value : []);

const notNull = <T>(value: T | null): value is T => value !== null;

export function validateBackup(doc: unknown, context: BackupContext): CheckedBackup {
  if (!isObject(doc) || Array.isArray(doc)) throw new BackupError('This file is not a GRIND backup.');
  if (doc.format && doc.format !== 'grind-backup') throw new BackupError('This file is not a GRIND backup.');
  const version = doc.version;
  if (version != null && (typeof version !== 'number' || !Number.isInteger(version) || version < 1 || version > 2)) {
    throw new BackupError('This backup was made by a newer (or unknown) version of GRIND. Update the app before restoring it.');
  }
  if (doc.encrypted) throw new BackupError('Encrypted backup — passphrase required.');

  const workoutsIn = list(doc.workouts);
  const logsIn = Array.isArray(doc.dailyLogs) ? doc.dailyLogs : Object.values(isObject(doc.dailyLogs) ? doc.dailyLogs : {});
  const labsIn = list(doc.labs);
  const favoritesIn = list(doc.favorites);
  const mealsIn = list(doc.meals);
  const routesIn = list(doc.routes);
  if (
    routesIn.length > LIMITS.routes || workoutsIn.length > LIMITS.workouts || logsIn.length > LIMITS.dailyLogs
    || labsIn.length > LIMITS.labs || favoritesIn.length > LIMITS.favorites || mealsIn.length > LIMITS.meals
  ) {
    throw new BackupError('Backup is larger than the supported limits.');
  }

  const { now } = context;
  const workouts = workoutsIn.map((raw) => sanitizeWorkout(raw, now)).filter(notNull);
  const dailyLogs = logsIn.map(sanitizeLog).filter(notNull);
  const labs = labsIn.map((raw) => sanitizeLab(raw, context.labKeys, now)).filter(notNull);
  const favorites = favoritesIn.map((raw) => sanitizeFavorite(raw, now)).filter(notNull);
  const meals = mealsIn.map((raw) => sanitizeMeal(raw, now)).filter(notNull);
  const seenRoutes = new Set<string>();
  const routes = routesIn.map(context.cleanRoute).filter((route): route is CleanRoute => {
    if (!route || seenRoutes.has(route.row.id)) return false;
    seenRoutes.add(route.row.id);
    return true;
  });
  const profile = doc.profile ? sanitizeProfile(doc.profile) : null;
  const settings = doc.settings && context.settingKeys.length
    ? sanitizeSettings({ values: doc.settings }, context.settingKeys)
    : null;

  const rejected = workoutsIn.length - workouts.length + logsIn.length - dailyLogs.length + labsIn.length - labs.length
    + favoritesIn.length - favorites.length + mealsIn.length - meals.length + routesIn.length - routes.length;
  if (!workouts.length && !dailyLogs.length && !labs.length && !favorites.length && !meals.length && !routes.length
    && !profile && !settings) {
    throw new BackupError('No valid records found in this file.');
  }
  return { workouts, dailyLogs, labs, favorites, meals, routes, profile, settings: settings?.values ?? null, rejected };
}

/** The kinds of record that sync, with the key each is stored under. */
export type RecordKind = 'dailyLogs' | 'workouts' | 'labs' | 'favoriteFoods' | 'savedMeals' | 'profile' | 'settings';

/**
 * Cleans a record that arrived from sync, or null when it is invalid or doesn't belong under `key` (a record must
 * live under its own id or date, so one record can't overwrite another).
 */
export function sanitizeSyncedRecord(kind: RecordKind, key: string, value: unknown, context: Omit<BackupContext, 'cleanRoute'>): unknown {
  const { now } = context;
  switch (kind) {
    case 'dailyLogs': {
      const log = sanitizeLog(value);
      return log && log.date === key ? log : null;
    }
    case 'workouts': {
      const workout = sanitizeWorkout(value, now);
      return workout && workout.id === key ? workout : null;
    }
    case 'labs': {
      const lab = sanitizeLab(value, context.labKeys, now);
      return lab && lab.id === key ? lab : null;
    }
    case 'favoriteFoods': {
      const favorite = sanitizeFavorite(value, now);
      return favorite && favorite.id === key ? favorite : null;
    }
    case 'savedMeals': {
      const meal = sanitizeMeal(value, now);
      return meal && meal.id === key ? meal : null;
    }
    case 'profile':
      return key === 'current_user' ? sanitizeProfile(value) : null;
    case 'settings':
      return key === 'main' ? sanitizeSettings(value, context.settingKeys) : null;
  }
}
