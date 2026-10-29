# 功能用例正文诊断、恢复与回滚

此工具仅供拥有数据库管理权限的运维人员离线运行，不提供公开HTTP入口，也不替代用户/项目权限接口。未获得可信原始内容时只能诊断，不生成猜测步骤。当前故障记录仍未执行恢复。

## 前提

- 先部署包含 FunctionalCaseBodyRepairTool、FunctionalCaseBlobCodec 及 outbox 表的新版本；不要在旧包中寻找新类。
- apply要求Linux/POSIX文件系统，备份目录700、文件600；Windows不支持POSIX时拒绝写入，不能降级为公开备份。
- 备份目录必须位于私有、持久化、受控磁盘，排除普通日志采集和公开静态目录。工具不打印原正文、原BLOB或数据库密码。
- 有原始导入文件/版本/备份，并完成用例ID、名称、项目、步骤及预期的核对。replacement文件必须是已确认的逻辑UTF-8正文，不是ZIP或数据库HEX。STEP字段需要合法步骤数组，默认拒绝空内容和空数组。
- 修复命令每次仅修改一个明确用例的一个白名单字段；不全库自动修复，不修改执行快照/历史执行事实。

## 只读诊断

```bash
python3 scripts/maintenance/diagnose-functional-case-body.py exported-steps.hex
```

退出0表示格式检查通过，退出2表示NEEDS_SOURCE；输出只有长度/摘要/层数/分类，不包含正文。五正文列可分别导出HEX；非steps字段加`--text`。

在正确的MSP数据库查询目标（读取原始BLOB，不经过MyBatis解压）：

```sql
SELECT c.id,c.project_id,c.case_edit_type,c.update_time,c.deleted,c.latest,
       b.steps IS NULL AS steps_is_null,
       SHA2(COALESCE(b.steps,X''),256) AS before_hash,
       OCTET_LENGTH(b.steps) AS before_bytes
FROM functional_case c JOIN functional_case_blob b ON b.id=c.id
WHERE c.id='1712038715113627712';
```

工具仅允许非删除且latest=1的目标。其他历史版本需要单独核对处理，不强行修改。

## 预览恢复

以下命令在新版本运行容器内部执行；`/app:/app/lib/*`对应仓库Dockerfile.backend的类与依赖布局。若是独立JAR部署，应使用同一构建产物解包后的classes/lib实际位置，不能混用旧依赖。

使用Secret注入MS_REPAIR_JDBC_URL、MS_REPAIR_DB_USER、MS_REPAIR_DB_PASSWORD；密码不要放进命令参数、日志或仓库。URL必须指向已核对的MySQL数据库。

```bash
java -Xmx256m -cp '/app:/app/lib/*'   io.metersphere.functional.service.FunctionalCaseBodyRepairTool   --case-id=CASE_ID --project-id=PROJECT_ID --field=steps   --actor-id=ACTIVE_OPERATOR_ID   --expected-hash=BEFORE_SHA256 --expected-revision=CURRENT_UPDATE_TIME   --replacement-file=/private/verified-steps.json   --source-reference=VERIFIED_SOURCE_FILE_SHA256_AND_ROW   --backup-directory=/private/persistent-case-body-backups
```

默认DRY_RUN：验证目标、来源内容和CAS条件，不创建备份、不更新数据库、不投递事件。输出beforeHash、afterHash、logicalHash和revision。任何case/project/field/hash/revision不匹配都停止。

支持字段：steps、text_description、expected_result、prerequisite、description；字段通过白名单选择，不允许任意SQL标识。

## 实际恢复

核对预览后，保持同一文件与参数，增加`--replacement-hash=预览返回的logicalHash --apply=true`。若来源文件在预览后被修改或没有确认摘要，拒绝写入。

工具顺序：锁定目标→核对活跃操作人及项目/版本/原摘要→验证逻辑正文→生成确定性的单层ZIP→写私有原始备份并fsync→更新单列BLOB及单调版本→事务内插入CASE_SYNC outbox→提交。

成功返回APPLIED。再次使用相同命令时，目标已等于期望存储内容则ALREADY_MATCHES，不重复更新版本、不新增事件或备份。这个状态只说明当前数据匹配，不声称一定是上次命令写入。

若outbox写入失败，正文和版本一起回滚，PREPARED备份保留；相同参数可安全重试。备份内容不一致、权限不安全或文件不完整则停止人工核对，不覆盖旧备份。提交阶段网络异常可能结果不确定，工具不宣称零写入，应以相同参数核对当前状态。

成功后检查：Web详情、MCP单条/带步骤检索、导出，以及plan/asset同步。源用例恢复不等于所有镜像立即收敛，失败事件应在平台查看并处理；不能以CLI退出0直接签收整项需求。

## 回滚

仅在明确需要撤销该次恢复时使用。回滚会精确恢复备份中的原始BLOB（可能重新变成损坏数据），不是再次尝试解压修复。先暂停相关业务写入及同步消费，并核对后续编辑；工具自身不会停服务。

先重新查询当前update_time和SHA256，要求当前字段仍等于原备份afterHash。使用原备份文件，替换replacement/source参数：

```bash
java -Xmx256m -cp '/app:/app/lib/*'   io.metersphere.functional.service.FunctionalCaseBodyRepairTool   --case-id=CASE_ID --project-id=PROJECT_ID --field=steps   --actor-id=ACTIVE_OPERATOR_ID   --expected-hash=ORIGINAL_BACKUP_AFTER_HASH --expected-revision=CURRENT_UPDATE_TIME   --rollback-backup=/private/persistent-case-body-backups/ORIGINAL_BACKUP.json   --backup-directory=/private/persistent-case-body-backups
```

先DRY_RUN；核对后加`--apply=true`。只接受私有文件，验证备份目标和原始字节摘要；回滚前也备份当前值，单调推进版本并发同步事件。不能覆盖已变为其他内容的字段；重复回滚不重复副作用。原内容损坏时同步事件可能失败，必须保持明确失败状态而不是伪装最新。

## 验证

- FunctionalCaseBlobCodecTests：规范化字节、格式/CRC/层数/大小及真实故障HEX。
- FunctionalCaseBodyRepairMySqlTests：真实MySQL dry-run、冲突、私有备份要求、恢复/回滚/重试幂等、outbox失败回滚。
- Linux CI实际执行apply和rollback；本机Windows验证拒绝不安全备份，崩溃恢复分支因不支持POSIX会明确跳过，不算Linux验证成功。

工具备份与result字段是运维证据，不把正文放进普通测试日志。保留备份到实际页面、MCP及下游收敛验收完成后，再按组织保留策略归档。
