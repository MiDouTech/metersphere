import type { AxiosResponse } from 'axios';

export function formatPreflightBlockedMessage(preflight: { blockedDetail?: string; blockedReason?: string }) {
  const messages: Record<string, string> = {
    ENVIRONMENT_ASSET_BINDING_REQUIRED: '请在测试平台配置中绑定已发布环境资产，再运行受管任务；个人直连不受影响',
    ENVIRONMENT_ASSET_BINDING_CONFLICT: '环境资产引用与平台配置不一致，请重新选择环境并预检',
    PUBLISHED_ASSET_VERSION_NOT_FOUND: '执行所需的资产尚未发布或已失效，请核对发布版本',
    ENVIRONMENT_PROFILE_DISABLED: '测试平台配置已停用，请启用或选择其他配置',
    PRODUCTION_EXECUTION_FORBIDDEN: '不允许在生产环境执行此任务，请选择测试或预发布环境',
    MODEL_PROFILE_REQUIRED: '请选择可用的模型配置后重新预检',
    PROMPT_TEMPLATE_REQUIRED: '请选择已发布的提示词模板后重新预检',
    BLOCKED_ENVIRONMENT: '环境配置未通过检查，请核对平台地址、状态及资产绑定',
    BLOCKED_CREDENTIAL: '凭据或登录配置不可用，请检查角色、状态及环境范围',
    BLOCKED_SCOPE: '用例范围或发布资产未通过检查，请核对执行范围和权限',
    BLOCKED_MODEL: '模型或提示词配置未通过检查，请核对配置及使用额度',
  };
  return (
    messages[preflight.blockedDetail || ''] ||
    messages[preflight.blockedReason || ''] ||
    '预检未通过，请检查执行配置；可使用 traceId 查询详细原因'
  );
}

const SENSITIVE_ERROR_PATTERNS = [
  /java\.[\w.$]+(?:Exception|Error)/i,
  /org\.[\w.$]+(?:Exception|Error)/i,
  /(?:select|insert|update|delete)\s.+\s(?:from|into|set)\s/ims,
  /(?:mapper\.xml|mybatis|jdbc|sqlstate|stack trace)/i,
  /(?:caused by:|at\s+[\w.$]+\([^)]*\.java:\d+\))/i,
];

export interface AppErrorDetails {
  code?: string | number;
  status?: number;
  category?:
    | 'validation'
    | 'permission'
    | 'conflict'
    | 'not-found'
    | 'rate-limit'
    | 'network'
    | 'timeout'
    | 'server'
    | 'unknown';
  messageKey?: string;
  message: string;
  requestId?: string;
  retryable: boolean;
  fieldErrors?: Record<string, string>;
  context?: Record<string, unknown>;
}

export class AppError extends Error implements AppErrorDetails {
  code?: string | number;

  status?: number;

  category?: AppErrorDetails['category'];

  messageKey?: string;

  requestId?: string;

  retryable: boolean;

  fieldErrors?: Record<string, string>;

  context?: Record<string, unknown>;

  constructor(details: AppErrorDetails) {
    super(details.message);
    this.name = 'AppError';
    this.code = details.code;
    this.status = details.status;
    this.category = details.category;
    this.messageKey = details.messageKey;
    this.requestId = details.requestId;
    this.retryable = details.retryable;
    this.fieldErrors = details.fieldErrors;
    this.context = details.context;
  }
}

export function containsSensitiveErrorDetail(message?: string): boolean {
  if (!message) return false;
  return SENSITIVE_ERROR_PATTERNS.some((pattern) => pattern.test(message));
}

export function sanitizeServerMessage(message: unknown, fallback: string): string {
  if (typeof message !== 'string') return fallback;
  const normalized = message.trim();
  if (!normalized || normalized === 'Internal server error' || containsSensitiveErrorDetail(normalized))
    return fallback;
  return normalized.length > 500 ? fallback : normalized;
}

function isStringMap(value: unknown): value is Record<string, string> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const entries = Object.entries(value);
  return entries.length > 0 && entries.every(([key, item]) => key !== 'requestId' && typeof item === 'string');
}

export function classifyStatus(status?: number): AppErrorDetails['category'] {
  if (!status) return 'unknown';
  if (status === 400 || status === 422) return 'validation';
  if (status === 401 || status === 403) return 'permission';
  if (status === 404 || status === 410) return 'not-found';
  if (status === 409 || status === 423) return 'conflict';
  if (status === 429) return 'rate-limit';
  if (status >= 500) return 'server';
  return 'unknown';
}

export function normalizeAppError(response: AxiosResponse | undefined, fallback: string): AppError {
  const body = response?.data || {};
  const nestedData = body?.data;
  const payload =
    nestedData &&
    typeof nestedData === 'object' &&
    !Array.isArray(nestedData) &&
    (nestedData.code || nestedData.message || nestedData.traceId || nestedData.fieldErrors)
      ? nestedData
      : body;
  const legacyFieldErrors = isStringMap(payload?.messageDetail) ? payload.messageDetail : undefined;
  const requestId =
    response?.headers?.['x-request-id'] ||
    payload?.traceId ||
    payload?.requestId ||
    payload?.messageDetail?.requestId ||
    body?.traceId ||
    body?.requestId;
  const status = response?.status;
  return new AppError({
    code: payload?.code ?? body?.code,
    status,
    category: classifyStatus(status),
    messageKey: payload?.messageKey ?? body?.messageKey,
    message: sanitizeServerMessage(payload?.message ?? body?.message, fallback),
    requestId,
    retryable: payload?.retryable ?? body?.retryable ?? Boolean(response?.status && response.status >= 500),
    fieldErrors: payload?.fieldErrors || legacyFieldErrors,
    context: payload?.context ?? body?.context,
  });
}

export function localizeAppError(error: AppError, translate: (key: string) => string): AppError {
  if (!error.messageKey || error.messageKey === 'api.businessError') return error;
  const localized = translate(error.messageKey);
  if (!localized || localized === error.messageKey) return error;
  return new AppError({ ...error, message: localized });
}

export function ensureAppError(error: unknown, fallback: string): AppError {
  if (error instanceof AppError) return error;
  const message = error instanceof Error ? sanitizeServerMessage(error.message, fallback) : fallback;
  return new AppError({ message, retryable: true });
}

export function formatAppErrorMessage(error: AppErrorDetails): string {
  if (!error.requestId) return error.message;
  return `${error.message} (${error.requestId})`;
}
