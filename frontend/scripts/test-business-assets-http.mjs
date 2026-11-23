import assert from 'node:assert/strict';
import axios from 'axios';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';
const cancelSource = await readFile(new URL('../src/api/http/axiosCancel.ts', import.meta.url), 'utf8');
const apiSource = await readFile(new URL('../src/api/modules/business-assets.ts', import.meta.url), 'utf8');
function javascript(source) {
  const ast = ts.createSourceFile('source.ts', source, ts.ScriptTarget.Latest, true);
  const body = ast.statements
    .filter((n) => !ts.isImportDeclaration(n))
    .map((n) => n.getText(ast).replace(/^export /, ''))
    .join('\n');
  return ts.transpileModule(body, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
}
const { AxiosCanceler } = runInNewContext(javascript(cancelSource) + ';({AxiosCanceler});', {
  axios,
  isFunction: (value) => typeof value === 'function',
});
test('real Axios cancellation does not drop active or archived business directory reads', async () => {
  const canceler = new AxiosCanceler();
  const transport = axios.create({
    adapter: async (config) => ({
      data: [{ archived: config.params.archived }],
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }),
  });
  transport.interceptors.request.use((config) => {
    if (!config.requestOptions?.ignoreCancelToken) canceler.addPending(config);
    return config;
  });
  transport.interceptors.response.use((response) => {
    canceler.completePending(response.config);
    return response;
  });
  const MSR = {
    get: async (config, options) => {
      const response = await transport.request({ ...config, method: 'get', requestOptions: options });
      return response.data;
    },
  };
  const { listBusinessAssets } = runInNewContext(javascript(apiSource) + ';({listBusinessAssets});', { MSR });
  const [active, archived] = await Promise.all([
    listBusinessAssets('fixture'),
    listBusinessAssets('fixture', '', true),
  ]);
  assert.equal(active[0].archived, false);
  assert.equal(archived[0].archived, true);
});
