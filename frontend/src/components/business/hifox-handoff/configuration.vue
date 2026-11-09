<template>
  <section>
    <h2>Webhook测试计划推送</h2>
    <p>MSP仅发送测试计划消息，接收后的建单和通知由你在接收平台配置。</p>
    <a-alert> {{ risk }} </a-alert>
    <a-spin :loading="loading">
      <a-alert v-if="error" type="error">配置读取失败或无配置权限 <a-button @click="load">重试</a-button></a-alert>
      <template v-else-if="view">
        <a-alert v-if="view.encryptionConfigured === false" type="warning"
          >Webhook加密配置缺失或不可用，请联系部署管理员核对持久化加密密钥后保存。</a-alert
        >
        <a-alert v-if="view.sourceConfigured === false" type="warning"
          >尚未配置MSP来源环境或HTTPS来源基地址，配置完成后才能启用推送。</a-alert
        >
        <a-alert v-if="actionError" type="error">{{ actionError }}</a-alert>
        <p>源环境：{{ view.environment }}；项目：{{ projectId }}；{{ view.enabled ? '已启用' : '已关闭' }}</p>
        <p>{{ view.configured ? `接收地址已配置（版本 ${view.urlRevision}）` : '尚未配置接收地址' }}</p>
        <a-form :model="draft" layout="vertical" @submit.prevent>
          <a-form-item :label="view.configured ? '替换接收地址（留空保留）' : 'HTTPS接收地址'">
            <a-input v-model="draft.url" :max-length="4096" autocomplete="off" />
          </a-form-item>
          <a-form-item label="认证模式">
            <a-select v-model="draft.authMode"
              ><a-option value="NONE">无额外认证</a-option><a-option value="BEARER">Bearer Token</a-option></a-select
            >
          </a-form-item>
          <a-form-item v-if="draft.authMode === 'BEARER'" label="Token（留空保留已配置值）">
            <a-input-password v-model="draft.token" autocomplete="new-password" :max-length="8192" />
          </a-form-item>
          <a-checkbox v-if="view.tokenConfigured && draft.authMode === 'NONE'" v-model="draft.clearToken"
            >确认清除已保存认证</a-checkbox
          >
        </a-form>
        <div class="my-4 flex flex-wrap gap-2">
          <a-button :disabled="busy" @click="save">保存配置</a-button>
          <a-button :disabled="busy || view.enabled || !view.configured" @click="enable">启用预览</a-button>
          <a-popconfirm content="停止新出站，在途请求无法撤回；既有暂停需有权者确认恢复。" @ok="disable"
            ><a-button :disabled="busy || !view.enabled">关闭推送</a-button></a-popconfirm
          >
        </div>
        <a-checkbox v-model="externalGrant">允许当前环境和项目向已配置接收地址出站</a-checkbox>
        <a-button :disabled="busy" @click="grant">保存出站授权</a-button>
        <a-divider />
        <Records :project-id="projectId" />
      </template>
    </a-spin>
    <a-modal v-model:visible="visible" title="确认启用" :on-before-ok="confirmEnable">
      <p>{{ preview?.message }}</p
      ><p>{{ preview?.payload.scope }}</p>
      <p>{{ preview?.payload.visibilityMessage }}</p>
      <p v-if="preview?.payload.visibleCount !== undefined"
        >当前可见 {{ preview.payload.visibleCount }} 条（非项目历史总量）</p
      >
      <p v-for="row in preview?.payload.visible" :key="row.planId"
        >{{ row.planName }}（{{ row.planId }}）：功能用例{{ row.functionalCount }}条</p
      >
      <p>仅自动处理启用后新通过计划；历史计划不自动补发。</p>
      <a-checkbox v-model="acknowledged">已理解HTTP结果和重复处理风险</a-checkbox>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import Records from './records.vue';

  import type { Configuration, ConfigView, PreviewView } from '@/api/modules/hifox-handoff';
  import {
    confirmAction,
    disableConfiguration,
    getConfiguration,
    grantConfiguration,
    previewAction,
    risk,
    saveConfiguration,
  } from '@/api/modules/hifox-handoff';
  import createLatestRequest from '@/utils/latestRequest';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';

  const props = defineProps<{ projectId: string }>();
  const empty = (): Configuration => ({ url: '', authMode: 'NONE', token: '', clearToken: false });
  const draft = ref<Configuration>(empty());
  const view = ref<ConfigView>();
  const loading = ref(false);
  const busy = ref(false);
  const error = ref(false);
  const actionError = ref('');
  const visible = ref(false);
  const acknowledged = ref(false);
  const externalGrant = ref(false);
  const preview = ref<PreviewView>();
  const request = createLatestRequest();
  async function load() {
    const seq = request.begin();
    const project = props.projectId;
    loading.value = true;
    error.value = false;
    actionError.value = '';
    view.value = undefined;
    visible.value = false;
    draft.value = empty();
    try {
      const result = await getConfiguration(project);
      if (!request.isCurrent(seq)) return;
      view.value = result;
      draft.value.authMode = result.authMode;
      externalGrant.value = result.external_grant;
    } catch {
      if (request.isCurrent(seq)) error.value = true;
    } finally {
      if (request.isCurrent(seq)) loading.value = false;
    }
  }
  async function run(action: () => Promise<unknown>) {
    if (busy.value) return;
    busy.value = true;
    actionError.value = '';
    const seq = request.current();
    try {
      await action();
      if (request.isCurrent(seq)) {
        busy.value = false;
        await load();
      }
    } catch (failure) {
      if (request.isCurrent(seq)) {
        actionError.value = formatAppErrorMessage(ensureAppError(failure, '操作失败，请核对权限、配置或重新预览'));
        Message.error(actionError.value);
      }
    } finally {
      if (request.isCurrent(seq)) busy.value = false;
    }
  }
  function save() {
    const project = props.projectId;
    const { revision } = view.value!;
    const input = { ...draft.value };
    return run(() => saveConfiguration(project, revision, input));
  }
  function grant() {
    const project = props.projectId;
    const { revision } = view.value!;
    const { value } = externalGrant;
    return run(() => grantConfiguration(project, revision, value));
  }
  function disable() {
    const project = props.projectId;
    const { revision } = view.value!;
    return run(() => disableConfiguration(project, revision));
  }
  async function enable() {
    if (busy.value) return;
    const seq = request.current();
    busy.value = true;
    try {
      const result = await previewAction(props.projectId, '', 'ENABLE');
      if (!request.isCurrent(seq)) return;
      preview.value = result;
      acknowledged.value = false;
      visible.value = true;
    } catch {
      if (request.isCurrent(seq)) Message.error('启用预览失败，请重试');
    } finally {
      if (request.isCurrent(seq)) busy.value = false;
    }
  }
  async function confirmEnable() {
    if (!acknowledged.value || !preview.value || busy.value || preview.value.expiresAt <= Date.now()) return false;
    const seq = request.current();
    busy.value = true;
    try {
      await confirmAction(props.projectId, preview.value.previewId);
      if (request.isCurrent(seq)) {
        busy.value = false;
        await load();
      }
      return true;
    } catch {
      if (request.isCurrent(seq)) Message.error('确认失败，预览可能变化或过期，请重新预览');
      return false;
    } finally {
      if (request.isCurrent(seq)) busy.value = false;
    }
  }
  watch(
    () => props.projectId,
    () => {
      busy.value = false;
      load();
    },
    { immediate: true }
  );
  onBeforeUnmount(() => {
    request.invalidate();
    draft.value = empty();
  });
</script>
