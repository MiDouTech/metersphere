import { getCaseExportNotificationTicket } from '@/api/modules/case-management/featureCase';

/** Optional acceleration only: consumers must re-read the authenticated HTTP task endpoint. */
export default function watchCaseExportNotifications(taskId: string, projectId: string, changed: () => void) {
  let disposed = false;
  let generation = 0;
  let attempts = 0;
  let version = -1;
  let socket: WebSocket | undefined;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let connecting = false;
  let connect: () => Promise<void>;
  let dispose: () => void;
  const available = () => !disposed && document.visibilityState !== 'hidden' && navigator.onLine;
  function disconnect() {
    generation += 1;
    connecting = false;
    clearTimeout(timer);
    const previous = socket;
    socket = undefined;
    if (previous) {
      previous.onclose = null;
      previous.onmessage = null;
      previous.close(1000, 'View paused');
    }
  }
  function schedule() {
    if (!available() || attempts >= 5) return;
    clearTimeout(timer);
    timer = setTimeout(connect, Math.min(1000 * 2 ** attempts, 30000) + Math.random() * 500);
  }
  connect = async () => {
    if (!available() || socket || connecting || attempts >= 5) return;
    connecting = true;
    attempts += 1;
    const current = generation;
    try {
      const ticket = await getCaseExportNotificationTicket(taskId);
      if (current !== generation || !available()) return;
      if (!ticket.enabled) {
        dispose();
        return;
      }
      if (
        ticket.taskId !== taskId ||
        ticket.protocol !== 'ms-case-export-v1' ||
        !/^[\w-]{43}$/.test(ticket.ticket || '')
      ) {
        dispose();
        return;
      }
      const prefix = String(import.meta.env.VITE_API_BASE_URL || '').replace(/^\/+|\/+$/g, '');
      const url = new URL(`${prefix ? `/${prefix}` : ''}/functional/case/export/ws`, window.location.origin);
      url.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      url.searchParams.set('taskId', taskId);
      const ws = new WebSocket(url, [ticket.protocol, `ticket.${ticket.ticket}`]);
      socket = ws;
      timer = setTimeout(() => ws.close(), 5000);
      ws.onopen = () => clearTimeout(timer);
      ws.onmessage = (event) => {
        if (current !== generation || socket !== ws) return;
        try {
          const message = JSON.parse(event.data);
          const { task } = message;
          if (
            message.type === 'EXPORT_TASK' &&
            task?.taskId === taskId &&
            task.projectId === projectId &&
            Number.isSafeInteger(task.stateVersion) &&
            task.stateVersion > version
          ) {
            version = task.stateVersion;
            changed();
          }
        } catch {
          // Ignore malformed notifications; the independent HTTP poll remains authoritative.
        }
      };
      ws.onclose = (event) => {
        if (current !== generation || socket !== ws) return;
        clearTimeout(timer);
        socket = undefined;
        if (event.code !== 1000 && event.code !== 1008) schedule();
      };
    } catch {
      if (current === generation) schedule();
    } finally {
      if (current === generation) connecting = false;
    }
  };
  function visibilityChanged() {
    if (!available()) disconnect();
    else connect();
  }
  dispose = () => {
    disposed = true;
    disconnect();
    document.removeEventListener('visibilitychange', visibilityChanged);
    window.removeEventListener('online', visibilityChanged);
    window.removeEventListener('offline', visibilityChanged);
  };
  document.addEventListener('visibilitychange', visibilityChanged);
  window.addEventListener('online', visibilityChanged);
  window.addEventListener('offline', visibilityChanged);
  connect();
  return dispose;
}
