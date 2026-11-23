import test from 'node:test';
import assert from 'node:assert/strict';
import { z } from 'zod';
import { sharedConfigurationTools } from '../dist/tools/sharedConfigurations.js';

test('configuration tools expose the whole governed service contract', () => {
  assert.deepEqual(sharedConfigurationTools.map(x=>x.name.split('.').at(-1)).sort(),
    ['batch','list','read','resolve','retry_parse','save','select_version','versions'].sort());
  const resolve=sharedConfigurationTools.find(x=>x.name.endsWith('.resolve'));
  assert.equal(z.object(resolve.inputSchema).safeParse({projectId:'p',id:'c'}).success,false);
  assert.deepEqual(z.object(resolve.inputSchema).parse({projectId:'p',id:'c',sessionId:'session'}),{projectId:'p',id:'c',sessionId:'session'});
});
test('configuration adapters preserve identity, version and native authorization without local persistence', async () => {
  let observed;
  const tool=sharedConfigurationTools.find(x=>x.name.endsWith('.save'));
  const args={projectId:'p',systemKey:'system',environmentKey:'test',name:'fixture',kind:'PLAIN',value:'fixture-value',expectedVersion:0};
  const parsed=z.object(tool.inputSchema).parse(args);
  await tool.handler({callNativeTool:async(name,value)=>{observed={name,value};return {}}},parsed);
  assert.equal(observed.name,'metersphere.shared_configuration.save');
  assert.deepEqual(observed.value,args);
  assert.equal(z.object(tool.inputSchema).safeParse({...args,expectedVersion:-1}).success,false);
});
