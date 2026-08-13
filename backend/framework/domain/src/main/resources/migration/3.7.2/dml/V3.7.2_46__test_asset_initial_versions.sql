-- Idempotently register existing parsed business documents and latest functional cases
-- in the unified immutable asset catalog. Stable case identity uses functional_case.ref_id.

INSERT IGNORE INTO test_asset_version
    (id, project_id, asset_type, asset_id, version_no, source_version, content_hash,
     content_snapshot, status, created_by, created_at, published_by, published_at)
SELECT SHA2(CONCAT('test-asset-document:', d.id, ':1'), 256),
       d.project_id,
       'DOCUMENT',
       d.id,
       1,
       d.sha256,
       SHA2(CONCAT_WS('|', d.sha256, d.parser_type, d.summary, d.section_index), 256),
       JSON_OBJECT(
           'id', d.id,
           'originalName', d.original_name,
           'mimeType', d.mime_type,
           'fileSize', d.file_size,
           'sha256', d.sha256,
           'parserType', d.parser_type,
           'summary', d.summary,
           'sections', d.section_index),
       'PUBLISHED',
       d.create_user,
       d.create_time,
       d.create_user,
       d.update_time
FROM ai_source_document d
WHERE d.deleted = 0
  AND d.parse_status = 'PARSED';

INSERT IGNORE INTO test_asset_version
    (id, project_id, asset_type, asset_id, version_no, source_version, content_hash,
     content_snapshot, status, created_by, created_at, published_by, published_at)
SELECT SHA2(CONCAT('test-asset-case:', fc.ref_id, ':', fc.version_id), 256),
       fc.project_id,
       'CASE',
       fc.ref_id,
       1,
       fc.version_id,
       SHA2(CONCAT_WS('|', fc.version_id, fc.name, fc.tags, fc.case_edit_type,
                     CONVERT(fb.prerequisite USING utf8mb4), CONVERT(fb.steps USING utf8mb4),
                     CONVERT(fb.text_description USING utf8mb4), CONVERT(fb.expected_result USING utf8mb4)), 256),
       JSON_OBJECT(
           'id', fc.id,
           'refId', fc.ref_id,
           'versionId', fc.version_id,
           'name', fc.name,
           'moduleId', fc.module_id,
           'tags', fc.tags,
           'caseEditType', fc.case_edit_type,
           'prerequisite', CONVERT(fb.prerequisite USING utf8mb4),
           'steps', CONVERT(fb.steps USING utf8mb4),
           'textDescription', CONVERT(fb.text_description USING utf8mb4),
           'expectedResult', CONVERT(fb.expected_result USING utf8mb4)),
       'PUBLISHED',
       fc.create_user,
       fc.create_time,
       COALESCE(fc.update_user, fc.create_user),
       fc.update_time
FROM functional_case fc
LEFT JOIN functional_case_blob fb ON fb.id = fc.id
WHERE fc.project_id IS NOT NULL
  AND fc.deleted = 0
  AND fc.latest = 1;

INSERT IGNORE INTO test_asset_relation
    (id, project_id, relation_type, source_asset_type, source_asset_id, source_version_id,
     target_asset_type, target_asset_id, target_version_id, metadata, created_by, created_at)
SELECT SHA2(CONCAT('test-asset-derived:', draft.id, ':', fc.ref_id), 256),
       draft.project_id,
       'DERIVED_FROM',
       'DOCUMENT',
       draft.source_document_id,
       document_version.id,
       'CASE',
       fc.ref_id,
       case_version.id,
       JSON_OBJECT('generationId', draft.generation_id, 'draftId', draft.id,
                   'sourceReferences', draft.source_references, 'migration', 'V3.7.2_46'),
       COALESCE(draft.reviewed_by, draft.create_user),
       COALESCE(draft.reviewed_at, draft.update_time)
FROM functional_case_ai_draft draft
JOIN functional_case fc ON fc.id = draft.formal_case_id
JOIN test_asset_version document_version
  ON document_version.project_id = draft.project_id
 AND document_version.asset_type = 'DOCUMENT'
 AND document_version.asset_id = draft.source_document_id
JOIN test_asset_version case_version
  ON case_version.project_id = draft.project_id
 AND case_version.asset_type = 'CASE'
 AND case_version.asset_id = fc.ref_id
WHERE draft.deleted = 0
  AND draft.formal_case_id IS NOT NULL
  AND draft.source_document_id IS NOT NULL;
