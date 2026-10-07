// Runs mutation testing, records the run in docs/mutation/history.json and writes the reports:
// docs/MUTATION-LOG.md (summary) and reports/mutation/runs.html (every run, what ran, what failed, why, the fix and
// each failure's status now).
//
//   changes (daily, `npm run mutation`): Stryker's incremental run retests the mutants in source files changed since
//     the last run. It can't see which test covers which mutant (we use its command runner), so a new or changed test
//     never retests anything by itself; the second step retests every open survivor, the mutants a new test may now
//     kill. Everything else comes from the cache.
//   full (weekly, `npm run mutation:full`): retests every mutant and refreshes the cache.
//
// Exits with Stryker's status, so the run fails below the break threshold.
import { spawn } from 'node:child_process';
import { rmSync } from 'node:fs';

import { appendRun } from './history.mjs';
import { writeHtmlReport } from './html.mjs';
import { writeLog } from './log.mjs';
import { listMutants, readReport, readResolutions, REPORT, summarize } from './results.mjs';

const mode = process.argv[2];
if (mode !== 'changes' && mode !== 'full') {
  console.error('Usage: node scripts/mutation/run.mjs <changes|full>');
  process.exit(2);
}

/** Runs Stryker, showing its output as usual, and reads from it how many mutants it tested and reused. */
function stryker(...args) {
  return new Promise((resolve) => {
    const child = spawn('npx', ['stryker', 'run', ...args], { stdio: ['inherit', 'pipe', 'pipe'] });
    let output = '';
    const keep = (target) => (chunk) => {
      target.write(chunk);
      output += chunk.toString();
    };
    child.stdout.on('data', keep(process.stdout));
    child.stderr.on('data', keep(process.stderr));
    child.on('close', (code) => {
      const reused = /(\d+) of \d+ mutant result\(s\) are reused/.exec(output);
      const progress = [...output.matchAll(/(\d+)\/(\d+) tested/g)].at(-1);
      resolve({ status: code ?? 1, tested: progress ? Number(progress[2]) : 0, reused: reused ? Number(reused[1]) : 0 });
    });
  });
}

/** Stryker's `--mutate` ranges for the open survivors: whole lines, one range per line. */
function survivorRanges(open) {
  return [...new Set(open.map(({ file, location }) => `${file}:${location.start.line}-${location.end.line}`))];
}

/** Mutants whose code is new or changed since the previous report, counted per file. */
function changedFiles(previousReport, report) {
  const before = new Set(previousReport === null ? [] : listMutants(previousReport).map((mutant) => mutant.key));
  const counts = new Map();
  for (const mutant of listMutants(report)) {
    if (!before.has(mutant.key)) counts.set(mutant.file, (counts.get(mutant.file) ?? 0) + 1);
  }
  return [...counts].map(([file, count]) => ({ file, count }));
}

const resolutions = readResolutions();
const previousReport = readReport();
// A report left from an earlier run must never be recorded as this run's result.
rmSync(REPORT, { force: true });

const steps = [];
const first = await stryker(...(mode === 'full' ? ['--force'] : []));
steps.push({ what: mode === 'full' ? 'all mutants' : 'mutants in changed code', tested: first.tested, reused: first.reused });
let status = first.status;
let report = readReport();
const changed = report === null ? [] : changedFiles(previousReport, report);

const openAfterFirst = mode === 'changes' && report !== null ? summarize(report, resolutions).open : [];
if (openAfterFirst.length > 0) {
  console.log(`\nRetesting ${openAfterFirst.length} open survivors against the current tests.\n`);
  const retest = await stryker('--force', '--mutate', survivorRanges(openAfterFirst).join(','));
  steps.push({ what: 'open survivors', tested: retest.tested, reused: null, survivorsRetested: openAfterFirst.length });
  status = retest.status;
  report = readReport();
}

if (report === null) {
  console.error('Stryker wrote no report: the run is not recorded and the reports are unchanged.');
  process.exit(status === 0 ? 1 : status);
}

const summary = summarize(report, resolutions);
const history = appendRun({
  date: new Date().toISOString(),
  mode,
  status: status === 0 ? 'passed' : 'failed',
  breakAt: summary.breakAt,
  score: summary.score,
  scoreWithAccepted: summary.scoreWithAccepted,
  counts: { killed: summary.counts.Killed, timeout: summary.counts.Timeout, survived: summary.counts.Survived, noCoverage: summary.counts.NoCoverage },
  total: summary.total,
  steps,
  changedFiles: mode === 'full' ? [] : changed,
  survivors: summary.survivors.map(({ key, file, line, mutator, replacement, original, code }) => ({ key, file, line, mutator, replacement, original, code })),
});
writeLog(history, summary, resolutions);
const htmlPath = writeHtmlReport(history, summary, resolutions);
console.log(`\nMutation score ${summary.score}% (${summary.open.length} open, ${summary.accepted.length} accepted), ${status === 0 ? 'passed' : 'FAILED'}.`);
console.log(`Reports: docs/MUTATION-LOG.md and ${htmlPath}`);
process.exit(status);
