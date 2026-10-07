// Whether this test process can change its time zone while running. Separate processes (npm test, CI) can; threads
// share one zone and can't (Stryker's mutation runs use threads). Time-zone tests use this to run every zone when
// they can, and only the process's own zone when they can't, instead of failing or passing for the wrong reason.

const setZone = (zone: string | undefined): void => {
  if (zone === undefined) delete process.env.TZ;
  else process.env.TZ = zone;
};

export const currentZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

/** True when setting `process.env.TZ` really changes local time (Samoa skipped 30 Dec 2011, so that day becomes the 31st). */
export const canSwitchTimeZone = ((): boolean => {
  const saved = process.env.TZ;
  setZone('Pacific/Apia');
  const switched = new Date(2011, 11, 30).getDate() === 31;
  setZone(saved);
  return switched;
})();

/** The zones a time-zone test can really exercise in this process. */
export const testableZones = <T extends string>(zones: readonly T[]): T[] =>
  canSwitchTimeZone ? [...zones] : zones.filter((zone) => zone === currentZone);
