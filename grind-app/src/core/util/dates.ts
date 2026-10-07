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
  return `${String(date.getFullYear())}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Local midnight of the date, or null when the text isn't a real calendar date (`2026-02-30`, `2026-2-3`). */
export function parseDateStr(text: string | null | undefined): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text ?? '');
  if (!match) return null;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(year, month - 1, day);
  // JavaScript rolls an impossible date over: 30 Feb becomes 2 Mar, month 13 next January. The day is checked too,
  // because a date a time zone skipped rolls within its month (Samoa had no 30 Dec 2011: it becomes the 31st). The
  // year is checked because JavaScript reads years 0 to 99 as 1900 to 1999: `0050-01-01` would become 1950.
  const isSameYear = date.getFullYear() === year;
  const isSameMonth = date.getMonth() === month - 1;
  const isSameDay = date.getDate() === day;
  return isSameYear && isSameMonth && isSameDay ? date : null;
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

/** Regions whose week starts on Sunday, used when Intl can't say (the web app's list). */
export const SUNDAY_FIRST_REGIONS: ReadonlySet<string> = new Set(['US', 'CA', 'MX', 'JP', 'BR', 'IN', 'IL', 'AU', 'PH', 'KR', 'TW', 'HK', 'ZA', 'SA', 'AR', 'CO', 'PE', 'VE']);

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

/** The first weekday Intl reports for the locale, or undefined when the runtime (or the locale) doesn't say. */
function intlFirstDay(locale: string): number | undefined {
  try {
    const intlLocale = new Intl.Locale(locale) as Intl.Locale & WeekInfoLocale;
    const info = typeof intlLocale.getWeekInfo === 'function' ? intlLocale.getWeekInfo() : intlLocale.weekInfo;
    return info?.firstDay;
  } catch {
    return undefined;
  }
}

/**
 * The first day of the week from what Intl reported, else from the region: Sunday in the regions that start the week
 * on Sunday, Monday elsewhere. Older engines don't report week info, so the fallback is what they use.
 */
export function chooseFirstDay(reported: number | undefined, region: string): Weekday {
  if (reported !== undefined && Number.isInteger(reported) && reported >= 1 && reported <= 7) return reported as Weekday;
  return SUNDAY_FIRST_REGIONS.has(region) ? 7 : 1;
}

/** The locale's first day of the week, from Intl when the runtime knows it, else from the region. */
export function firstDayOfWeek(locale: string): Weekday {
  return chooseFirstDay(intlFirstDay(locale), localeRegion(locale));
}
