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
