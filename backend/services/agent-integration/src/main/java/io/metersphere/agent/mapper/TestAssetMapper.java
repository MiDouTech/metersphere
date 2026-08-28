package io.metersphere.agent.mapper;

import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.dto.TestAssetDocumentDTO;
import io.metersphere.agent.dto.TestAssetRelationDTO;
import io.metersphere.agent.dto.TestAssetContextDocumentDTO;
import io.metersphere.agent.dto.TestAssetCatalogItemDTO;
import io.metersphere.agent.dto.TestAssetExecutableSnapshotDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TestAssetMapper {
    TestAssetVersionDTO selectByHash(@Param("projectId") String projectId,
                                     @Param("assetType") String assetType,
                                     @Param("assetId") String assetId,
                                     @Param("contentHash") String contentHash);

    Integer selectMaxVersion(@Param("projectId") String projectId,
                             @Param("assetType") String assetType,
                             @Param("assetId") String assetId);

    TestAssetVersionDTO selectLatest(@Param("projectId") String projectId,
                                     @Param("assetType") String assetType,
                                     @Param("assetId") String assetId);

    TestAssetVersionDTO selectLatestPublished(@Param("projectId") String projectId,
                                              @Param("assetType") String assetType,
                                              @Param("assetId") String assetId);

    TestAssetVersionDTO selectVersionById(@Param("id") String id);

    int deprecateVersion(@Param("id") String id,
                         @Param("projectId") String projectId,
                         @Param("assetType") String assetType,
                         @Param("assetId") String assetId);

    List<TestAssetCatalogItemDTO> selectCatalog(@Param("projectId") String projectId,
                                                @Param("assetType") String assetType,
                                                @Param("keyword") String keyword,
                                                @Param("status") String status,
                                                @Param("updatedAfter") Long updatedAfter,
                                                @Param("offset") long offset,
                                                @Param("pageSize") int pageSize);

    long countCatalog(@Param("projectId") String projectId,
                      @Param("assetType") String assetType,
                      @Param("keyword") String keyword,
                      @Param("status") String status,
                      @Param("updatedAfter") Long updatedAfter);

    TestAssetCatalogItemDTO selectCatalogItem(@Param("projectId") String projectId,
                                              @Param("assetType") String assetType,
                                              @Param("assetId") String assetId);

    TestAssetExecutableSnapshotDTO selectExecutableSnapshot(@Param("projectId") String projectId,
                                                            @Param("assetType") String assetType,
                                                            @Param("assetId") String assetId);

    List<TestAssetDocumentDTO> selectDocuments(@Param("projectId") String projectId,
                                               @Param("parseStatus") String parseStatus,
                                               @Param("keyword") String keyword,
                                               @Param("offset") long offset,
                                               @Param("pageSize") int pageSize);

    long countDocuments(@Param("projectId") String projectId,
                        @Param("parseStatus") String parseStatus,
                        @Param("keyword") String keyword);

    List<TestAssetVersionDTO> selectVersions(@Param("projectId") String projectId,
                                             @Param("allowedTypes") List<String> allowedTypes,
                                             @Param("assetType") String assetType,
                                             @Param("assetId") String assetId,
                                             @Param("keyword") String keyword,
                                             @Param("offset") long offset,
                                             @Param("pageSize") int pageSize);

    long countVersions(@Param("projectId") String projectId,
                       @Param("allowedTypes") List<String> allowedTypes,
                       @Param("assetType") String assetType,
                       @Param("assetId") String assetId,
                       @Param("keyword") String keyword);

    List<TestAssetRelationDTO> selectRelations(@Param("projectId") String projectId,
                                               @Param("allowedTypes") List<String> allowedTypes,
                                               @Param("assetType") String assetType,
                                               @Param("assetId") String assetId,
                                               @Param("relationType") String relationType,
                                               @Param("keyword") String keyword,
                                               @Param("offset") long offset,
                                               @Param("pageSize") int pageSize);

    long countRelations(@Param("projectId") String projectId,
                        @Param("allowedTypes") List<String> allowedTypes,
                        @Param("assetType") String assetType,
                        @Param("assetId") String assetId,
                        @Param("relationType") String relationType,
                        @Param("keyword") String keyword);

    List<TestAssetContextDocumentDTO> selectDocumentContextForCases(@Param("projectId") String projectId,
                                                                    @Param("caseAssetIds") List<String> caseAssetIds);

    void insertVersion(TestAssetVersionDTO version);

    void insertRelation(@Param("id") String id,
                        @Param("projectId") String projectId,
                        @Param("relationType") String relationType,
                        @Param("sourceAssetType") String sourceAssetType,
                        @Param("sourceAssetId") String sourceAssetId,
                        @Param("sourceVersionId") String sourceVersionId,
                        @Param("targetAssetType") String targetAssetType,
                        @Param("targetAssetId") String targetAssetId,
                        @Param("targetVersionId") String targetVersionId,
                        @Param("metadata") String metadata,
                        @Param("createdBy") String createdBy,
                        @Param("createdAt") long createdAt);
}
