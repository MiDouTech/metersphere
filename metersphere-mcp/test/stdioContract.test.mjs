import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';

test('built stdio server advertises package version and current execution/create contracts', async () => {
  const client=new Client({name:'contract-test',version:'1'});
  const transport=new StdioClientTransport({command:process.execPath,args:['--max-old-space-size=128',fileURLToPath(new URL('../dist/index.js',import.meta.url))],env:{MS_BASE_URL:'http://127.0.0.1:1',MS_AGENT_TOKEN:'synthetic-unused',MS_PROJECT_ID:'fixture'}});
  try {
    await client.connect(transport);
    assert.equal(client.getServerVersion().version,JSON.parse(readFileSync(new URL('../package.json',import.meta.url),'utf8')).version);
    const {tools}=await client.listTools();
    const data=tools.find(t=>t.name==='metersphere.data_asset.manage');
    assert.ok(data);
    assert.ok(data.inputSchema.required.includes('sourceProjectId'));
    for(const action of ['create','update','delete','upload','replace_upload','batch','download']) assert.ok(data.inputSchema.properties.action.enum.includes(action));
    for(const name of ['metersphere.business_asset.search','metersphere.shared_configuration.list']) {
      const schema=tools.find(t=>t.name===name).inputSchema;
      assert.ok(schema.properties.sourceProjectId);
      assert.ok(!(schema.required??[]).includes('projectId'));
    }
    const submit=tools.find(t=>t.name==='metersphere.functional.execution.submit').inputSchema;
    assert.ok(submit.properties.evidenceIds);
    assert.ok(!submit.required.includes('contentHash'));
    assert.ok(!submit.required.includes('policyHash'));
    const upload=tools.find(t=>t.name==='metersphere.functional.execution.evidence.upload').inputSchema;
    for(const field of ['contentHash','stepNum','purpose'])assert.ok(!upload.required.includes(field));
    for(const name of ['create_functional_case','create_functional_module']) {
      const tool=tools.find(t=>t.name===name);
      assert.ok(tool, name);assert.ok(tool.inputSchema.required.includes('requestId'));
    }
  } finally { await client.close(); }
});
