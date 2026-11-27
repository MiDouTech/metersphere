package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import java.util.*;
import java.util.function.Supplier;

/** Shared HTTP/native allowlist. Unknown exception text is never a user-facing message. */
public final class DataAssetErrors {
  public record Safe(String code, String message, int status) {}

  private static final Map<String, Safe> KNOWN;

  static {
    var values = new HashMap<String, Safe>();
    register(
        values,
        "DATA_ASSET_VALIDATION_ERROR",
        400,
        "CSV引号未闭合",
        "CSV数据需要完整的二维行数组",
        "CSV超过10000行或16384列限制",
        "CSV超过16384列限制",
        "上传需要文件名、幂等标识及Base64内容",
        "下载偏移超过文件长度",
        "不支持的文件格式",
        "分页参数无效",
        "分页范围无效，请缩小查询范围",
        "分页预览不能直接保存，请加载完整内容后编辑",
        "删除需要当前版本号",
        "单次迁移最多50个版本且总量不超过20MB",
        "名称不能超过255个字符",
        "名称不能超过255字符",
        "名称或数据格式无效",
        "工作簿数据需要包含名称和行数据的完整工作表",
        "工作表名称或行列数据无效",
        "工作表超过10000行限制",
        "批次内容无效",
        "批次资产及版本参数无效",
        "数据内容无效或超过2MB限制",
        "数据版本元信息无效",
        "文件实际格式与扩展名不一致",
        "文件编码无效",
        "文件解析失败，请检查格式及UTF-8编码",
        "文件超过2MB限制",
        "文本格式的数据内容必须是文本",
        "日期单元格格式无效，请使用ISO日期或日期时间",
        "替换文件必须保持原数据格式",
        "替换文件需要幂等请求标识",
        "替换文件需要当前版本号",
        "未知操作",
        "来源实际总量超过20MB限制",
        "来源文件超过2MB限制",
        "查询范围过大，请增加筛选条件后重试",
        "每批需要1至100项数据",
        "测试数据工具参数无效",
        "外层与数据内的版本号或请求标识不一致",
        "编辑不能改变原数据格式",
        "解析后内容超过2MB限制",
        "说明不能超过2000个字符",
        "请将错误单元格转换为值后上传",
        "请提供完整数据参数",
        "请提供完整的数据内容",
        "请求标识过长",
        "请选择数据资产",
        "请选择有效的资产类型",
        "资产已删除或无访问权限，不能重试解析",
        "预览分页参数无效");
    register(
        values,
        "DATA_ASSET_STATE_CONFLICT",
        409,
        "仅失败或中断的批次可重试",
        "仅失败的解析任务可重试",
        "任务状态已变化，请刷新",
        "批次已中断，请刷新结果",
        "批次正在重试，请刷新",
        "来源版本已映射多个资产，需要先处理映射冲突",
        "资产标识对应多个来源，请明确来源项目后重试");
    register(
        values,
        "DATA_ASSET_NOT_FOUND",
        404,
        "分类不存在或无访问权限",
        "工作表不存在",
        "批次不存在或无访问权限",
        "数据版本不存在",
        "数据资产不存在或无访问权限",
        "来源文件不存在或无访问权限",
        "解析任务不存在或无访问权限",
        "资产不存在或无访问权限",
        "资产版本不存在或无访问权限");
    register(
        values,
        "DATA_ASSET_STORAGE_UNAVAILABLE",
        503,
        "原始文件暂时无法下载",
        "原文件存储失败，请稍后重试",
        "文件存储失败，请稍后重试",
        "来源文件暂时无法导入");
    register(
        values,
        "DATA_ASSET_READ_FAILED",
        500,
        "批次暂时无法重试",
        "批次结果暂时无法读取",
        "数据内容暂时无法读取",
        "数据版本元信息暂时无法读取");
    register(values, "DATA_ASSET_VERSION_CONFLICT", 409, "数据已更新，请刷新后重试");
    register(values, "DATA_ASSET_IN_USE", 409, "数据正在被运行任务使用，请等待任务结束", "数据正在被运行任务引用，请等待任务结束");
    register(values, "DATA_ASSET_PROCESSING", 409, "数据解析完成后才能编辑", "请等待当前解析完成后再替换");
    register(values, "DATA_ASSET_FILE_NOT_FOUND", 404, "该版本没有原始文件");
    register(values, "DATA_ASSET_IDEMPOTENCY_CONFLICT", 409, "请求标识已用于不同内容");
    register(values, "DATA_ASSET_PROCESSING", 409, "数据正在解析，请稍后刷新", "解析失败，请查看任务并重试");
    register(
        values,
        "DATA_ASSET_PERMISSION_DENIED",
        403,
        "AGENT_RESOURCE_NOT_ACCESSIBLE",
        "PERMISSION_DENIED",
        "RBAC_DENIED");
    register(values, "ASSET_CATEGORY_VALIDATION_ERROR", 400, "不能迁移到待删除分类或其子分类");
    register(values, "ASSET_CATEGORY_CONFLICT", 409, "仅来源不明资产允许治理，已确认来源不可再次修改");
    register(values, "ASSET_CATEGORY_VALIDATION_ERROR", 400, "分类不能移动到自身或其子分类下");
    register(values, "ASSET_CATEGORY_CONFLICT", 409, "分类包含资产或子分类，请选择迁移或转为未分类");
    register(values, "ASSET_CATEGORY_VALIDATION_ERROR", 400, "分类名称长度必须为 1～100 个字符");
    register(values, "ASSET_CATEGORY_VALIDATION_ERROR", 400, "只能调整同一父分类下的顺序");
    register(values, "ASSET_CATEGORY_CONFLICT", 409, "同一父分类下名称不能重复");
    register(values, "ASSET_CATEGORY_VALIDATION_ERROR", 400, "移动后分类层级将超过 5 层");
    register(values, "ASSET_CATEGORY_VALIDATION_ERROR", 400, "请先选择组织");
    register(values, "ASSET_CATEGORY_VALIDATION_ERROR", 400, "请选择有证据的确定来源");
    register(values, "ASSET_CATEGORY_VALIDATION_ERROR", 400, "请选择迁移目标分类");
    register(values, "ASSET_CATEGORY_NOT_FOUND", 404, "资产分类不存在或已被删除");
    register(values, "ASSET_CATEGORY_VALIDATION_ERROR", 400, "资产分类最多支持 5 层");
    register(values, "ASSET_CATEGORY_NOT_FOUND", 404, "项目不存在");
    register(values, "ASSET_CATEGORY_NOT_FOUND", 404, "项目不存在或不属于当前组织");
    register(values, "ASSET_VALIDATION_ERROR", 400, "不支持的测试资产类型");
    register(values, "ASSET_VALIDATION_ERROR", 400, "单个任务最多引用 50 个扩展测试资产");
    register(values, "ASSET_VALIDATION_ERROR", 400, "缺少测试资产读取权限");
    register(values, "ASSET_NOT_FOUND", 404, "证据不存在或已超过保留期限");
    register(values, "DATA_ASSET_VALIDATION_ERROR", 400,
        "文件超过30MB限制", "解析后内容超过30MB限制", "数据内容无效或超过30MB限制",
        "来源文件超过30MB限制", "工作表超过100000行限制", "CSV超过100000行或16384列限制",
        "单次迁移最多50个版本且总量不超过150MB", "来源实际总量超过150MB限制",
        "数据记录超过100000行限制", "记录分页参数无效", "请选择1至100000条记录", "记录不存在或不属于该版本", "操作请求标识无效",
        "数据内容无效或展开后超过63MB限制", "解析后内容超过63MB限制", "复制内容超过30MB限制", "复制内容超出安全容量，请减少选择条目", "数据内容无效");
    register(values, "ASSET_CATEGORY_VALIDATION_ERROR", 400,
        "默认分类名称保留，不能创建或重命名", "默认分类不可修改、删除或作为父分类");
    register(values, "DATA_ASSET_READ_FAILED", 500, "记录暂时无法读取");
    register(values, "DATA_ASSET_IDEMPOTENCY_CONFLICT", 409, "操作请求标识已用于其他内容");
    KNOWN = Map.copyOf(values);
  }

