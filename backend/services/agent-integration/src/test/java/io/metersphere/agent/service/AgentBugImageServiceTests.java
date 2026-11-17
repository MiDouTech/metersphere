package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentBugDTO;
import io.metersphere.bug.domain.*;
import io.metersphere.bug.mapper.*;
import io.metersphere.project.domain.*;
import io.metersphere.project.mapper.*;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentBugImageServiceTests {
    AgentBusinessAccess access = mock(AgentBusinessAccess.class);
    BugMapper bugs = mock(BugMapper.class);
    BugContentMapper contents = mock(BugContentMapper.class);
    BugLocalAttachmentMapper locals = mock(BugLocalAttachmentMapper.class);
    FileAssociationMapper associations = mock(FileAssociationMapper.class);
    FileMetadataMapper metadata = mock(FileMetadataMapper.class);
    AgentBugImageStore store = mock(AgentBugImageStore.class);
    AgentBugImagePermission permissions = mock(AgentBugImagePermission.class);
    AgentBugImageFinalCheck finalCheck = mock(AgentBugImageFinalCheck.class);
    AgentBugImageService service = new AgentBugImageService(access, bugs, contents, locals, associations, metadata, store, mock(org.mybatis.spring.SqlSessionTemplate.class), permissions, finalCheck);
    BugContent content = new BugContent();
    AgentToken token = new AgentToken();
    byte[] png;

    @BeforeEach void setup() throws Exception {
        doAnswer(call -> { ((Runnable)call.getArgument(3)).run(); return null; }).when(finalCheck).run(anyString(), anyString(), anyString(), any(Runnable.class));
        token.setScopes("BUG_READ"); token.setUserId("u"); when(access.activeToken("BUG_READ")).thenReturn(token);
        Bug bug = new Bug(); bug.setId("b"); bug.setProjectId("p"); bug.setDeleted(false);
        when(bugs.selectByPrimaryKey("b")).thenReturn(bug);
        content.setBugId("b"); content.setDescription(html("f") + "text" + html("f"));
        when(contents.selectByPrimaryKey("b")).thenReturn(content);
        when(locals.selectByExample(any())).thenReturn(List.of(local("r", "f", "RICH_TEXT")));
        when(associations.selectByExample(any())).thenReturn(List.of());
        when(store.local(anyString(), anyString(), any())).thenReturn(new FileRequest("server-owned", "MINIO", "f"));
        when(store.managed(any())).thenReturn(new FileRequest("server-owned", "MINIO", "managed"));
        when(store.revision(any())).thenReturn("object-version-1");
        png = BugImageValidatorTests.image("png", 2, 2);
        when(store.read(any())).thenReturn(png); when(store.head(any())).thenReturn(Arrays.copyOf(png, 16));
    }
    static String html(String file) { return "<img src='/api/bug/attachment/preview/md/p/" + file + "/false' alt='step'>"; }
    static BugLocalAttachment local(String ref, String file, String source) {
        var value = new BugLocalAttachment(); value.setId(ref); value.setFileId(file); value.setFileName("evidence.png");
        value.setSize(100L); value.setSource(source); value.setBugId("b"); value.setCreateTime(1L); return value;
    }
    AgentBugDTO detail() { var result = new AgentBugDTO(); result.setId("b"); result.setProjectId("p"); result.setDescription(content.getDescription()); service.addIndex(result); return result; }
    String ref() { return (String) detail().getImages().getFirst().get("imageRef"); }
    void fails(String reason, Runnable action) { assertEquals("BUG_IMAGE_" + reason, assertThrows(MSException.class, action::run).getMessage()); }

    @Test void preservesTextPositionsAndOriginalBytesWithoutDecodingAtIndexTime() throws Exception {
        var detail = detail(); assertEquals(content.getDescription(), detail.getDescription());
        assertTrue(detail.getImagesComplete()); assertTrue(detail.getAttachmentImages().isEmpty());
        assertEquals(List.of(1,2), detail.getImages().stream().map(e -> e.get("occurrence")).toList());
        assertEquals(detail.getImages().get(0).get("imageRef"), detail.getImages().get(1).get("imageRef"));
        verify(store, never()).read(any());
        var result = service.read("p", "b", (String) detail.getImages().getFirst().get("imageRef"));
        assertArrayEquals(png, Base64.getDecoder().decode(result.data())); assertEquals("image/png", result.mimeType());
        assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(png), result.metadata().get("sha256"));
        assertEquals(png.length, result.metadata().get("sizeBytes"));
        verify(permissions, atLeast(2)).require("u", "p");
        verify(locals, never()).insert(any()); verify(associations, never()).insert(any());
    }
    @Test void removingOneOccurrenceKeepsReferenceButRemovingAllExpiresIt() {
        String ref = ref(); content.setDescription(html("f")); assertNotNull(service.read("p", "b", ref));
        content.setDescription("text only"); fails("REFERENCE_EXPIRED", () -> service.read("p", "b", ref));
    }
    @Test void descriptionAndAttachmentLifecyclesAreIndependent() {
        when(locals.selectByExample(any())).thenReturn(List.of(local("r","f","RICH_TEXT"), local("a","f","ATTACHMENT")));
        var detail = detail(); String desc = (String) detail.getImages().getFirst().get("imageRef");
        String att = (String) detail.getAttachmentImages().getFirst().get("imageRef"); assertNotEquals(desc, att);
        content.setDescription(""); fails("REFERENCE_EXPIRED", () -> service.read("p","b",desc)); assertNotNull(service.read("p","b",att));
        content.setDescription(html("f")); when(locals.selectByExample(any())).thenReturn(List.of(local("r","f","RICH_TEXT")));
        fails("REFERENCE_EXPIRED", () -> service.read("p","b",att)); assertNotNull(service.read("p","b",desc));
    }
    @Test void objectReplacementIncludingSameBytesExpiresTheIndexedReference() throws Exception {
        String ref = ref(); when(store.revision(any())).thenReturn("object-version-2");
        fails("REFERENCE_EXPIRED", () -> service.read("p","b",ref)); verify(store, never()).read(any());
    }
    @Test void versionSuffixCannotBeReplacedWithUnknownSentinel() {
        String ref = ref(); String forged = ref.substring(0,69) + "0".repeat(64);
        fails("REFERENCE_EXPIRED", () -> service.read("p","b",forged));
    }
    @Test void finalCheckRejectsRevocationEvenWhenImageWasAlreadyRead() throws Exception {
        String ref = ref(); when(store.read(any())).thenAnswer(call -> {
            doThrow(AgentBusinessAccess.denied()).when(permissions).require("u","p"); return png;
        });
        assertEquals("AGENT_RESOURCE_NOT_ACCESSIBLE", assertThrows(MSException.class, () -> service.read("p","b",ref)).getMessage());
    }
    @Test void finalCheckRejectsConcurrentSourceRemoval() throws Exception {
        String ref = ref(); when(store.read(any())).thenAnswer(call -> { content.setDescription(""); return png; });
        fails("REFERENCE_EXPIRED", () -> service.read("p","b",ref));
    }
    @Test void finalCheckRejectsConcurrentStorageReplacement() throws Exception {
        String ref = ref(); when(store.read(any())).thenAnswer(call -> { when(store.revision(any())).thenReturn("v2"); return png; });
        fails("REFERENCE_EXPIRED", () -> service.read("p","b",ref));
    }
    @Test void temporaryStorageFailureIsExplicitlyRetryableAndRestoresWithSameReference() throws Exception {
        String ref = ref(); when(store.read(any())).thenThrow(new IOException("private storage path"));
        fails("STORAGE_UNAVAILABLE", () -> service.read("p","b",ref));
        var mapped = new AgentSafeErrorMapper().toApiError(new MSException("BUG_IMAGE_STORAGE_UNAVAILABLE"), null);
        assertEquals(true, mapped.getDetails().get("retryable")); assertFalse(mapped.toString().contains("private storage"));
        doReturn(png).when(store).read(any()); assertNotNull(service.read("p","b",ref));
    }
    @Test void explicitReadScopeAndProjectAuthorizationPrecedeFileDiagnosis() throws Exception {
        token.setScopes("BUG_WRITE;BUG_ATTACHMENT");
        assertThrows(MSException.class, this::detail); assertThrows(MSException.class, () -> service.read("p","b","https://private"));
        verifyNoInteractions(store);
    }
    @Test void crossBugAndCrossProjectAreRejectedBeforeStorage() {
        assertThrows(MSException.class, () -> service.read("other","b","bi1_" + "0".repeat(129)));
        assertThrows(MSException.class, () -> service.read("p","other","bi1_" + "0".repeat(129)));
        verifyNoInteractions(store);
    }
    @Test void externalUnsavedAndMissingSourcesRetainPositionsWithoutFetching() throws Exception {
        content.setDescription("<img src='https://private.example/secret'><img src='blob:secret'><img src='/api/bug/attachment/preview/md/p/missing/false'>");
        var images = detail().getImages(); assertEquals(3, images.size());
        assertEquals(List.of("EXTERNAL_IMAGE","UNSAVED_IMAGE","RESOURCE_UNAVAILABLE"), images.stream().map(e->e.get("reason")).toList());
        assertFalse(images.toString().contains("private.example"));
        for (var image : images) fails((String)image.get("reason"), () -> service.read("p","b",(String)image.get("imageRef")));
        verify(store,never()).read(any()); verify(store,never()).head(any()); verify(store,never()).revision(any());
    }
    @Test void managedAssociationsAreSortedAndRequireOnlyDefectPermissions() {
        content.setDescription("");
        var first = association("a",1L); var second = association("z",1L);
        when(associations.selectByExample(any())).thenReturn(List.of(second,first));
        var file = new FileMetadata(); file.setId("managed"); file.setProjectId("p"); file.setName("proof"); file.setType("pdf");
        file.setSize(100L); file.setFileVersion("v1"); file.setUpdateTime(1L); when(metadata.selectByPrimaryKey("managed")).thenReturn(file);
        var detail = detail(); assertTrue(detail.getImages().isEmpty()); assertEquals(2,detail.getAttachmentImages().size());
        assertEquals(List.of("a","z"), detail.getAttachmentImages().stream().map(e->e.get("associationRef")).toList());
        String ref = (String)detail.getAttachmentImages().getFirst().get("imageRef"); assertNotNull(service.read("p","b",ref));
        file.setFileVersion("v2"); fails("REFERENCE_EXPIRED", () -> service.read("p","b",ref));
        verify(access, never()).project(anyString(), eq("PROJECT_FILE_MANAGEMENT:READ"), anyBoolean(), anyString());
    }
    static FileAssociation association(String id,long time) {
        var value = new FileAssociation(); value.setId(id); value.setSourceId("b"); value.setSourceType("BUG");
        value.setFileId("managed"); value.setFileVersion("v1"); value.setCreateTime(time); value.setUpdateTime(time); value.setDeleted(false); return value;
    }
    @Test void disabledCapabilityLeavesLegacyDetailAndBusinessDataUntouched() {
        org.springframework.test.util.ReflectionTestUtils.setField(service,"enabled",false);
        var detail=detail(); assertNull(detail.getImages()); verifyNoInteractions(store,locals,associations);
        fails("UNSUPPORTED", () -> service.read("p","b","not-a-ref"));
    }
    @Test void invalidReferencesNeverSelectClientPaths() {
        for (String ref : List.of("", "https://example.org/a.png", "../file", "a".repeat(4096)))
            fails("INVALID_ARGUMENT", () -> service.read("p","b",ref));
        verifyNoInteractions(store);
    }
    @Test void malformedHtmlDoesNotMasqueradeAsACompleteEmptyIndex() {
        content.setDescription("text<img src='unterminated");
        var detail=detail();assertFalse(detail.getImagesComplete());assertEquals(content.getDescription(),detail.getDescription());
        assertTrue(detail.getImageWarnings().contains("DESCRIPTION_INDEX_INCOMPLETE"));
    }
    @Test void preservesLegacyNullDescriptionAndDoesNotIndexADifferentTextSnapshot() {
        var result=new AgentBugDTO();result.setId("b");result.setProjectId("p");service.addIndex(result);
        assertNull(result.getDescription());assertTrue(result.getImages().isEmpty());
        result.setDescription(html("removed"));service.addIndex(result);
        assertEquals(html("removed"),result.getDescription());assertEquals("RESOURCE_UNAVAILABLE",result.getImages().getFirst().get("reason"));
    }
    @Test void oneUnavailableLibrarySourceDoesNotHideTextOrOtherImages() {
        when(associations.selectByExample(any())).thenReturn(List.of(association("a",1L)));
        var file=new FileMetadata();file.setId("managed");file.setProjectId("p");file.setName("bad");file.setType("png");
        when(metadata.selectByPrimaryKey("managed")).thenReturn(file);when(store.managed(file)).thenThrow(new IllegalStateException("hidden configuration"));
        var detail=detail();assertEquals(2,detail.getImages().size());assertFalse(detail.getAttachmentImagesComplete());
        var unavailable=detail.getAttachmentImages().getFirst();assertEquals("RESOURCE_UNAVAILABLE",unavailable.get("reason"));
        assertNotNull(service.read("p","b",(String)detail.getImages().getFirst().get("imageRef")));
        fails("RESOURCE_UNAVAILABLE",()->service.read("p","b",(String)unavailable.get("imageRef")));
    }
    @Test void removingOneOfSeveralLibraryAssociationsPreservesAnEffectiveDescriptionReference() {
        content.setDescription(html("managed"));var a=association("a",1L);var z=association("z",1L);
        when(associations.selectByExample(any())).thenReturn(List.of(a,z));
        var file=new FileMetadata();file.setId("managed");file.setProjectId("p");file.setName("proof");file.setType("png");file.setFileVersion("v1");
        when(metadata.selectByPrimaryKey("managed")).thenReturn(file);
        var detail=detail();String desc=(String)detail.getImages().getFirst().get("imageRef");
        String attachment=(String)detail.getAttachmentImages().getFirst().get("imageRef");
        when(associations.selectByExample(any())).thenReturn(List.of(z));
        assertNotNull(service.read("p","b",desc));fails("REFERENCE_EXPIRED",()->service.read("p","b",attachment));
    }
    @Test void twoConcurrentReadsRejectAChangeBeforeTheirFinalChecks() throws Exception {
        String ref=ref();var entered=new java.util.concurrent.CountDownLatch(2);var release=new java.util.concurrent.CountDownLatch(1);
        when(store.read(any())).thenAnswer(call->{entered.countDown();assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));return png;});
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=workers.submit(()->service.read("p","b",ref));var second=workers.submit(()->service.read("p","b",ref));
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));content.setDescription("");release.countDown();
            for(var future:List.of(first,second)){
                var failure=assertThrows(java.util.concurrent.ExecutionException.class,()->future.get(5,java.util.concurrent.TimeUnit.SECONDS));
                assertEquals("BUG_IMAGE_REFERENCE_EXPIRED",failure.getCause().getMessage());
            }
        }finally{release.countDown();}
    }
    @Test void finalAuthorizationCannotHideALateBodyRemoval() {
        String ref=ref();var calls=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call->{if(calls.incrementAndGet()==2)content.setDescription("");return null;}).when(permissions).require("u","p");
        fails("REFERENCE_EXPIRED",()->service.read("p","b",ref));
    }
    @Test void finalAuthorizationCannotHideALateStorageReplacement() throws Exception {
        String ref=ref();var calls=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call->{if(calls.incrementAndGet()==2)doReturn("v2").when(store).revision(any());return null;}).when(permissions).require("u","p");
        fails("REFERENCE_EXPIRED",()->service.read("p","b",ref));
    }
    @Test void lateAttachmentDetachAndBugDeletionAreRejected() {
        when(locals.selectByExample(any())).thenReturn(List.of(local("a","f","ATTACHMENT")));
        String ref=(String)detail().getAttachmentImages().getFirst().get("imageRef");var calls=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call->{if(calls.incrementAndGet()==2)when(locals.selectByExample(any())).thenReturn(List.of());return null;}).when(permissions).require("u","p");
        fails("REFERENCE_EXPIRED",()->service.read("p","b",ref));
        doNothing().when(permissions).require("u","p");when(locals.selectByExample(any())).thenReturn(List.of(local("a","f","ATTACHMENT")));calls.set(0);
        doAnswer(call->{if(calls.incrementAndGet()==2)when(bugs.selectByPrimaryKey("b")).thenReturn(null);return null;}).when(permissions).require("u","p");
        assertThrows(MSException.class,()->service.read("p","b",ref));
    }
    @Test void changesAfterTheDecisionAffectOnlyFollowingReads() {
        String ref=ref();
        doAnswer(call->{((Runnable)call.getArgument(3)).run();content.setDescription("");return null;}).when(finalCheck).run(anyString(),anyString(),anyString(),any(Runnable.class));
        assertNotNull(service.read("p","b",ref));fails("REFERENCE_EXPIRED",()->service.read("p","b",ref));
    }
    @Test void indexStorageOutageIsExplicitAndRequiresARefreshedBoundReference() throws Exception {
        when(locals.selectByExample(any())).thenReturn(List.of(local("r","f","RICH_TEXT"),local("a","f","ATTACHMENT")));
        when(store.revision(any())).thenThrow(new IOException("temporary"));
        var index=detail();
        for(var entry:List.of(index.getImages().getFirst(),index.getAttachmentImages().getFirst())){
            assertEquals(false,entry.get("readable"));assertEquals("STORAGE_UNAVAILABLE",entry.get("reason"));
            assertEquals(true,entry.get("retryable"));assertEquals("REFRESH_BUG",entry.get("nextAction"));
            String ref=(String)entry.get("imageRef");assertFalse(ref.endsWith("0".repeat(64)));
            doReturn("object-version-1").when(store).revision(any());
            fails("INDEX_STORAGE_UNAVAILABLE",()->service.read("p","b",ref));
        }
        var refreshed=detail();assertEquals(true,refreshed.getImages().getFirst().get("readable"));
        assertNotNull(service.read("p","b",(String)refreshed.getImages().getFirst().get("imageRef")));
        assertNotNull(service.read("p","b",(String)refreshed.getAttachmentImages().getFirst().get("imageRef")));
    }
    @Test void missingObjectAtIndexTimeIsNotATemporaryStorageFailure() throws Exception {
        when(locals.selectByExample(any())).thenReturn(List.of(local("r","f","RICH_TEXT"),local("a","f","ATTACHMENT")));
        when(store.revision(any())).thenThrow(new FileNotFoundException("hidden"));
        var index=detail();
        for(var entry:List.of(index.getImages().getFirst(),index.getAttachmentImages().getFirst())){
            assertEquals(false,entry.get("readable"));assertEquals("RESOURCE_UNAVAILABLE",entry.get("reason"));assertEquals(false,entry.get("retryable"));
            fails("RESOURCE_UNAVAILABLE",()->service.read("p","b",(String)entry.get("imageRef")));
        }
    }
}
