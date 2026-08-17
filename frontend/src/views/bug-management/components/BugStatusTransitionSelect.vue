<template>
  <a-dropdown
    v-if="canUpdate && (runtime?.transitions.length || !loaded)"
    trigger="click"
    position="bl"
    :disabled="loading"
    @popup-visible-change="handleVisible"
    @select="handleSelect"
  >
    <button type="button" class="bug-status-button" :class="getBugStatusClass(displayName)">
      {{ displayName || '-' }}
      <icon-down v-if="runtime?.transitions.length" class="ml-1" />
    </button>
    <template #content>
      <a-doption
        v-for="item in runtime?.transitions || []"
        :key="item.transitionId"
        :value="item.transitionId"
        :disabled="!item.operable"
      >
        <span class="bug-status-button bug-status-option" :class="getBugStatusClass(item.targetStatus.name)">
          {{ item.targetStatus.name }}
        </span>
      </a-doption>
      <a-doption v-if="loaded && !runtime?.transitions.length" disabled>
        {{ runtime?.unavailableReason || '当前没有可执行的下一步状态' }}
      </a-doption>
    </template>
  </a-dropdown>
  <a-tooltip v-else :content="runtime?.unavailableReason || noTransitionReason">
    <span class="bug-status-button inline-flex items-center gap-1" :class="getBugStatusClass(displayName)">
      {{ displayName || '-' }}
      <icon-info-circle v-if="canUpdate && loaded" />
    </span>
  </a-tooltip>

  <a-modal
    v-model:visible="overrideVisible"
    title="管理员强制流转"
    :ok-loading="submitting"
    @ok="submitOverride"
    @cancel="overrideReason = ''"
  >
    <a-alert class="mb-3" type="warning">
      您未命中此流转的普通流程角色。管理员只能绕过角色授权，不能绕过合法下一步；操作原因将写入审计。
    </a-alert>
    <a-textarea v-model="overrideReason" :max-length="2000" show-word-limit placeholder="请输入强制流转原因" />
  </a-modal>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import { type BugTransitionRuntime, getBugTransitions, transitionBug } from '@/api/modules/bug-management';
  import { hasAnyPermission } from '@/utils/permission';

  import getBugStatusClass from '../utils/bugStatusStyle';

  const props = defineProps<{
    bugId: string;
    status: string;
    statusName?: string;
    initialRuntime?: BugTransitionRuntime;
  }>();
  const emit = defineEmits<{ (e: 'success', runtime: BugTransitionRuntime): void }>();

  const runtime = ref<BugTransitionRuntime | undefined>(props.initialRuntime);
  const loading = ref(false);
  const loaded = ref(Boolean(props.initialRuntime));
  const submitting = ref(false);
  const selectedTransitionId = ref('');
  const overrideVisible = ref(false);
  const overrideReason = ref('');
  const canUpdate = hasAnyPermission(['PROJECT_BUG:READ+UPDATE']);
  const displayName = computed(() => runtime.value?.currentStatus.name || props.statusName || '未知状态');
  const noTransitionReason = computed(() =>
    canUpdate && loaded.value ? '当前状态没有可执行的下一步；请联系管理员检查流程角色和流转规则' : '当前用户无修改权限'
  );

  async function load() {
    loading.value = true;
    try {
      runtime.value = await getBugTransitions(props.bugId);
      loaded.value = true;
    } finally {
      loading.value = false;
    }
  }

  function handleVisible(visible: boolean) {
    if (visible && !loaded.value) load();
  }

  watch(
    () => props.initialRuntime,
    (value) => {
      if (!value) return;
      runtime.value = value;
      loaded.value = true;
    }
  );

  async function execute(reason?: string, override = false) {
    const item = runtime.value?.transitions.find(
      (transition) => transition.transitionId === selectedTransitionId.value
    );
    if (!item || !runtime.value) return;
    submitting.value = true;
    try {
      runtime.value = await transitionBug(props.bugId, {
        transitionId: item.transitionId,
        targetStatusId: item.targetStatus.id,
        expectedUpdateTime: runtime.value.updateTime,
        override,
        overrideReason: reason,
      });
      Message.success('缺陷状态已流转');
      overrideVisible.value = false;
      overrideReason.value = '';
      emit('success', runtime.value);
    } finally {
      submitting.value = false;
    }
  }

  function handleSelect(value: string | number | Record<string, any> | undefined) {
    selectedTransitionId.value = String(value || '');
    const item = runtime.value?.transitions.find(
      (transition) => transition.transitionId === selectedTransitionId.value
    );
    if (!item) return;
    if (item.overrideRequired) overrideVisible.value = true;
    else execute();
  }

  function submitOverride() {
    if (!overrideReason.value.trim()) {
      Message.error('管理员强制流转原因不能为空');
      return false;
    }
    return execute(overrideReason.value.trim(), true);
  }
</script>
