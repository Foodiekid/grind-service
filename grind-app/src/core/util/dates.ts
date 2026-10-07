/**
 * Calendar dates as local `YYYY-MM-DD` strings, the format every GRIND record uses for "which day".
 *
 * Always local, never UTC: slicing `toISOString()` would put an evening workout on the next day for anyone west of
 * Greenwich. Ported from the web app's `util.js`; golden tests check both agree in several time zones, including
 * daylight-saving changes and a day that never happened (Samoa, 30 Dec 2011).
 *
 * No hidden clock: functions that need "today" take it as an argument.
 */

/** A local calendar date, `YYYY-MM-DD`. */
export type DateStr = string;

/** ISO weekday: 1 = Monday ... 7 = Sunday. */
export type Weekday = 1 | 2 | 3 | 4 | 5 | 6 | 7;

const pad = (n: number): string => String(n).padStart(2, '0');

export function localDateStr(date: Date): DateStr {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Local midnight of the date, or null when the text isn't a real calendar date (`2026-02-30`, `2026-2-3`). */
export function parseDateStr(text: string | null | undefined): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text ?? '');
  if (!match) return null;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day ? date : null;
}

export function isValidDateStr(text: string | null | undefined): boolean {
  return parseDateStr(text) !== null;
}

/**
 * The date `delta` days away. Counts calendar days, so a daylight-saving change never skips or repeats a day. Where
 * a whole day was removed from the calendar (Samoa, 30 Dec 2011) two neighbouring results can coincide, exactly as
 * in the web app. Null for an invalid date (the web app silently used today instead, which hid bad input).
 */
export function addDays(dateStr: DateStr, delta: number): DateStr | null {
  const date = parseDateStr(dateStr);
  if (!date) return null;
  date.setDate(date.getDate() + delta);
  return localDateStr(date);
}

/** The first day of the week containing `date`, for a week that starts on `firstDay`. */
export function startOfWeekStr(date: Date, firstDay: Weekday): DateStr {
  const start = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const offset = (start.getDay() - (firstDay % 7) + 7) % 7;
  start.setDate(start.getDate() - offset);
  return localDateStr(start);
}

const SUNDAY_REGIONS = new Set(['US', 'CA', 'MX', 'JP', 'BR', 'IN', 'IL', 'AU', 'PH', 'KR', 'TW', 'HK', 'ZA', 'SA', 'AR', 'CO', 'PE', 'VE']);

/** The region of a BCP 47 locale (`en-US` gives `US`, `de` gives `DE`), or '' when unknown. */
export function localeRegion(locale: string): string {
  try {
    return new Intl.Locale(locale).maximize().region ?? '';
  } catch {
    return '';
  }
}

interface WeekInfoLocale {
  getWeekInfo?: () => { firstDay?: number };
  weekInfo?: { firstDay?: number };
}

/** The locale's first day of the week, from Intl when the runtime knows it, else from the region. */
export function firstDayOfWeek(locale: string): Weekday {
  try {
    const intlLocale = new Intl.Locale(locale) as Intl.Locale & WeekInfoLocale;
    const info = typeof intlLocale.getWeekInfo === 'function' ? intlLocale.getWeekInfo() : intlLocale.weekInfo;
    const firstDay = info?.firstDay;
    if (firstDay !== undefined && firstDay >= 1 && firstDay <= 7) return firstDay as Weekday;
  } catch {
    // Unknown locale: fall back to the region below.
  }
  return SUNDAY_REGIONS.has(localeRegion(locale)) ? 7 : 1;
}
