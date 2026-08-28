package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.dto.TestAssetRefDTO;
import io.metersphere.agent.mapper.TestAssetMapper;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import io.metersphere.sdk.exception.MSException;

import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class TestAssetVersionService {
    @Resource
    private TestAssetMapper mapper;

    public TestAssetVersionDTO publish(String projectId, String assetType, String assetId,
                                       String sourceVersion, String snapshot, String userId) {
        String content = StringUtils.defaultString(snapshot);
        String hash = DigestUtils.sha256Hex(content);
        TestAssetVersionDTO existing = mapper.selectByHash(projectId, assetType, assetId, hash);
        if (existing != null) {
            return existing;
        }
        Integer max = mapper.selectMaxVersion(projectId, assetType, assetId);
        long now = System.currentTimeMillis();
        TestAssetVersionDTO version = new TestAssetVersionDTO();
        version.setId(IDGenerator.nextStr());
        version.setProjectId(projectId);
        version.setAssetType(assetType);
        version.setAssetId(assetId);
        version.setVersionNo((max == null ? 0 : max) + 1);
        version.setSourceVersion(StringUtils.trimToNull(sourceVersion));
        version.setContentHash(hash);
        version.setContentSnapshot(content);
        version.setStatus("PUBLISHED");
        version.setCreatedBy(userId);
        version.setCreatedAt(now);
        version.setPublishedBy(userId);
        version.setPublishedAt(now);
        try {
            mapper.insertVersion(version);
            return version;
        } catch (DuplicateKeyException concurrentPublish) {
            TestAssetVersionDTO published = mapper.selectByHash(projectId, assetType, assetId, hash);
            if (published != null) {
                return published;
            }
            Integer retryMax = mapper.selectMaxVersion(projectId, assetType, assetId);
            version.setId(IDGenerator.nextStr());
            version.setVersionNo((retryMax == null ? 0 : retryMax) + 1);
            try {
                mapper.insertVersion(version);
                return version;
            } catch (DuplicateKeyException secondCollision) {
                TestAssetVersionDTO retryPublished = mapper.selectByHash(projectId, assetType, assetId, hash);
                if (retryPublished != null) {
                    return retryPublished;
                }
                throw secondCollision;
            }
        }
    }

    public void relate(String projectId, String relationType,
                       String sourceType, String sourceId, String sourceVersionId,
                       String targetType, String targetId, String targetVersionId,
                       String metadata, String userId) {
        mapper.insertRelation(IDGenerator.nextStr(), projectId, relationType, sourceType, sourceId,
                sourceVersionId, targetType, targetId, targetVersionId, metadata, userId, System.currentTimeMillis());
    }

    public TestAssetVersionDTO getPublished(String id,String projectId,String assetType,String assetId){
        TestAssetVersionDTO version=mapper.selectVersionById(id);
        if(version==null||!projectId.equals(version.getProjectId())||!assetType.equals(version.getAssetType())
                ||!assetId.equals(version.getAssetId())||!"PUBLISHED".equals(version.getStatus()))throw new MSException("PUBLISHED_ASSET_VERSION_NOT_FOUND");
        return version;
    }
    public TestAssetVersionDTO latestPublished(String projectId,String assetType,String assetId){TestAssetVersionDTO v=mapper.selectLatestPublished(projectId,assetType,assetId);if(v==null)throw new MSException("PUBLISHED_ASSET_VERSION_NOT_FOUND");return v;}

    public List<TestAssetVersionDTO> freezeVersions(String projectId,List<TestAssetRefDTO> refs){
        if(refs==null||refs.isEmpty())return List.of();
        return refs.stream().map(ref->StringUtils.isNotBlank(ref.getVersionId())
                ?getPublished(ref.getVersionId(),projectId,ref.getAssetType(),ref.getAssetId())
                :latestPublished(projectId,ref.getAssetType(),ref.getAssetId())).toList();
    }

    public TestAssetVersionDTO deprecate(String id,String projectId,String assetType,String assetId){
        TestAssetVersionDTO version=getPublished(id,projectId,assetType,assetId);
        if(mapper.deprecateVersion(id,projectId,assetType,assetId)!=1)throw new MSException("ASSET_VERSION_DEPRECATE_CONFLICT");
        version.setStatus("DEPRECATED");
        return version;
    }
    public TestAssetVersionDTO getFrozen(String id,String projectId,String assetType,String assetId){
        TestAssetVersionDTO version=mapper.selectVersionById(id);
        if(version==null||!projectId.equals(version.getProjectId())||!assetType.equals(version.getAssetType())
                ||!assetId.equals(version.getAssetId())||!List.of("PUBLISHED","DEPRECATED").contains(version.getStatus()))throw new MSException("FROZEN_ASSET_VERSION_NOT_FOUND");
        return version;
    }
}
