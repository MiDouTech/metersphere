-- The generation tab was removed from the case management entry. Keep permission IDs
-- and existing grants for compatible API clients, but stop advertising its UI resource.
UPDATE permission_resource SET enabled=b'0' WHERE code='FUNCTIONAL_CASE_AI_GENERATE_TAB';
