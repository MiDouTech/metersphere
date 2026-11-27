package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.metersphere.sdk.file.*;
import io.minio.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@EnabledIfEnvironmentVariable(named="MSP_TEST_MINIO_PROPERTIES", matches=".+")
class DataAssetRealMinioIntegrationTests {
 private MinioRepository repository() throws Exception {
  var properties=new Properties();
  try(var input=java.nio.file.Files.newInputStream(java.nio.file.Path.of(System.getenv("MSP_TEST_MINIO_PROPERTIES")))){properties.load(input);}
  var client=MinioClient.builder().endpoint(properties.getProperty("endpoint")).credentials(properties.getProperty("accessKey"),properties.getProperty("secretKey")).build();
  String bucket="data-assets-it-"+UUID.randomUUID();
  client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
  var repository=new MinioRepository();repository.init(client);ReflectionTestUtils.setField(repository,"bucket",bucket);return repository;
 }
 private void assertStored(DataAssetServiceTests fixture,MinioRepository repository,String id,byte[] bytes) throws Exception {
  var source=fixture.jdbc.queryForMap("SELECT storage_path FROM data_asset_file WHERE version_id=?",fixture.service.detail(id).get("currentVersionId"));
  String path=source.get("storage_path").toString();assertTrue(path.startsWith("organization/org/data-assets/"));
  assertArrayEquals(bytes,fixture.service.file(id,null));
  repository.delete(new FileRequest(path,"MINIO","source"));
 }
 @Test void exactThirtyMiBOriginalRoundTripsThroughRealRepository() throws Exception {
  var repository=repository();var fixture=new DataAssetServiceTests();fixture.setup();
  var transaction=new TransactionTemplate(new DataSourceTransactionManager(fixture.jdbc.getDataSource()));
  byte[] bytes=new byte[DataAssetLimits.MAX_BYTES];Arrays.fill(bytes,(byte)'a');
  try(var center=mockStatic(FileCenter.class)) {
   center.when(()->FileCenter.getRepository("MINIO")).thenReturn(repository);
   var result=transaction.execute(status->fixture.service.upload(bytes,"large.csv",Map.of("name","large","format","CSV","content",new DataAssetParser().parse(bytes,"CSV"))));
   assertStored(fixture,repository,result.get("id").toString(),bytes);
   var invalid=new FileRequest("data-assets/org/invalid","MINIO","source");
   assertThrows(io.metersphere.sdk.exception.MSException.class,()->repository.saveFile(new byte[]{1},invalid));
  }
 }
 @Test void hundredThousandCsvRowsAsyncParseKeepsExactOriginal() throws Exception {
  var repository=repository();var fixture=new DataAssetServiceTests();fixture.setup();
  var access=mock(GlobalAssetAccess.class);when(access.authorize(isNull(),anyString())).thenReturn(new GlobalAssetAccess.Scope("org","user"));
  var worker=new DataAssetParseJobService(fixture.jdbc,access,new DataAssetParser());
  var transaction=new TransactionTemplate(new DataSourceTransactionManager(fixture.jdbc.getDataSource()));
  byte[] bytes="a,b\r\n".repeat(100000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
  try(var center=mockStatic(FileCenter.class)) {
   center.when(()->FileCenter.getRepository("MINIO")).thenReturn(repository);
   var result=transaction.execute(status->worker.upload(bytes,"rows.csv","rows",null,"real-minio"));
   assertEquals("PENDING",result.get("parseStatus"));worker.drain();
   String id=result.get("id").toString();assertEquals("READY",fixture.service.detail(id).get("parseStatus"));
   assertStored(fixture,repository,id,bytes);
  }
 }
}