  private static final Set<String> LEGACY_CODES =
      Set.of(
          "AGENT_RESOURCE_NOT_ACCESSIBLE",
          "AGENT_TASK_LEASE_TOKEN_REQUIRED",
          "AGENT_VALIDATION_ERROR",
          "IDEMPOTENCY_CONFLICT",
          "IDEMPOTENCY_KEY_REQUIRED",
          "IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED",
          "ARTIFACT_CASE_MISMATCH",
          "ARTIFACT_COMMIT_CONFLICT",
          "ARTIFACT_COMMIT_REQUEST_INVALID",
          "ARTIFACT_CONTENT_TYPE_MISMATCH",
          "ARTIFACT_HASH_INVALID",
          "ARTIFACT_HASH_MISMATCH",
          "ARTIFACT_NOT_FOUND",
          "ARTIFACT_NOT_FOUND_OR_NOT_ACCESSIBLE",
          "ARTIFACT_PREPARE_CONFLICT",
          "ARTIFACT_PREPARE_REQUEST_INVALID",
          "ARTIFACT_REDACTION_REQUIRED",
          "ARTIFACT_SIZE_INVALID",
          "ARTIFACT_STEP_MISMATCH",
          "ARTIFACT_TYPE_NOT_ALLOWED",
          "ASSET_NOT_FOUND",
          "ASSET_PUBLICATION_ACTOR_REQUIRED",
          "ASSET_TYPES_REQUIRED",
          "ASSET_VERSION_DEPRECATE_CONFLICT",
          "ASSET_VERSION_NOT_FOUND",
          "CASE_CONTENT_DECODE_FAILED",
          "CASE_CONTENT_FORMAT_INVALID",
          "CASE_CONTENT_HASH_INVALID",
          "CASE_CONTENT_LIMIT_EXCEEDED",
          "CASE_CONTENT_MISSING",
          "CASE_VERSION_NOT_PUBLISHED",
          "FROZEN_ASSET_VERSION_NOT_FOUND",
          "INVALID_TRUSTED_ASSET_SOURCE",
          "PROJECT_IDENTITY_AMBIGUOUS",
          "PROJECT_IDENTITY_REQUIRED",
          "PUBLISHED_ASSET_VERSION_NOT_FOUND",
          "RUNNER_LEASE_TASK_MISMATCH",
          "TASK_NOT_FOUND_OR_NOT_ACCESSIBLE");

