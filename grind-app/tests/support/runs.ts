// How many generated inputs a property test tries. Normal runs use the full count; during mutation testing (Stryker
// sets __STRYKER_ACTIVE_MUTANT__ for every run) a smaller count is enough to catch a planted bug and keeps the run fast.

const underMutation = process.env.__STRYKER_ACTIVE_MUTANT__ !== undefined;

/** fast-check options: `full` generated inputs normally, a tenth (at least 25) during mutation testing. */
export const runs = (full: number): { numRuns: number } => ({
  numRuns: underMutation ? Math.max(25, Math.round(full / 10)) : full,
});
