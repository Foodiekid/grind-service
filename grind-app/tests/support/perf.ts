// Latency percentiles for performance tests. Averages hide the slow cases users feel, so budgets are set on p95 and
// p99: run the work many times after a warm-up and read the 95th and 99th slowest-percent runs.

/**
 * Budgets are for a laptop. Slower machines (shared CI runners) set GRIND_PERF_FACTOR, e.g. 2, which scales every
 * budget openly instead of loosening the numbers in the tests.
 */
export const PERF_FACTOR = Number(process.env.GRIND_PERF_FACTOR ?? '1') || 1;

/** A budget in milliseconds, scaled for the machine running the test. */
export const budget = (ms: number): number => ms * PERF_FACTOR;

export interface Percentiles {
  p50: number;
  p95: number;
  p99: number;
  max: number;
}

const percentile = (sorted: readonly number[], p: number): number =>
  sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)] ?? Number.NaN;

/** Milliseconds per call of `work`, after `warmUp` untimed calls (lets the JIT settle, as on a phone after start). */
export function measure(work: () => unknown, runs = 200, warmUp = 20): Percentiles {
  for (let i = 0; i < warmUp; i += 1) work();
  const times: number[] = [];
  for (let i = 0; i < runs; i += 1) {
    const start = performance.now();
    work();
    times.push(performance.now() - start);
  }
  times.sort((a, b) => a - b);
  return { p50: percentile(times, 50), p95: percentile(times, 95), p99: percentile(times, 99), max: times.at(-1) ?? Number.NaN };
}

/** One readable line for test output. */
export const describePercentiles = (label: string, { p50, p95, p99 }: Percentiles): string =>
  `${label}: p50 ${p50.toFixed(3)} ms, p95 ${p95.toFixed(3)} ms, p99 ${p99.toFixed(3)} ms`;