  private DataAssetErrors() {}

  private static void register(Map<String, Safe> map, String code, int status, String... messages) {
    for (String message : messages) map.put(message, new Safe(code, message, status));
  }

  public static Safe map(Throwable failure) {
    if (failure instanceof DataAssetFailure safe)
      return new Safe(safe.code(), safe.getMessage(), safe.httpStatus());
    if (failure instanceof BusinessGovernanceException safe) {
      int status =
          safe.code().endsWith("CONFLICT")
              ? 409
              : safe.code().endsWith("NOT_ACCESSIBLE")
                  ? 403
                  : safe.code().equals("CONFIGURATION_KEY_UNAVAILABLE") ? 503 : 400;
      return new Safe(safe.code(), safe.getMessage(), status);
    }
    if (failure instanceof org.apache.shiro.authz.AuthorizationException) return denied();
    if (failure instanceof MSException) {
      if (((MSException) failure).getErrorCode()
          == io.metersphere.system.controller.handler.result.CommonResultCode.FEATURE_DISABLED)
        return new Safe("FEATURE_DISABLED", "当前功能未启用，请联系管理员", 403);
      var known = KNOWN.get(failure.getMessage());
      if (known != null)
        return known.code().equals("DATA_ASSET_PERMISSION_DENIED")
            ? new Safe(
                failure.getMessage(),
                new AgentSafeErrorMapper()
                    .toApiError(failure, UUID.randomUUID().toString())
                    .getMessage(),
                403)
            : known;
      if (LEGACY_CODES.contains(failure.getMessage())
          || ((MSException) failure).getErrorCode()
              instanceof io.metersphere.agent.constants.AgentErrorCode) {
        var mapped = new AgentSafeErrorMapper().toApiError(failure, UUID.randomUUID().toString());
        String code = mapped.getCode();
        return new Safe(code, mapped.getMessage(), legacyStatus(code));
      }
    }
    if (failure instanceof org.springframework.web.bind.MethodArgumentNotValidException
        || failure instanceof org.springframework.validation.BindException
        || failure instanceof org.springframework.web.bind.MissingServletRequestParameterException
        || failure
            instanceof org.springframework.web.multipart.support.MissingServletRequestPartException
        || failure instanceof org.springframework.http.converter.HttpMessageNotReadableException
        || failure
            instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
        || failure instanceof IllegalArgumentException
        || failure instanceof org.springframework.web.multipart.MaxUploadSizeExceededException)
      return new Safe("DATA_ASSET_VALIDATION_ERROR", "提交字段不符合要求，请检查必填项、长度和格式", 400);
    if (failure instanceof java.io.IOException)
      return new Safe("DATA_ASSET_STORAGE_UNAVAILABLE", "文件暂时无法读取，请稍后重试", 503);
    return new Safe("DATA_ASSET_INTERNAL_ERROR", "服务暂时不可用，请稍后重试并提供追踪编号", 500);
  }

