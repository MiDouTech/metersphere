const SENSITIVE_KEYS = /secret|token|authorization|targetId|chatId|userid|botId/i;

export function mask(value) {
  if (!value) return '';
  const text = String(value);
  if (text.length <= 6) return '******';
  return `${text.slice(0, 2)}******${text.slice(-2)}`;
}

export function sanitize(value) {
  if (Array.isArray(value)) return value.map(sanitize);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, SENSITIVE_KEYS.test(key) ? mask(item) : sanitize(item)])
    );
  }
  return value;
}

const write = (level, message, fields = {}) => {
  const output = JSON.stringify({ time: new Date().toISOString(), level, message, ...sanitize(fields) });
  (level === 'error' ? console.error : console.log)(output);
};

export const logger = {
  debug: (message, fields) => process.env.NODE_ENV !== 'production' && write('debug', message, fields),
  info: (message, fields) => write('info', message, fields),
  warn: (message, fields) => write('warn', message, fields),
  error: (message, fields) => write('error', message, fields),
};

export const sdkLogger = {
  debug: (message, ...args) => logger.debug(message, { args }),
  info: (message, ...args) => logger.info(message, { args }),
  warn: (message, ...args) => logger.warn(message, { args }),
  error: (message, ...args) => logger.error(message, { args }),
};
