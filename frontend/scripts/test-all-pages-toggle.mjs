import { effectScope, reactive, ref, watch, watchEffect } from 'vue';
import { cloneDeep } from 'lodash-es';

import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import { parse } from 'vue/compiler-sfc';

const source = readFileSync(new URL('../src/components/pure/ms-table/useTable.ts', import.meta.url), 'utf8');
const enums = ts.transpileModule(readFileSync(new URL('../src/enums/tableEnum.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText;
const enumExports = {};
vm.runInNewContext(enums, { exports: enumExports });
const { SelectAllEnum } = enumExports;
const hook = ts.transpileModule(source.replace(/^import .*;\s*$/gm, '').replaceAll('import.meta.env.DEV', 'false'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText;
const hookExports = {};
vm.runInNewContext(hook, {
  exports: hookExports,
  ref,
  reactive,
  watch,
  watchEffect,
  cloneDeep,
  useAppStore: () => ({ innerHeight: 900, pageSize: 10 }),
  useTableStore: () => ({}),
  useListRefresh: () => undefined,
  SelectAllEnum,
});

const table = parse(
  readFileSync(new URL('../src/components/pure/ms-table/base-table.vue', import.meta.url), 'utf8')
).descriptor;
const ast = ts.createSourceFile('table.ts', table.scriptSetup.content, ts.ScriptTarget.Latest, true);
const declaration = ast.statements.find(
  (n) =>
    ts.isVariableStatement(n) && n.declarationList.declarations.some((d) => d.name.getText(ast) === 'toggleAllPages')
);
assert.ok(declaration, 'production toggle handler');
const handler = ts.transpileModule(declaration.getText(ast), {}).outputText;

test('production toolbar toggle clears cross-page mode, selected keys and exclusions together', async () => {
  const scope = effectScope();
  try {
    const state = scope.run(() =>
      hookExports.default(
        async (p) => ({
          list: [{ id: String((p.current - 1) * 2 + 1) }, { id: String((p.current - 1) * 2 + 2) }],
          current: p.current,
          total: 6,
        }),
        { data: [{ id: '1' }, { id: '2' }], msPagination: { current: 1, pageSize: 2, total: 6 } }
      )
    );
    const props = {
      get selectorStatus() {
        return state.propsRes.value.selectorStatus;
      },
    };
    const toggle = vm.runInNewContext(`${handler};toggleAllPages;`, {
      props,
      handleSelectAllChange: state.propsEvent.value.selectAllChange,
      SelectAllEnum,
    });
    toggle();
    assert.equal(props.selectorStatus, SelectAllEnum.ALL);
    assert.equal(state.getSelectedCount(), 6);
    state.propsRes.value.msPagination.current = 2;
    await state.loadList();
    assert.ok(state.propsRes.value.selectedKeys.has('3'));
    state.propsEvent.value.rowSelectChange({ id: '3' });
    assert.equal(state.getSelectedCount(), 5);
    assert.equal(state.propsRes.value.excludeKeys.size, 1);
    toggle();
    assert.equal(state.getSelectedCount(), 0);
    assert.notEqual(props.selectorStatus, SelectAllEnum.ALL);
    assert.equal(state.propsRes.value.selectedKeys.size, 0);
    assert.equal(state.propsRes.value.excludeKeys.size, 0);
    toggle();
    assert.equal(state.getSelectedCount(), 6);
    state.propsEvent.value.selectAllChange(SelectAllEnum.CANCEL_ALL, true);
    assert.equal(state.getSelectedCount(), 0);
  } finally {
    scope.stop();
  }
});
