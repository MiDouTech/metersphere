package io.metersphere.project.service;
import io.metersphere.system.dto.sdk.BaseTreeNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ModuleTreeRevisionTests {
 @Test void nestedNodesPreserveRevisionIdentityAndIndependentMetadata() {
  var service=new ModuleTreeService(){ public void updatePos(String id,long pos){throw new UnsupportedOperationException();} public void refreshPos(String parent){throw new UnsupportedOperationException();} };
  var root=new BaseTreeNode("root","Root","MODULE","NONE");
  var child=new BaseTreeNode("child","Child","MODULE","root");
  child.setProjectId("p");child.setUpdateTime(123L);child.putAttachInfo("source","bound");
  var grand=new BaseTreeNode("grand","Grand","MODULE","child");grand.setUpdateTime(456L);
  var tree=service.buildTreeAndCountResource(new ArrayList<>(List.of(grand,child,root)),false,"");
  var actual=tree.getFirst().getChildren().getFirst();
  assertEquals(123L,actual.getUpdateTime());assertEquals("p",actual.getProjectId());
  assertEquals(456L,actual.getChildren().getFirst().getUpdateTime());
  assertEquals("bound",actual.getAttachInfo().get("source"));
  actual.putAttachInfo("source","changed");assertEquals("bound",child.getAttachInfo().get("source"));
 }
}
