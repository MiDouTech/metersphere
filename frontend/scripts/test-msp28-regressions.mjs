import ts from 'typescript';
import { compileScript, compileTemplate, parse } from 'vue/compiler-sfc';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const source = readFileSync(new URL('../src/api/modules/case-management/featureCase.ts', import.meta.url), 'utf8');
const ast = ts.createSourceFile('featureCase.ts', source, ts.ScriptTarget.Latest, true);
const declaration = ast.statements.find(
  (node) => ts.isFunctionDeclaration(node) && node.name?.text === 'importCaseAssetFile'
);
const code = ts.transpileModule(declaration.getText(ast), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;

for (const format of ['excel', 'xmind']) {
  test(`asset ${format} import unwraps the upload envelope before status polling`, async () => {
    const jobId = '1730245184560734208';
    const exports = {};
    const calls = [];
    const file = { name: `cases.${format}` };
    const MSR = {
      uploadFile: async (...args) => {
        calls.push(args);
        return { code: 100200, data: { jobId, status: 'RUNNING' } };
      },
    };
    new Function('exports', 'MSR', code)(exports, MSR);
    const result = await exports.importCaseAssetFile('catalog', file, format, false);
    assert.equal(result.jobId, jobId);
    assert.equal(result.status, 'RUNNING');
    assert.equal(calls.length, 1);
    assert.equal(calls[0][0].url, `/case-asset/case/import/${format}/catalog`);
    assert.deepEqual(calls[0][1].fileList, [file]);
    assert.equal(calls[0][1].request.cover, false);
  });
}

for (const path of [
  'views/case-management/caseManagementFeature/components/import/importJob.vue',
  'components/business/hifox-handoff/configuration.vue',
]) {
  test(`modified UI compiles: ${path}`, () => {
    const text = readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8');
    const { descriptor, errors } = parse(text);
    assert.deepEqual(errors, []);
    const script = compileScript(descriptor, { id: 'msp28' });
    assert.deepEqual(
      compileTemplate({
        source: descriptor.template.content,
        filename: path,
        id: 'msp28',
        compilerOptions: { bindingMetadata: script.bindings },
      }).errors,
      []
    );
  });
}
