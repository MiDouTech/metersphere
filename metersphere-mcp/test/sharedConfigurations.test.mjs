import test from 'node:test';
import assert from 'node:assert/strict';
import { z } from 'zod';
import { sharedConfigurationTools } from '../dist/tools/sharedConfigurations.js';

test('configuration tools expose the whole governed service contract', () => {
  assert.deepEqual(sharedConfigurationTools.map(x=>x.name.split('.').at(-1)).sort(),
    ['batch','list','read','resolve','retry_parse','save','select_version','versions','audits'].sort());
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

test('audit metadata query forwards optional filters and enforces pagination without extra grants', async () => {
  const tool = sharedConfigurationTools.find(x=>x.name.endsWith('.audits'));
  assert.ok(tool);
  const schema = z.object(tool.inputSchema);
  assert.deepEqual(schema.parse({projectId:'p',id:'c'}),{projectId:'p',id:'c'});
  for (const extra of [{page:0},{pageSize:101},{version:0},{eventType:'UNKNOWN'}])
    assert.equal(schema.safeParse({projectId:'p',id:'c',...extra}).success,false);
  const args={projectId:'p',id:'c',page:2,pageSize:10,eventType:'SELECT_VERSION',version:1};
  const result=await tool.handler({callNativeTool:async(name,value)=>{
    assert.equal(name,'metersphere.shared_configuration.audits');assert.deepEqual(value,args);
    return {items:[{eventType:'SELECT_VERSION',reason:'synthetic rollback'}],total:1};
  }},schema.parse(args));
  assert.equal(result.total,1);
});
