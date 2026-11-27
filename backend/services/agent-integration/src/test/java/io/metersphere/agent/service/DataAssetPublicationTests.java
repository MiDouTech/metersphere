package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.metersphere.agent.controller.DataAssetPublicationController;
import io.metersphere.agent.dto.TestAssetVersionDTO;
import java.util.*;
import org.junit.jupiter.api.Test;

class DataAssetPublicationTests {
  @Test void publishesSelectedImmutableVersionAndPreservesNullJson() {
    var data=mock(DataAssetService.class); var global=mock(GlobalAssetAccess.class);
    var access=mock(AgentBusinessAccess.class); var versions=mock(TestAssetVersionService.class);
    when(global.authorize("p","TEST_DATA_ASSET:READ")).thenReturn(new GlobalAssetAccess.Scope("o","u"));
    when(access.userHasPermission("u","p","TEST_ASSET_VERSION:MANAGE")).thenReturn(true);
    Map<String,Object> resolved=new HashMap<>(); resolved.put("versionId","v1");
    resolved.put("format","JSON"); resolved.put("content",null);
    when(data.resolveForProject("p","a","v1")).thenReturn(resolved);
    var published=new TestAssetVersionDTO(); published.setId("bridge");
    when(versions.publish(eq("p"),eq("DATASET"),eq("a"),eq("v1"),anyString(),eq("u"))).thenReturn(published);
    assertSame(published,new DataAssetPublicationController(data,global,access,versions).publish("a","p","v1"));
    verify(versions).publish(eq("p"),eq("DATASET"),eq("a"),eq("v1"),contains("JSON"),eq("u"));
  }
  @Test void rejectsProjectWithoutPublicationPermissionBeforeReadingContent() {
    var data=mock(DataAssetService.class); var global=mock(GlobalAssetAccess.class);
    var access=mock(AgentBusinessAccess.class); var versions=mock(TestAssetVersionService.class);
    when(global.authorize("p","TEST_DATA_ASSET:READ")).thenReturn(new GlobalAssetAccess.Scope("o","u"));
    assertThrows(io.metersphere.sdk.exception.MSException.class,
        ()->new DataAssetPublicationController(data,global,access,versions).publish("a","p",null));
    verifyNoInteractions(data,versions);
  }
}
