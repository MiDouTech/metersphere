package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentBugDTO;
import io.metersphere.agent.security.AgentTokenScopeParser;
import io.metersphere.agent.tool.AgentMcpImageResult;
import io.metersphere.bug.domain.*;
import io.metersphere.bug.mapper.*;
import io.metersphere.project.domain.*;
import io.metersphere.project.mapper.*;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Read-only current-state protocol. No transaction snapshot or generic response cache may hide revocation. */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AgentBugImageService {
    private final AgentBusinessAccess access;
    private final BugMapper bugs;
    private final BugContentMapper contents;
    private final BugLocalAttachmentMapper localFiles;
    private final FileAssociationMapper associations;
    private final FileMetadataMapper metadata;
    private final AgentBugImageStore store;
    private final org.mybatis.spring.SqlSessionTemplate sessions;
    private final AgentBugImagePermission permissions;
    private final AgentBugImageFinalCheck finalCheck;
    @org.springframework.beans.factory.annotation.Value("${agent.bug-images.enabled:true}")
    private boolean enabled = true;

    public AgentBugImageService(AgentBusinessAccess access, BugMapper bugs, BugContentMapper contents,
                               BugLocalAttachmentMapper localFiles, FileAssociationMapper associations,
                               FileMetadataMapper metadata, AgentBugImageStore store, org.mybatis.spring.SqlSessionTemplate sessions,
                               AgentBugImagePermission permissions, AgentBugImageFinalCheck finalCheck) {
        this.access = access; this.bugs = bugs; this.contents = contents; this.localFiles = localFiles;
        this.associations = associations; this.metadata = metadata; this.store = store;
        this.sessions = sessions;
        this.permissions = permissions;
        this.finalCheck = finalCheck;
    }

    record Resource(String fileId, String association, String version, String name, Long size,
                    Long created, String source, FileRequest request, String issue) { }
    record State(String description, List<Resource> resources, boolean complete) { }
    record Selection(Resource resource, String reason) { }
    record Indexed(String reference, String reason, boolean retryable) { }

    public void authorize(String project, String bug) {
        sessions.clearCache();
        var token = access.activeToken(AgentTokenScope.BUG_READ);
        // BUG_WRITE's historical implication must not grant the new image/index capability.
        if (!AgentTokenScopeParser.parse(token.getScopes()).contains(AgentTokenScope.BUG_READ)) throw AgentBusinessAccess.denied();
        if (!io.metersphere.agent.security.AgentTokenProjectAccess.allows(token, project)) throw AgentBusinessAccess.denied();
        permissions.require(token.getUserId(), project);
        Bug current = bugs.selectByPrimaryKey(bug);
        if (current == null || Boolean.TRUE.equals(current.getDeleted()) || !project.equals(current.getProjectId()))
            throw AgentBusinessAccess.denied();
    }

    public void addIndex(AgentBugDTO detail) {
        if (!enabled) return;
        String project = detail.getProjectId(), bug = detail.getId();
        authorize(project, bug);
        State state = state(project, bug);
        // Keep the existing detail field exactly as returned, including null for platform templates.
        // A concurrent edit must not make the index describe a different text snapshot.
        var parsed = BugImageDescription.parse(detail.getDescription(), project);
        Set<String> currentBodyFiles = BugImageDescription.parse(state.description(), project).images().stream()
                .filter(image -> image.reason() == null).map(BugImageDescription.Image::fileId).collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> images = new ArrayList<>(), attachments = new ArrayList<>();
        for (var image : parsed.images()) {
            Resource resource = currentBodyFiles.contains(image.fileId()) ? descriptionResource(state, image.fileId()) : null;
            String reason = image.reason() != null ? image.reason() : resource == null ? "RESOURCE_UNAVAILABLE" : knownReason(resource);
            Indexed indexed = reason != null && resource == null || image.reason() != null
                    ? new Indexed(placeholder(project, bug, image), reason, false) : indexedReference(project, bug, "DESCRIPTION", resource);
            Map<String, Object> entry = indexedEntry(indexed, "DESCRIPTION", reason);
            entry.put("occurrence", image.occurrence()); entry.put("alt", image.alt()); images.add(entry);
        }
        boolean attachmentComplete = state.complete();
        for (Resource resource : state.resources()) {
            if (!"ATTACHMENT".equals(resource.source())) continue;
            boolean image = Objects.toString(resource.name(), "").toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|gif|webp|bmp|tiff?|avif|apng)$");
            try { image = image || supportedHeader(store.head(resource.request())); }
            catch (Exception failure) { if (!image) attachmentComplete = false; }
            if (!image) continue;
            Map<String, Object> entry = indexedEntry(indexedReference(project, bug, "ATTACHMENT", resource), "ATTACHMENT", knownReason(resource));
            entry.put("associationRef", resource.association()); entry.put("fileName", resource.name()); attachments.add(entry);
        }
        detail.setImages(images); detail.setImagesComplete(parsed.complete());
        detail.setAttachmentImages(attachments); detail.setAttachmentImagesComplete(attachmentComplete);
        List<String> warnings = new ArrayList<>();
        if (!parsed.complete()) warnings.add("DESCRIPTION_INDEX_INCOMPLETE");
        if (!attachmentComplete) warnings.add("ATTACHMENT_INDEX_INCOMPLETE");
        detail.setImageWarnings(warnings);
        authorize(project, bug);
    }

    public AgentMcpImageResult read(String project, String bug, String ref) {
        validateId(project); validateId(bug);
        authorize(project, bug);
        if (!enabled) throw BugImageValidator.failure("UNSUPPORTED");
        if (ref == null || !ref.matches("bi1_[a-f0-9]{64}_[a-f0-9]{64}")) throw BugImageValidator.failure("INVALID_ARGUMENT");
        Selection selected = select(state(project, bug), project, bug, ref);
        if (selected.reason() != null) throw BugImageValidator.failure(selected.reason());
        Resource resource = selected.resource();
        if (ref.endsWith("_" + unboundVersion("STORAGE_UNAVAILABLE")))
            throw new MSException("BUG_IMAGE_INDEX_STORAGE_UNAVAILABLE");
        if (ref.endsWith("_" + unboundVersion("RESOURCE_UNAVAILABLE")))
            throw BugImageValidator.failure("RESOURCE_UNAVAILABLE");
        String revision;
        byte[] bytes;
        String mime;
        try {
            revision = store.revision(resource.request());
            if (!ref.endsWith("_" + DigestUtils.sha256Hex(revision)))
                throw BugImageValidator.failure("REFERENCE_EXPIRED");
            bytes = store.read(resource.request());
            mime = BugImageValidator.validate(bytes);
        } catch (Exception failure) {
            // A revocation or replacement takes precedence over detailed file diagnostics.
            finalSelection(project, bug, ref, resource);
            if (failure instanceof MSException business) throw business;
            if (failure instanceof java.io.FileNotFoundException || missingObject(failure))
                throw BugImageValidator.failure("RESOURCE_UNAVAILABLE");
            throw BugImageValidator.failure("STORAGE_UNAVAILABLE");
        }
        String hash = DigestUtils.sha256Hex(bytes);
        var result = new AgentMcpImageResult(Base64.getEncoder().encodeToString(bytes), mime,
                Map.of("imageRef", ref, "bugId", bug, "mimeType", mime, "sizeBytes", bytes.length, "sha256", hash));
        finalCheck.run(project, bug, resource.fileId(), () -> {
            authorize(project, bug);
            Selection current = select(state(project, bug), project, bug, ref);
            if (current.resource() == null || current.reason() != null || !resource.version().equals(current.resource().version()))
                throw BugImageValidator.failure("REFERENCE_EXPIRED");
            try {
                // This stat, of the newly selected request, is the linearization point.
                // The database rows remain locked until the callback and transaction finish.
                if (!revision.equals(store.revision(current.resource().request()))) throw BugImageValidator.failure("REFERENCE_EXPIRED");
            } catch (MSException failure) { throw failure; }
            catch (Exception failure) {
                if (failure instanceof java.io.FileNotFoundException || missingObject(failure)) throw BugImageValidator.failure("RESOURCE_UNAVAILABLE");
                throw BugImageValidator.failure("STORAGE_UNAVAILABLE");
            }
            // Locked grants cannot change here. Expiry is time-based: validating it AFTER
            // the stat also proves the token was unexpired at the earlier decision point.
            access.activeToken(AgentTokenScope.BUG_READ);
        });
        return result;
    }

    private void finalSelection(String project, String bug, String ref, Resource previous) {
        authorize(project, bug);
        Selection current = select(state(project, bug), project, bug, ref);
        if (current.resource() == null || current.reason() != null || !previous.version().equals(current.resource().version()))
            throw BugImageValidator.failure("REFERENCE_EXPIRED");
        authorize(project, bug);
    }

    private Selection select(State state, String project, String bug, String ref) {
        for (var image : BugImageDescription.parse(state.description(), project).images()) {
            Resource resource = descriptionResource(state, image.fileId());
            if (image.reason() != null || resource == null) {
                if (placeholder(project, bug, image).equals(ref)) return new Selection(null,
                        image.reason() == null ? "RESOURCE_UNAVAILABLE" : image.reason());
            } else if (ref.startsWith(reference(project, bug, "DESCRIPTION", resource) + "_")) return new Selection(resource, resource.issue());
        }
        for (Resource resource : state.resources())
            if ("ATTACHMENT".equals(resource.source()) && ref.startsWith(reference(project, bug, "ATTACHMENT", resource) + "_"))
                return new Selection(resource, resource.issue());
        throw BugImageValidator.failure("REFERENCE_EXPIRED");
    }

    private State state(String project, String bug) {
        // A NOT_SUPPORTED scope can still have Spring synchronization and a MyBatis local cache.
        // Never let that cache turn final authorization/source checks into an earlier snapshot.
        sessions.clearCache();
        BugContent content = contents.selectByPrimaryKey(bug);
        List<Resource> result = new ArrayList<>();
        boolean complete = true;
        var localQuery = new BugLocalAttachmentExample(); localQuery.createCriteria().andBugIdEqualTo(bug);
        for (var file : localFiles.selectByExample(localQuery)) {
            if (!"ATTACHMENT".equals(file.getSource()) && !"RICH_TEXT".equals(file.getSource())) { complete = false; continue; }
            if (file.getFileId() == null || file.getId() == null || file.getFileName() == null) { complete = false; continue; }
            result.add(new Resource(file.getFileId(), file.getId(), fingerprint(file.getFileId(), file.getFileName(), file.getSize()),
                    file.getFileName(), file.getSize(), file.getCreateTime(), file.getSource(), store.local(project, bug, file), null));
        }
        var query = new FileAssociationExample();
        query.createCriteria().andSourceIdEqualTo(bug).andSourceTypeEqualTo("BUG").andDeletedEqualTo(false);
        for (var association : associations.selectByExample(query)) {
            FileMetadata file = metadata.selectByPrimaryKey(association.getFileId());
            if (file == null || !project.equals(file.getProjectId())) { complete = false; continue; }
            String version = fingerprint(file.getId(), file.getFileVersion(), file.getUpdateTime(), file.getSize());
            FileRequest request = null; String issue = null;
            try { request = store.managed(file); }
            catch (RuntimeException unavailable) { issue = "RESOURCE_UNAVAILABLE"; complete = false; }
            result.add(new Resource(file.getId(), association.getId(), version, file.getName() + "." + file.getType(),
                    file.getSize(), association.getCreateTime(), "ATTACHMENT", request, issue));
        }
        result.sort(Comparator.comparing(Resource::created, Comparator.nullsFirst(Long::compareTo)).thenComparing(Resource::association));
        return new State(content == null ? "" : content.getDescription(), List.copyOf(result), complete);
    }

    private static Resource descriptionResource(State state, String fileId) {
        // Each local file ID is an immutable uploaded resource, not a URL or a caller-selected path.
        return state.resources().stream().filter(r -> r.fileId().equals(fileId)).findFirst().orElse(null);
    }
    private static String knownReason(Resource resource) {
        if (resource.issue() != null) return resource.issue();
        if (resource.size() != null && resource.size() == 0) return "EMPTY_FILE";
        if (resource.size() != null && resource.size() > BugImageValidator.MAX_BYTES) return "SIZE_LIMIT";
        return null;
    }
    private static Map<String, Object> entry(String ref, String origin, String reason) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("imageRef", ref); entry.put("origin", origin); entry.put("readable", reason == null);
        if (reason != null) entry.put("reason", reason);
        return entry;
    }
    private static String reference(String project, String bug, String origin, Resource resource) {
        return "bi1_" + fingerprint(project, bug, origin, resource.fileId(), resource.version(),
                "ATTACHMENT".equals(origin) ? resource.association() : "");
    }
    private static String placeholder(String project, String bug, BugImageDescription.Image image) {
        return "bi1_" + fingerprint(project, bug, "UNREADABLE", image.occurrence(), image.fileId(), image.reason()) + "_" + "0".repeat(64);
    }
    private Indexed indexedReference(String project, String bug, String origin, Resource resource) {
        String base = reference(project, bug, origin, resource) + "_";
        if (resource.issue() != null) return new Indexed(base + unboundVersion("RESOURCE_UNAVAILABLE"), resource.issue(), false);
        try { return new Indexed(base + DigestUtils.sha256Hex(store.revision(resource.request())), null, false); }
        catch (Exception unavailable) {
            String reason = unavailable instanceof java.io.FileNotFoundException || missingObject(unavailable)
                    ? "RESOURCE_UNAVAILABLE" : "STORAGE_UNAVAILABLE";
            return new Indexed(base + unboundVersion(reason), reason, "STORAGE_UNAVAILABLE".equals(reason));
        }
    }
    private static String unboundVersion(String reason) { return fingerprint("INDEX_WITHOUT_STORAGE_VERSION", reason); }
    private static Map<String, Object> indexedEntry(Indexed indexed, String origin, String knownReason) {
        Map<String, Object> result = entry(indexed.reference(), origin, indexed.reason() == null ? knownReason : indexed.reason());
        if (indexed.reason() != null) {
            result.put("retryable", indexed.retryable()); result.put("nextAction", "REFRESH_BUG");
        }
        return result;
    }
    private static String fingerprint(Object... values) { return DigestUtils.sha256Hex(JSON.toJSONString(values)); }
    private static void validateId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) throw BugImageValidator.failure("INVALID_ARGUMENT");
    }
    private static boolean supportedHeader(byte[] bytes) {
        return bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                || bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8;
    }
    private static boolean missingObject(Exception failure) {
        return failure instanceof java.nio.file.NoSuchFileException || failure instanceof java.io.FileNotFoundException
                || failure instanceof io.minio.errors.ErrorResponseException error && Set.of("NoSuchKey", "NoSuchVersion", "NoSuchObject").contains(error.errorResponse().code());
    }
}
