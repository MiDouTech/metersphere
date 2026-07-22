-- handle_user 扩至 VARCHAR(1000) 时，整列索引在 utf8mb4 下会触发 Error 1071（索引键超长）
-- 先删 idx_assign_user，改列后再建前缀索引（191*4=764 < 3072）
ALTER TABLE bug DROP INDEX idx_assign_user;
ALTER TABLE bug MODIFY COLUMN handle_user VARCHAR(1000) NOT NULL COMMENT '处理人(多个以逗号分隔)';
CREATE INDEX idx_assign_user ON bug (handle_user(191));
