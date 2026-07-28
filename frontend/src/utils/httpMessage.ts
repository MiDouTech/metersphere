import { Message, Modal } from '@arco-design/web-vue';

/** 全局错误提示默认停留时间（ms） */
export const HTTP_MESSAGE_DURATION = 5000;

/** 同类文案去重窗口（ms） */
const DEDUPE_WINDOW_MS = 3000;

let lastMessageKey = '';
let lastMessageAt = 0;
let sessionExpiredHandling = false;

/**
 * 同类错误提示去重：短时间内相同文案只弹一次
 */
export function showHttpErrorMessage(
  content: string,
  options?: { duration?: number; mode?: 'message' | 'modal'; title?: string }
): void {
  if (!content) {
    return;
  }
  const mode = options?.mode || 'message';
  const duration = options?.duration ?? HTTP_MESSAGE_DURATION;
  const now = Date.now();
  const key = `${mode}:${content}`;
  if (key === lastMessageKey && now - lastMessageAt < DEDUPE_WINDOW_MS) {
    return;
  }
  lastMessageKey = key;
  lastMessageAt = now;

  if (mode === 'modal') {
    Modal.error({ title: options?.title || 'Error', content });
    return;
  }
  Message.error({ content, duration });
}

/**
 * 会话失效处理锁：并发 401 只走一次登出/提示
 */
export function tryBeginSessionExpiredHandling(): boolean {
  if (sessionExpiredHandling) {
    return false;
  }
  sessionExpiredHandling = true;
  return true;
}

export function endSessionExpiredHandling(): void {
  sessionExpiredHandling = false;
}

export function isSessionExpiredHandling(): boolean {
  return sessionExpiredHandling;
}
