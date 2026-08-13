package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.mapper.TestAssetMapper;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        mapper.insertVersion(version);
        return version;
    }

    public void relate(String projectId, String relationType,
                       String sourceType, String sourceId, String sourceVersionId,
                       String targetType, String targetId, String targetVersionId,
                       String metadata, String userId) {
        mapper.insertRelation(IDGenerator.nextStr(), projectId, relationType, sourceType, sourceId,
                sourceVersionId, targetType, targetId, targetVersionId, metadata, userId, System.currentTimeMillis());
    }
}
