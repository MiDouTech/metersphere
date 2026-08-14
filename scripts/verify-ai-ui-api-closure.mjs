import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const root = process.cwd();
const manifestPath = path.join(root, 'docs/task/deviation_gap_closure_20260813/interface-closure-manifest.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));

function collectEntries(directory, extensions) {
  const values = [];
  const directories = [directory];
  while (directories.length) {
    const current = directories.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const target = path.join(current, entry.name);
      if (entry.isDirectory()) directories.push(target);
      else if (extensions.some((extension) => entry.name.endsWith(extension))) {
        values.push({ path: target, content: fs.readFileSync(target, 'utf8') });
      }
    }
  }
  return values;
}

const joinContents = (entries) => entries.map((entry) => entry.content).join('\n');
const backendEntries = collectEntries(path.join(root, 'backend'), ['.java', '.xml', '.sql']);
const frontendEntries = collectEntries(path.join(root, 'frontend/src'), ['.ts', '.vue']);
const testEntries = collectEntries(path.join(root, 'backend'), ['Tests.java', 'Test.java']);
const backend = joinContents(backendEntries);
const frontend = joinContents(frontendEntries);
const tests = joinContents(testEntries);
const failures = [];
const endpointInventory = new Map(
  (manifest.backendEndpoints || []).map((item) => [`${item.method.toUpperCase()} ${item.path}`, item])
);

for (const capability of manifest.capabilities) {
  for (const pattern of capability.backend || []) {
    if (!backend.includes(pattern)) failures.push(`${capability.name}: missing backend pattern: ${pattern}`);
  }
  if (capability.type === 'UI' && !(capability.frontend || []).length) {
    failures.push(`${capability.name}: UI capability has no frontend declaration`);
  }
  for (const pattern of capability.frontend || []) {
    if (!frontend.includes(pattern)) failures.push(`${capability.name}: missing frontend pattern: ${pattern}`);
  }
  if (['PROTOCOL', 'WEBHOOK', 'INTERNAL'].includes(capability.type) && !(capability.tests || []).length) {
    failures.push(`${capability.name}: ${capability.type} capability has no test declaration`);
  }
  for (const pattern of capability.tests || []) {
    if (!tests.includes(pattern)) failures.push(`${capability.name}: missing test pattern: ${pattern}`);
  }
  if (capability.type === 'RESERVED' && (capability.frontend || []).length) {
    failures.push(`${capability.name}: RESERVED capability must not expose a frontend entry`);
  }
  if (capability.type === 'LEGACY') {
    for (const field of ['owner', 'reason', 'expiresAt']) {
      if (!capability[field]) failures.push(`${capability.name}: LEGACY capability has no ${field}`);
    }
    if (capability.expiresAt && Date.parse(capability.expiresAt) < Date.now()) {
      failures.push(`${capability.name}: LEGACY capability expired at ${capability.expiresAt}`);
    }
  }
}

const normalize = (value) => value.replaceAll('\\', '/');
const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const exceptionMap = new Map(
  (manifest.frontendApiExceptions || []).map((item) => [`${normalize(item.module)}:${item.name}`, item])
);
const discoveredApiFunctions = new Set();
const frontendApiEntries = collectEntries(path.join(root, 'frontend/src/api/modules'), ['.ts']);
const endpointFrontendMarkers = [...endpointInventory.values()].flatMap((item) => item.frontend || []);
const frontendPathConstants = new Map();
for (const entry of frontendEntries) {
  for (const match of entry.content.matchAll(/(?:export\s+)?const\s+([A-Za-z_$][\w$]*)\s*=\s*['"](\/[^'"]*)['"]/g)) {
    frontendPathConstants.set(match[1], match[2]);
  }
  for (const match of entry.content.matchAll(/([A-Za-z_$][\w$]*)\s*:\s*['"](\/[^'"]*)['"]/g)) {
    frontendPathConstants.set(`Url.${match[1]}`, match[2]);
  }
}
const frontendApiModules = frontendApiEntries
  .filter((entry) => endpointFrontendMarkers.some((name) =>
    new RegExp(`export\\s+(?:(?:async\\s+)?function\\s+|const\\s+)${escapeRegExp(name)}\\b`).test(entry.content)))
  .map((entry) => normalize(path.relative(root, entry.path)));

