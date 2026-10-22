async (page) => {
  await page.goto('http://127.0.0.1:15175/#/execution/platforms?orgId=100001&pId=100001100001');
  await page.getByRole('button', {name:'新增配置',exact:true}).waitFor();
  return await page.evaluate(async () => {
    const projectId='100001100001';
    const headers={'X-AUTH-TOKEN':localStorage.getItem('sessionId')||'', 'CSRF-TOKEN':localStorage.getItem('csrfToken')||'',PROJECT:projectId,ORGANIZATION:'100001'};
    const check=(ok,message)=>{if(!ok)throw new Error(message);};
    const api=async(path,method='GET',body)=>{
      const response=await fetch('/api'+path,{method,headers:body instanceof FormData?headers:{...headers,'Content-Type':'application/json'},body:body===undefined?undefined:body instanceof FormData?body:JSON.stringify(body)});
      const json=await response.json(); return {status:response.status,data:json.data??json,code:json.code};
    };
    const name='binding-acceptance-'+Date.now();
    const form=new FormData();form.append('request',new Blob([JSON.stringify({projectId,name})],{type:'application/json'}));
    const env=await api('/project/environment/add','POST',form);
    check(env.status===200 && env.data.id,'Create real environment');
    let profile;
    const request={projectId,name,baseUrl:'https://example.com',allowedOrigins:['https://example.com'],environmentType:'TEST',runnerType:'BROWSER',enabled:true};
    try {
      const personal=await api('/ai/environment-profiles','POST',request);
      check(personal.status===200 && personal.data.id,'Personal configuration requires no published asset');
      profile=personal.data;
      check(!profile.environmentAssetId,'Unbound profile must not invent asset');
      const scope=profile.environmentId;
      const invalid=await api('/ai/environment-profiles/'+profile.id,'PUT',{...request,version:profile.version,environmentAssetId:env.data.id});
      check(invalid.status===400 && invalid.data.code==='ENVIRONMENT_ASSET_UNAVAILABLE','Unpublished environment must reject');
      const publish=await api('/test-assets/catalog/ENVIRONMENT/'+env.data.id+'/publish?projectId='+projectId,'POST');
      check(publish.status===200 && publish.data.assetVersionId,'Publish real environment');
      const bound=await api('/ai/environment-profiles/'+profile.id,'PUT',{...request,version:profile.version,environmentAssetId:env.data.id});
      check(bound.status===200 && bound.data.environmentAssetId===env.data.id,'Published binding persists');
      check(bound.data.environmentId===scope && bound.data.version===profile.version+1,'Credential identity stays stable and version increments');
      const stale=await api('/ai/environment-profiles/'+profile.id,'PUT',{...request,version:profile.version,environmentAssetId:env.data.id});
      check(stale.status===409 && stale.data.code==='ENVIRONMENT_PROFILE_VERSION_CONFLICT','Stale profile must return safe conflict');
      profile=bound.data;
      const wrong=await api('/ai/environment-profiles/'+profile.id,'PUT',{...request,version:profile.version,environmentAssetId:'nonexistent-binding'});
      check(wrong.status===400 && wrong.data.code==='ENVIRONMENT_ASSET_UNAVAILABLE','Missing binding rejects');
      const changed=await api('/ai/environment-profiles/'+profile.id,'PUT',{...request,version:profile.version,environmentId:'another-scope'});
      check(changed.status===400 && changed.data.code==='ENVIRONMENT_CREDENTIAL_SCOPE_IMMUTABLE','Scope cannot be replaced');
      const legacy=await api('/ai/environment-profiles/'+profile.id,'PUT',{...request,version:profile.version});
      check(legacy.status===200 && legacy.data.environmentAssetId===env.data.id,'Legacy update preserves binding');
      profile=legacy.data;
      const unbound=await api('/ai/environment-profiles/'+profile.id,'PUT',{...request,version:profile.version,environmentAssetId:''});
      check(unbound.status===200 && !unbound.data.environmentAssetId && unbound.data.environmentId===scope,'Explicit unbind preserves credentials');
      profile=unbound.data;
      return {personalSave:true,unpublishedRejected:true,publishedBinding:true,staleConflict:true,scopeImmutable:true,missingRejected:true,legacyPreserved:true,explicitUnbind:true,fixtureProfileId:profile.id};
    } finally {
      if(profile) await api('/ai/environment-profiles/'+profile.id+'/disable','POST');
      await api('/project/environment/delete/'+env.data.id);
    }
  });
}
