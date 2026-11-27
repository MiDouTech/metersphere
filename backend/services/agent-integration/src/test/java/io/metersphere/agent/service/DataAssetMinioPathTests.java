package io.metersphere.agent.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.metersphere.sdk.file.*;
import io.minio.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
class DataAssetMinioPathTests {
 @Test void realRepositoryValidatesOrganizationPathsAtNetworkBoundary() throws Exception {
  var client=mock(MinioClient.class);
  var repository=new MinioRepository();
  ReflectionTestUtils.setField(repository,"bucket","test-bucket");repository.init(client);
  var request=new FileRequest("organization/org/data-assets/version","MINIO","source.csv");
  repository.saveFile(new byte[]{1},request);
  repository.getFileAsStream(request);
  repository.delete(request);
  verify(client).putObject(argThat(a -> a.object().equals("organization/org/data-assets/version/source.csv")));
  verify(client).getObject(argThat(a -> a.object().equals("organization/org/data-assets/version/source.csv")));
  verify(client).removeObject(argThat(a -> a.object().equals("organization/org/data-assets/version/source.csv")));
  var invalid=new FileRequest("data-assets/org/version","MINIO","source.csv");
  assertThrows(io.metersphere.sdk.exception.MSException.class,()->repository.saveFile(new byte[]{1},invalid));
  assertThrows(io.metersphere.sdk.exception.MSException.class,()->repository.getFileAsStream(invalid));
  assertThrows(io.metersphere.sdk.exception.MSException.class,()->repository.delete(invalid));
  verifyNoMoreInteractions(client);
 }
}