  private static int legacyStatus(String code) {
    if (code.equals("AUTHENTICATION_REQUIRED") || code.endsWith("_TOKEN_REQUIRED")) return 401;
    if (code.equals("FEATURE_DISABLED")) return 403;
    if (code.contains("EXPIRED")) return 410;
    if (code.contains("RATE_LIMIT")
        || code.contains("QUOTA")
        || code.contains("BUDGET")
        || code.contains("INVOCATION_LIMIT")) return 429;
    if (code.startsWith("MAP_GATEWAY_") || code.startsWith("UPSTREAM_")) return 502;
    if (code.contains("FORBIDDEN")
        || code.contains("PERMISSION_DENIED")
        || code.endsWith("NOT_ACCESSIBLE")
        || Set.of("SCOPE_DENIED", "RBAC_DENIED", "PROJECT_NOT_ALLOWED").contains(code)) return 403;
    if (code.contains("NOT_FOUND") || code.endsWith("NOT_EXIST")) return 404;
    if (code.contains("CONFLICT") || code.startsWith("ALREADY_") || code.equals("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED")) return 409;
    if (code.contains("NOT_AVAILABLE") || code.contains("NOT_CONFIGURED")) return 503;
    return 400;
  }

  private static Safe denied() {
    return new Safe("DATA_ASSET_PERMISSION_DENIED", "无权访问此资产或执行该操作", 403);
  }

  public static Object nativeBoundary(Supplier<Object> action) {
    try {
      return action.get();
    } catch (RuntimeException failure) {
      var safe = map(failure);
      String trace = UUID.randomUUID().toString();
      io.metersphere.agent.security.AgentSafeDiagnostics.failure(
          org.slf4j.LoggerFactory.getLogger(DataAssetErrors.class),
          "DATA_ASSET_NATIVE_FAILURE",
          "native-tool",
          trace,
          safe.code(),
          failure);
      throw new DataAssetFailure(safe, trace, failure);
    }
  }
}
