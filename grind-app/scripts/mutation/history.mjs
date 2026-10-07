// The record of every mutation-testing run (docs/mutation/history.json, local and git-ignored), and each past survivor's status
// in the latest run.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

import { findResolution } from './results.mjs';

const HISTORY = '../docs/mutation/history.json';
const COMMENT = 'Every mutation-testing run, newest last. Written by grind-app/scripts/mutation/run.mjs; never edit by hand.';

export function readHistory() {
  return existsSync(HISTORY) ? JSON.parse(readFileSync(HISTORY, 'utf8')).runs : [];
}

/** Adds the run and returns the whole history, newest last. */
export function appendRun(run) {
  const runs = [...readHistory(), run];
  // The history is local (git-ignored), so a fresh checkout such as CI has no folder yet.
  mkdirSync(dirname(HISTORY), { recursive: true });
  writeFileSync(HISTORY, `${JSON.stringify({ _comment: COMMENT, runs }, null, 1)}\n`);
  return runs;
}

/**
 * Where a mutant that once survived stands in the latest run: killed (a test catches it now), accepted (equivalent,
 * with a reason), open (still survives, needs a test), or gone (its code was changed or removed).
 */
export function statusNow(survivor, summary, resolutions) {
  const latest = summary.mutants.find((mutant) => mutant.key === survivor.key);
  if (latest === undefined) return 'gone';
  const stillSurvives = latest.status === 'Survived' || latest.status === 'NoCoverage';
  if (!stillSurvives) return 'killed';
  const resolution = findResolution(resolutions, survivor.file, survivor.mutator, survivor.code);
  return resolution?.kind === 'equivalent' ? 'accepted' : 'open';
}