for (const moduleName of frontendApiModules) {
  const sourcePath = path.join(root, moduleName);
  if (!fs.existsSync(sourcePath)) {
    failures.push(`frontend API module does not exist: ${moduleName}`);
    continue;
  }
  const source = fs.readFileSync(sourcePath, 'utf8');
  const exportPattern = /export\s+(?:(?:async\s+)?function\s+|const\s+)([A-Za-z_$][\w$]*)/g;
  for (const match of source.matchAll(exportPattern)) {
    const name = match[1];
    const key = `${normalize(moduleName)}:${name}`;
    discoveredApiFunctions.add(key);
    const usagePattern = new RegExp(`\\b${escapeRegExp(name)}\\b`);
    const usedOutsideDefinition = frontendEntries.some(
      (entry) => path.resolve(entry.path) !== path.resolve(sourcePath) && usagePattern.test(entry.content)
    );
    if (!usedOutsideDefinition) {
      const exception = exceptionMap.get(key);
      if (!exception) {
        failures.push(`${moduleName}: exported API function has no business caller: ${name}`);
        continue;
      }
      for (const field of ['owner', 'reason', 'expiresAt']) {
        if (!exception[field]) failures.push(`${key}: API exception has no ${field}`);
      }
      if (exception.expiresAt && Date.parse(exception.expiresAt) < Date.now()) {
        failures.push(`${key}: API exception expired at ${exception.expiresAt}`);
      }
    }
  }
}

for (const key of exceptionMap.keys()) {
  if (!discoveredApiFunctions.has(key)) failures.push(`${key}: stale frontend API exception`);
}

const discoveredEndpoints = new Set();

function firstMappedPath(argumentsText = '') {
  return argumentsText.match(/["']([^"']+)["']/)?.[1] || '';
}

function joinRoute(base, suffix) {
  const value = `${base || ''}/${suffix || ''}`.replace(/\/{2,}/g, '/');
  return value.length > 1 && value.endsWith('/') ? value.slice(0, -1) : value;
}

function findFrontendApiDefinition(name) {
  for (const moduleName of frontendApiModules) {
    const sourcePath = path.join(root, moduleName);
    if (!fs.existsSync(sourcePath)) continue;
    const source = fs.readFileSync(sourcePath, 'utf8');
    const patterns = [
      new RegExp(`export\\s+(?:async\\s+)?function\\s+${escapeRegExp(name)}\\b`),
      new RegExp(`export\\s+const\\s+${escapeRegExp(name)}\\b`),
    ];
    const start = patterns.map((pattern) => source.search(pattern)).find((index) => index >= 0);
    if (start == null) continue;
    const remainder = source.slice(start);
    const next = remainder.slice(1).search(/\nexport\s+(?:(?:async\s+)?function|const)\s+/);
    return { moduleName, source: next >= 0 ? remainder.slice(0, next + 1) : remainder };
  }
  return undefined;
}

function inferFrontendHttpMethod(source) {
  const explicit = source.match(/method\s*:\s*['"](GET|POST|PUT|PATCH|DELETE)['"]/i)?.[1];
  if (explicit) return explicit.toUpperCase();
  const helper = source.match(/MSR\.(get|post|put|delete|uploadFile)\b/i)?.[1]?.toUpperCase();
  if (helper === 'UPLOADFILE') return 'POST';
  if (helper) return helper;
  if (source.includes('EventSource')) return 'GET';
  return undefined;
}

function frontendReferencesPath(source, expectedPath) {
  let expanded = source;
  for (const [name, value] of frontendPathConstants) {
    expanded = expanded.replace(new RegExp(`\\$\\{${escapeRegExp(name)}\\}`, 'g'), value);
    expanded = expanded.replace(new RegExp(`\\b${escapeRegExp(name)}\\b`, 'g'), value);
  }
  const pattern = expectedPath
    .split(/(\{[^}]+\})/)
    .map((part) => part.startsWith('{')
      ? '(?:\\$\\{[^}]+\\}|[^\\s/`\'"?]+)'
      : escapeRegExp(part))
    .join('');
  return new RegExp(pattern).test(expanded);
}

const classifiedEndpointPaths = [...endpointInventory.keys()].map((key) => key.slice(key.indexOf(' ') + 1));
const controllerRecords = backendEntries
  .filter((entry) => entry.path.endsWith('Controller.java'))
  .map((entry) => {
    const requestMapping = entry.content.match(/@RequestMapping\s*\(([^)]*)\)/);
    const basePath = firstMappedPath(requestMapping?.[1]);
    return { name: normalize(path.relative(root, entry.path)), basePath };
  })
  .filter((entry) => entry.basePath);
