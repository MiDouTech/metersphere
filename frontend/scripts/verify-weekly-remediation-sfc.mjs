import ts from 'typescript';

import { readFileSync } from 'node:fs';
import { compileScript, compileTemplate, parse } from 'vue/compiler-sfc';

// Syntax/template gate only. This intentionally does not claim full vue-tsc semantic checking.
const files = [
  'components/pure/ms-table/batchAction.vue',
  'components/pure/ms-table/base-table.vue',
  'components/business/ms-detail-drawer/index.vue',
  'components/business/ms-prev-next-button/index.vue',
  'views/case-management/caseManagementFeature/components/caseTable.vue',
  'views/case-management/caseReview/components/index/reviewTable.vue',
  'views/test-plan/testPlan/components/planTable.vue',
  'views/setting/system/agentIntegration/index.vue',
];
files.forEach((file) => {
  const { descriptor, errors } = parse(readFileSync(new URL(`../src/${file}`, import.meta.url), 'utf8'));
  if (errors.length) throw new Error(`${file}: ${errors.join(', ')}`);
  const script = compileScript(descriptor, { id: file });
  const compiled = compileTemplate({
    source: descriptor.template.content,
    filename: file,
    id: file,
    compilerOptions: { bindingMetadata: script.bindings, expressionPlugins: ['typescript'] },
  });
  if (compiled.errors.length) throw new Error(`${file}: ${compiled.errors.join(', ')}`);
  const syntax = ts.transpileModule(script.content, { reportDiagnostics: true });
  const failures = syntax.diagnostics?.filter((d) => d.category === ts.DiagnosticCategory.Error) || [];
  if (failures.length)
    throw new Error(
      ts.formatDiagnosticsWithColorAndContext(failures, {
        getCurrentDirectory: () => '',
        getCanonicalFileName: (name) => name,
        getNewLine: () => '\n',
      })
    );
  console.log(`SFC syntax/template OK: ${file}`);
});
