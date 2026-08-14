import { readdirSync, readFileSync } from 'node:fs';
import { extname, join, relative } from 'node:path';

const root = process.cwd();
const sourceRoot = join(root, 'src');
const supportedExtensions = new Set(['.vue', '.ts', '.tsx']);

function collectFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const absolutePath = join(directory, entry.name);
    if (entry.isDirectory()) return collectFiles(absolutePath);
    return supportedExtensions.has(extname(entry.name)) ? [absolutePath] : [];
  });
}

const violations = collectFiles(sourceRoot).flatMap((file) => {
  const source = readFileSync(file, 'utf8');
  const displayPath = relative(root, file).replaceAll('\\', '/');
  const fileViolations = [];

  if (/<(?:div|span)\b[^>]*@(?:click|dblclick)\b/i.test(source)) {
    fileViolations.push(`${displayPath}: use a semantic button or link instead of a clickable div/span`);
  }

  if (/Message\.error\(\s*(?:error|err|response)(?:\.|\))/i.test(source)) {
    fileViolations.push(`${displayPath}: do not display a raw server or JavaScript error`);
  }
  return fileViolations;
});

const migrationBaseline = 116;
if (violations.length > migrationBaseline) {
  throw new Error(
    `UI consistency verification failed: ${
      violations.length
    } findings exceed the migration baseline of ${migrationBaseline}.\n${violations.slice(0, 20).join('\n')}`
  );
}

// eslint-disable-next-line no-console
console.log(`UI consistency verification passed. Existing migration backlog: ${violations.length} findings.`);
