import { getCurrentInstance, onActivated, onDeactivated, onMounted, onUnmounted } from 'vue';

import { isSessionExpiredHandling } from '@/utils/httpMessage';
import createListRefreshGate from '@/utils/listRefreshGate';

/** Refresh active lists without polling hidden tabs or interrupting form editing. */
export default function useListRefresh(
  refresh: () => Promise<unknown>,
  ready: () => boolean,
  intervalMs = 60000,
  options: { allowReadOnlyOverlay?: boolean } = {}
) {
  const instance = getCurrentInstance();
  let active = false;
  let mounted = false;
  let timer: ReturnType<typeof setInterval> | undefined;
  const gate = createListRefreshGate(refresh, () => {
    if (
      !active ||
      !ready() ||
      !navigator.onLine ||
      isSessionExpiredHandling() ||
      document.visibilityState !== 'visible'
    )
      return false;
    const root = instance?.proxy?.$el;
    const visibleRoot = root instanceof HTMLElement ? root : root?.nextElementSibling;
    if (visibleRoot instanceof HTMLElement && !visibleRoot.getClientRects().length) return false;
    const element = document.activeElement;
    if (element instanceof HTMLElement && (element.matches('input, textarea, select') || element.isContentEditable))
      return false;
    // A modal/drawer may contain unsaved edits even when an input has lost focus.
    return (
      options.allowReadOnlyOverlay ||
      !Array.from(document.querySelectorAll('.arco-modal-wrapper, .arco-drawer')).some(
        (dialog) => dialog.getClientRects().length > 0
      )
    );
  });
  const run = async () => {
    try {
      await gate.run();
    } catch {
      // The list and HTTP layer own error presentation; the next activation may retry.
    }
  };
  onMounted(() => {
    active = true;
    document.addEventListener('visibilitychange', run);
    window.addEventListener('focus', run);
    window.addEventListener('online', run);
    timer = setInterval(run, intervalMs);
  });
  onActivated(() => {
    active = true;
    if (mounted) run();
    mounted = true;
  });
  onDeactivated(() => {
    active = false;
  });
  onUnmounted(() => {
    active = false;
    gate.dispose();
    clearInterval(timer);
    document.removeEventListener('visibilitychange', run);
    window.removeEventListener('focus', run);
    window.removeEventListener('online', run);
  });
}
