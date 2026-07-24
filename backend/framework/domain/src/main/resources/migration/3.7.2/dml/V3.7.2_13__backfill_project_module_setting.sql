-- 回填历史空 module_setting，避免工作台 getLayout 因 null 触发 NPE
UPDATE project
SET module_setting = '["bugManagement","caseManagement","apiTest","testPlan"]'
WHERE deleted = 0
  AND (module_setting IS NULL OR module_setting = '' OR module_setting = 'null');
