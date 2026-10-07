// Reads Stryker's JSON report: counts per status, each mutant's status by a key that survives line shifts, and the
// surviving mutants, each matched to its recorded resolution (resolutions.json) when there is one.
import { existsSync, readFileSync } from 'node:fs';

export const REPORT = 'reports/mutation/mutation.json';
export const RESOLUTIONS = 'scripts/mutation/resolutions.json';

/** Stryker's report, or null when it wrote none (it stopped before the end). */
export function readReport() {
  return existsSync(REPORT) ? JSON.parse(readFileSync(REPORT, 'utf8')) : null;
}

export function readResolutions() {
  return JSON.parse(readFileSync(RESOLUTIONS, 'utf8')).resolutions;
}

/** The exact source text a mutant replaces (columns are 1-based), with runs of whitespace made single spaces. */
function replacedText(lines, { start, end }) {
  const span = lines.slice(start.line - 1, end.line);
  span[span.length - 1] = span.at(-1).slice(0, end.column - 1);
  span[0] = span[0].slice(start.column - 1);
  return span.join(' ').replace(/\s+/g, ' ').trim();
}

/**
 * The same mutant across runs: file, mutator, the trimmed line it starts on, its column, the exact code it replaces
 * and its replacement. Line numbers are left out, so code moving up or down keeps the key; editing the code makes a
 * new mutant. Mutants that are still identical (the same code twice on one line) get "#2", "#3" in order.
 */
function mutantKey(file, mutant, firstLine, lines) {
  return [file, mutant.mutatorName, firstLine.trim(), mutant.location.start.column, replacedText(lines, mutant.location), mutant.replacement].join('|');
}

/** The resolution recorded for a mutant: same file, an allowed mutator, and its code contains the entry's code. */
export function findResolution(resolutions, file, mutator, code) {
  return resolutions.find((entry) => entry.file === file
    && (entry.mutators === undefined || entry.mutators.includes(mutator))
    && code.includes(entry.code));
}

/** Every mutant of the report with its key and code, in report order. */
export function listMutants(report) {
  const mutants = [];
  const seenKeys = new Map();
  for (const [file, { source, mutants: fileMutants }] of Object.entries(report.files)) {
    const lines = source.split('\n');
    for (const mutant of fileMutants) {
      const { start, end } = mutant.location;
      const firstLine = lines[start.line - 1] ?? '';
      // The first line plus the whole replaced span, so a resolution can match on either.
      const code = `${firstLine}\n${lines.slice(start.line - 1, end.line).join('\n')}`;
      const baseKey = mutantKey(file, mutant, firstLine, lines);
      const repeat = (seenKeys.get(baseKey) ?? 0) + 1;
      seenKeys.set(baseKey, repeat);
      mutants.push({
        key: repeat === 1 ? baseKey : `${baseKey}#${repeat}`,
        file,
        line: start.line,
        location: mutant.location,
        mutator: mutant.mutatorName,
        replacement: mutant.replacement,
        original: firstLine.trim(),
        code,
        status: mutant.status,
      });
    }
  }
  return mutants;
}

/** Counts, survivors (open or accepted as equivalent) and equivalent entries that no longer match anything. */
export function summarize(report, resolutions) {
  const mutants = listMutants(report);
  const counts = { Killed: 0, Timeout: 0, Survived: 0, NoCoverage: 0 };
  for (const mutant of mutants) if (mutant.status in counts) counts[mutant.status] += 1;

  const survivors = mutants
    .filter((mutant) => mutant.status === 'Survived' || mutant.status === 'NoCoverage')
    .map((mutant) => ({ ...mutant, resolution: findResolution(resolutions, mutant.file, mutant.mutator, mutant.code) }));
  const isAccepted = (survivor) => survivor.resolution?.kind === 'equivalent';
  const open = survivors.filter((survivor) => !isAccepted(survivor));
  const accepted = survivors.filter(isAccepted);

  // An equivalent entry that matches no survivor is stale: its code changed or a test now kills the mutant.
  const staleEquivalents = resolutions.filter((entry) => entry.kind === 'equivalent' && !accepted.some((s) => s.resolution === entry));

  const detected = counts.Killed + counts.Timeout;
  const valid = detected + counts.Survived + counts.NoCoverage;
  const percent = (count) => (valid === 0 ? null : Math.round((count / valid) * 10000) / 100);
  return {
    mutants,
    counts,
    total: mutants.length,
    score: percent(detected),
    scoreWithAccepted: percent(detected + accepted.length),
    survivors,
    open,
    accepted,
    staleEquivalents,
    breakAt: report.thresholds?.break ?? null,
  };
}
