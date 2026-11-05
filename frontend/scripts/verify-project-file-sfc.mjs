import { readFileSync } from 'node:fs';
import { compileScript, compileTemplate, parse } from 'vue/compiler-sfc';
for (const file of ['views/project-management/fileManagement/components/fileDetailDrawer.vue', 'views/setting/system/agentIntegration/index.vue']) {
  const { descriptor, errors } = parse(readFileSync(new URL(`../src/${file}`, import.meta.url), 'utf8'));
  if (errors.length) throw new Error(String(errors));
  const script = compileScript(descriptor, { id: file });
  const template = compileTemplate({ source: descriptor.template.content, filename: file, id: file, compilerOptions: { bindingMetadata: script.bindings, expressionPlugins: ['typescript'] } });
  if (template.errors.length) throw new Error(String(template.errors));
  console.log(`SFC syntax/template OK: ${file}`);
}