const selectedControllerNames = new Set();
for (const endpointPath of classifiedEndpointPaths) {
  const matches = controllerRecords
    .filter((controller) => endpointPath === controller.basePath || endpointPath.startsWith(`${controller.basePath}/`))
    .sort((left, right) => right.basePath.length - left.basePath.length);
  if (matches.length) selectedControllerNames.add(matches[0].name);
}
const controllerNames = [...selectedControllerNames];

for (const controllerName of controllerNames) {
  const controllerPath = path.join(root, controllerName);
  if (!fs.existsSync(controllerPath)) {
    failures.push(`backend endpoint scope does not exist: ${controllerName}`);
    continue;
  }
  const source = fs.readFileSync(controllerPath, 'utf8');
  const requestMapping = source.match(/@RequestMapping\s*\(([^)]*)\)/);
  const basePath = firstMappedPath(requestMapping?.[1]);
  const mappingPattern = /@(Get|Post|Put|Patch|Delete)Mapping(?:\s*\(([^)]*)\))?/g;
  for (const match of source.matchAll(mappingPattern)) {
    const method = match[1].toUpperCase();
    const route = joinRoute(basePath, firstMappedPath(match[2]));
    const key = `${method} ${route}`;
    discoveredEndpoints.add(key);
    const classification = endpointInventory.get(key);
    if (!classification) {
      failures.push(`${controllerName}: unclassified backend endpoint: ${key}`);
      continue;
    }
    if (classification.type === 'UI') {
      if (!(classification.frontend || []).length) failures.push(`${key}: UI endpoint has no frontend evidence`);
      for (const marker of classification.frontend || []) {
        if (!frontend.includes(marker)) failures.push(`${key}: missing frontend evidence: ${marker}`);
        const definition = findFrontendApiDefinition(marker);
        if (!definition) {
          failures.push(`${key}: frontend evidence is not an exported API function: ${marker}`);
          continue;
        }
        const frontendMethod = inferFrontendHttpMethod(definition.source);
        if (!frontendMethod) failures.push(`${key}: cannot infer frontend HTTP method for ${marker}`);
        else if (frontendMethod !== method) {
          failures.push(`${key}: frontend ${marker} uses ${frontendMethod} in ${definition.moduleName}`);
        }
        if (!frontendReferencesPath(definition.source, route)) {
          failures.push(`${key}: frontend ${marker} does not reference controller path in ${definition.moduleName}`);
        }
      }
    }
    if (['PROTOCOL', 'WEBHOOK', 'INTERNAL'].includes(classification.type)) {
      if (!(classification.tests || []).length) failures.push(`${key}: ${classification.type} endpoint has no test evidence`);
      for (const marker of classification.tests || []) {
        if (!tests.includes(marker)) failures.push(`${key}: missing test evidence: ${marker}`);
      }
    }
    if (classification.type === 'LEGACY') {
      for (const field of ['owner', 'reason', 'expiresAt']) {
        if (!classification[field]) failures.push(`${key}: LEGACY endpoint has no ${field}`);
      }
      if (classification.expiresAt && Date.parse(classification.expiresAt) < Date.now()) {
        failures.push(`${key}: LEGACY endpoint expired at ${classification.expiresAt}`);
      }
    }
  }
}

for (const key of endpointInventory.keys()) {
  if (!discoveredEndpoints.has(key)) failures.push(`${key}: stale backend endpoint classification`);
}

if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}
console.log(`AI UI/API closure verified: ${manifest.capabilities.length} classified capabilities.`);
