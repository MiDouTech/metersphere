import fs from 'node:fs';
import path from 'node:path';

const frontendRoot = path.resolve(import.meta.dirname, '..');
const repositoryRoot = path.resolve(frontendRoot, '..');
const migrationRoot = path.join(repositoryRoot, 'backend/framework/domain/src/main/resources/migration/3.7.2/dml');

function walk(directory, predicate) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) return walk(target, predicate);
    return predicate(target) ? [target] : [];
  });
}

const migrationFiles = walk(migrationRoot, (file) => file.endsWith('.sql'));
const resources = [];
const updateStatements = [];
for (const file of migrationFiles) {
  const sql = fs.readFileSync(file, 'utf8');
  updateStatements.push(...sql.split(';').filter((statement) => /UPDATE\s+permission_resource/i.test(statement)));
  const patterns = [
    /\(\s*'([A-Z][A-Z0-9_]+)'\s*,\s*'\1'\s*,\s*'[^']*'\s*,\s*'(MENU|PAGE|TAB|BUTTON|API)'\s*,\s*'(SYSTEM|ORGANIZATION|PROJECT)'\s*,\s*(?:NULL|'[^']*')\s*,\s*(NULL|'[^']*')\s*,\s*(NULL|'[^']*')/g,
    /SELECT\s+'([A-Z][A-Z0-9_]+)'\s*,\s*'\1'\s*,\s*'[^']*'\s*,\s*'(MENU|PAGE|TAB|BUTTON|API)'\s*,\s*'(SYSTEM|ORGANIZATION|PROJECT)'\s*,\s*(?:NULL|'[^']*')\s*,\s*(NULL|'[^']*')\s*,\s*(NULL|'[^']*')/g,
  ];
  for (const pattern of patterns) {
    for (const match of sql.matchAll(pattern)) {
      const nullable = (value) => (value === 'NULL' ? undefined : value.slice(1, -1));
      resources.push({ code: match[1], type: match[2], routeName: nullable(match[4]), permissionId: nullable(match[5]), enabled: true });
    }
  }
}

const resourceMap = new Map(resources.map((resource) => [resource.code, resource]));
for (const statement of updateStatements) {
  const one = statement.match(/WHERE\s+code\s*=\s*'([A-Z][A-Z0-9_]+)'/i)?.[1];
  const many = statement.match(/WHERE\s+code\s+IN\s*\(([^)]+)\)/is)?.[1]?.match(/[A-Z][A-Z0-9_]+/g) || [];
  for (const code of one ? [one] : many) {
    const resource = resourceMap.get(code);
    if (!resource) continue;
    resource.routeName = statement.match(/route_name\s*=\s*'([^']+)'/i)?.[1] || resource.routeName;
    resource.type = statement.match(/type\s*=\s*'(MENU|PAGE|TAB|BUTTON|API)'/i)?.[1] || resource.type;
    if (/enabled\s*=\s*b?'0'/i.test(statement)) resource.enabled = false;
    if (/enabled\s*=\s*b?'1'/i.test(statement)) resource.enabled = true;
  }
}

const isLegacyDeadSource = (file) => /[\\/](?:usergroup|userGroup)[\\/]|AgentTabs\.vue$|TestAssetTabs\.vue$/.test(file);
const routeFiles = walk(path.join(frontendRoot, 'src/router/routes'), (file) => file.endsWith('.ts'));
const activeViewFiles = walk(path.join(frontendRoot, 'src/views'), (file) => /\.(?:vue|ts|tsx)$/.test(file) && !isLegacyDeadSource(file));
const routeText = [...routeFiles, path.join(frontendRoot, 'src/enums/routeEnum.ts')]
  .map((file) => fs.readFileSync(file, 'utf8'))
  .join('\n');
const activeViewText = activeViewFiles.map((file) => fs.readFileSync(file, 'utf8')).join('\n');

const exact = [];
const compatible = [];
const uncovered = [];
for (const resource of [...resourceMap.values()].filter((item) => item.enabled)) {
  const exactCode = routeText.includes(resource.code) || activeViewText.includes(resource.code);
  const routeBound = resource.routeName && routeText.includes(resource.routeName);
  const escapedPermission = resource.permissionId?.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const permissionDirective = escapedPermission
    ? new RegExp(`v-(?:permission|visible-permission|operable-permission)[^>]*${escapedPermission}`).test(activeViewText)
    : false;

  if (exactCode) exact.push(resource.code);
  else if ((resource.type === 'MENU' || resource.type === 'PAGE' || resource.type === 'TAB') && routeBound) compatible.push(resource.code);
  else if ((resource.type === 'BUTTON' || resource.type === 'API') && permissionDirective) compatible.push(resource.code);
  else uncovered.push(resource);
}

if (uncovered.length) {
  console.error('Enabled permission resources without an active route/template binding:');
  uncovered.forEach((resource) => console.error(`- ${resource.code} (${resource.type})`));
  process.exit(1);
}

console.log(`Permission resource coverage passed: ${exact.length} exact code bindings, ${compatible.length} audited compatibility bindings, 0 unbound resources.`);
