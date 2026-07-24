import { computed, isRef, onBeforeUnmount, onMounted, type Ref, ref, unref, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { Message } from '@arco-design/web-vue';

import {
  acquireResourceEditLock,
  getResourceEditAutosaveEnabled,
  getResourceEditMeta,
  heartbeatResourceEditLock,
  recordResourceEditSnapshot,
  redoResourceEdit,
  releaseResourceEditLock,
  type ResourceEditLockRequest,
  type ResourceEditType,
  undoResourceEdit,
} from '@/api/modules/common/resourceEdit';
import { useI18n } from '@/hooks/useI18n';
import useModal from '@/hooks/useModal';

export type AutoSaveStatus = 'idle' | 'dirty' | 'saving' | 'error' | 'locked-readonly';

export interface UseAutoSaveEditorOptions {
  resourceType: ResourceEditType;
  resourceId: Ref<string> | string;
  projectId: Ref<string> | string;
  /** 是否有写权限且处于可编辑态 */
  canEdit: Ref<boolean> | boolean;
  /** 序列化当前表单为 JSON（正文白名单，不含附件） */
  serialize: () => string;
  /** 执行业务保存；成功后自动登记快照 */
  save: () => Promise<void>;
  /** Undo/Redo 后用 payload 回填表单 */
  applyPayload?: (payload: string) => void | Promise<void>;
  debounceMs?: number;
  heartbeatMs?: number;
}

function unrefValue<T>(v: Ref<T> | T): T {
  return isRef(v) ? unref(v) : v;
}

/**
 * 自动保存 + 编辑锁 + Undo/Redo 快捷键（S1 SDK）
 */
export default function useAutoSaveEditor(options: UseAutoSaveEditorOptions) {
  const { t } = useI18n();
  const { openModal } = useModal();

  const status = ref<AutoSaveStatus>('idle');
  const lastSavedAt = ref<number | null>(null);
  const readOnly = ref(false);
  const lockMessage = ref('');
  const undoAvailable = ref(0);
  const redoAvailable = ref(0);
  const autosaveEnabled = ref(true);
  const saving = computed(() => status.value === 'saving');

  let debounceTimer: ReturnType<typeof setTimeout> | null = null;
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  let lastSerialized = '';
  let lockHeld = false;
  let activeResourceId = '';

  function lockBody(): ResourceEditLockRequest {
    return {
      resourceType: options.resourceType,
      resourceId: unrefValue(options.resourceId),
      projectId: unrefValue(options.projectId),
    };
  }

  function clearDebounce() {
    if (debounceTimer) {
      clearTimeout(debounceTimer);
      debounceTimer = null;
    }
  }

  function clearHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  }

  async function releaseHeldLock() {
    clearDebounce();
    clearHeartbeat();
    if (!lockHeld || !activeResourceId) {
      lockHeld = false;
      return;
    }
    const body = {
      resourceType: options.resourceType,
      resourceId: activeResourceId,
      projectId: unrefValue(options.projectId),
    };
    lockHeld = false;
    activeResourceId = '';
    try {
      await releaseResourceEditLock(body);
    } catch {
      // ignore
    }
  }

  async function refreshMeta() {
    const id = unrefValue(options.resourceId);
    if (!id) return;
    try {
      const meta = await getResourceEditMeta(options.resourceType, id);
      undoAvailable.value = meta.undoAvailable || 0;
      redoAvailable.value = meta.redoAvailable || 0;
    } catch {
      // ignore
    }
  }

  async function doSave(fromManual = false) {
    if (!unrefValue(options.canEdit) || readOnly.value) return false;
    if (status.value === 'saving') return false;
    const payload = options.serialize();
    if (!fromManual && payload === lastSerialized && status.value !== 'error') {
      return true;
    }
    status.value = 'saving';
    try {
      await options.save();
      lastSerialized = payload;
      lastSavedAt.value = Date.now();
      status.value = 'idle';
      if (autosaveEnabled.value) {
        try {
          await recordResourceEditSnapshot({ ...lockBody(), payload });
          await refreshMeta();
        } catch {
          // 快照失败不阻断保存成功态
        }
      }
      return true;
    } catch {
      status.value = 'error';
      Message.error(t('common.autoSave.saveFailed'));
      return false;
    }
  }

  function markDirty() {
    if (!unrefValue(options.canEdit) || readOnly.value || !autosaveEnabled.value) return;
    status.value = 'dirty';
    clearDebounce();
    const ms = options.debounceMs ?? 1800;
    debounceTimer = setTimeout(() => {
      doSave(false);
    }, ms);
  }

  function onBlurSave() {
    if (!unrefValue(options.canEdit) || readOnly.value || !autosaveEnabled.value) return;
    clearDebounce();
    if (status.value === 'dirty' || status.value === 'error') {
      doSave(false);
    }
  }

  async function manualSave() {
    clearDebounce();
    return doSave(true);
  }

  async function undo() {
    if (!unrefValue(options.canEdit) || readOnly.value) return;
    const res = await undoResourceEdit(lockBody());
    if (!res.success) {
      Message.warning(res.message || t('common.autoSave.nothingToUndo'));
      return;
    }
    if (res.payload && options.applyPayload) {
      await options.applyPayload(res.payload);
      lastSerialized = res.payload;
    }
    undoAvailable.value = res.undoAvailable;
    redoAvailable.value = res.redoAvailable;
    status.value = 'idle';
    Message.success(t('common.autoSave.undoSuccess'));
  }

  async function redo() {
    if (!unrefValue(options.canEdit) || readOnly.value) return;
    const res = await redoResourceEdit(lockBody());
    if (!res.success) {
      Message.warning(res.message || t('common.autoSave.nothingToRedo'));
      return;
    }
    if (res.payload && options.applyPayload) {
      await options.applyPayload(res.payload);
      lastSerialized = res.payload;
    }
    undoAvailable.value = res.undoAvailable;
    redoAvailable.value = res.redoAvailable;
    status.value = 'idle';
    Message.success(t('common.autoSave.redoSuccess'));
  }

  function onKeydown(e: KeyboardEvent) {
    if (!unrefValue(options.canEdit) || readOnly.value) return;
    const mod = e.ctrlKey || e.metaKey;
    if (!mod) return;
    const key = e.key.toLowerCase();
    if (key === 's') {
      e.preventDefault();
      manualSave();
      return;
    }
    if (key === 'z' && !e.shiftKey) {
      e.preventDefault();
      undo();
      return;
    }
    if ((key === 'z' && e.shiftKey) || key === 'y') {
      e.preventDefault();
      redo();
    }
  }

  function blockLeave(next: (ok?: boolean) => void) {
    const blocked = status.value === 'error' || status.value === 'dirty' || status.value === 'saving';
    if (!blocked) {
      next();
      return;
    }
    openModal({
      type: 'warning',
      title: t('common.unSaveLeaveTitle'),
      content: status.value === 'error' ? t('common.autoSave.leaveWithError') : t('common.unSaveLeaveContent'),
      okText: t('common.leave'),
      cancelText: t('common.stay'),
      onBeforeOk: async () => {
        next();
      },
      hideCancel: false,
    });
  }

  onBeforeRouteLeave((to, from, next) => {
    if (to.path === from.path) {
      next();
      return;
    }
    blockLeave(next);
  });

  async function initLock() {
    const id = unrefValue(options.resourceId);
    const can = unrefValue(options.canEdit);
    await releaseHeldLock();
    readOnly.value = false;
    lockMessage.value = '';
    status.value = 'idle';
    undoAvailable.value = 0;
    redoAvailable.value = 0;

    if (!can || !id) {
      return;
    }

    try {
      autosaveEnabled.value = await getResourceEditAutosaveEnabled();
    } catch {
      autosaveEnabled.value = true;
    }

    try {
      const lock = await acquireResourceEditLock({
        resourceType: options.resourceType,
        resourceId: id,
        projectId: unrefValue(options.projectId),
      });
      if (!lock.acquired) {
        readOnly.value = true;
        status.value = 'locked-readonly';
        lockMessage.value = lock.message || t('common.autoSave.lockedByOther');
        return;
      }
      lockHeld = true;
      activeResourceId = id;
      readOnly.value = false;
      lastSerialized = options.serialize();
      await refreshMeta();
      const hb = options.heartbeatMs ?? 60_000;
      heartbeatTimer = setInterval(() => {
        heartbeatResourceEditLock(lockBody()).catch(() => undefined);
      }, hb);
    } catch {
      Message.error(t('common.autoSave.lockFailed'));
    }
  }

  watch(
    () => ({
      id: unrefValue(options.resourceId),
      can: unrefValue(options.canEdit),
    }),
    (cur, prev) => {
      if (prev && cur.id === prev.id && cur.can === prev.can) return;
      initLock();
    },
    { immediate: true }
  );

  onMounted(() => {
    document.addEventListener('keydown', onKeydown);
  });

  onBeforeUnmount(() => {
    document.removeEventListener('keydown', onKeydown);
    releaseHeldLock();
  });

  return {
    status,
    saving,
    lastSavedAt,
    readOnly,
    lockMessage,
    undoAvailable,
    redoAvailable,
    autosaveEnabled,
    markDirty,
    onBlurSave,
    manualSave,
    undo,
    redo,
    blockLeave,
  };
}
