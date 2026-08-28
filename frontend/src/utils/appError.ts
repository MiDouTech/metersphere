import type { AxiosResponse } from 'axios';

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
  const legacyFieldErrors = isStringMap(body?.messageDetail) ? body.messageDetail : undefined;
  const requestId =
    response?.headers?.['x-request-id'] ||
    body?.traceId ||
    body?.requestId ||
    body?.messageDetail?.requestId ||
    body?.data?.requestId;
  const status = response?.status;
  return new AppError({
    code: body?.code,
    status,
    category: classifyStatus(status),
    messageKey: body?.messageKey,
    message: sanitizeServerMessage(body?.message, fallback),
    requestId,
    retryable: body?.retryable ?? Boolean(response?.status && response.status >= 500),
    fieldErrors: body?.fieldErrors || legacyFieldErrors,
    context: body?.context,
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
