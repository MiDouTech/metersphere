<template>
  <MsCard simple>
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div>
        <div class="text-base font-medium">{{ t('system.agentIntegration.packageTitle') }}</div>
        <div class="mt-1 text-sm text-[var(--color-text-3)]">
          {{ t('system.agentIntegration.packageDescription') }}
        </div>
      </div>
      <div class="flex gap-2">
        <a-button :loading="loading" @click="load">{{ t('common.refresh') }}</a-button>
        <a-button v-permission="['SYSTEM_AGENT_PACKAGE:READ+ADD']" type="primary" @click="openUpload">
          {{ t('system.agentIntegration.packageUpload') }}
        </a-button>
      </div>
    </div>

    <a-alert type="info" class="mb-4">
      {{ t('system.agentIntegration.packageNotice') }}
    </a-alert>

    <a-table :data="packages" :loading="loading" row-key="id" :pagination="false">
      <template #empty><a-empty :description="t('system.agentIntegration.packageEmpty')" /></template>
      <a-table-column :title="t('system.agentIntegration.packageVersion')" data-index="version" :width="120" />
      <a-table-column :title="t('system.agentIntegration.packagePlatform')" :width="150">
        <template #cell="{ record }">{{ record.osType }} / {{ record.architecture }}</template>
      </a-table-column>
      <a-table-column :title="t('system.agentIntegration.packageFile')" data-index="fileName" />
      <a-table-column :title="t('system.agentIntegration.packageSize')" :width="110">
        <template #cell="{ record }">{{ formatSize(record.sizeBytes) }}</template>
      </a-table-column>
      <a-table-column title="SHA-256" :width="180">
        <template #cell="{ record }">
          <a-tooltip :content="record.sha256"
            ><span class="font-mono">{{ record.sha256.slice(0, 16) }}…</span></a-tooltip
          >
        </template>
      </a-table-column>
      <a-table-column :title="t('system.agentIntegration.packageDownloads')" data-index="downloadCount" :width="90" />
      <a-table-column :title="t('system.agentIntegration.packageStatus')" :width="100">
        <template #cell="{ record }">
          <a-tag :color="record.status === 'ACTIVE' ? 'green' : 'gray'">{{ record.status }}</a-tag>
        </template>
      </a-table-column>
      <a-table-column :title="t('common.operation')" :width="260" fixed="right">
        <template #cell="{ record }">
          <div class="flex gap-2">
            <a-link @click="download(record)">{{ t('common.download') }}</a-link>
            <a-link
              v-if="record.status !== 'ACTIVE'"
              v-permission="['SYSTEM_AGENT_PACKAGE:READ+UPDATE']"
              @click="activate(record)"
            >
              {{ t('common.enable') }}
            </a-link>
            <a-link
              v-else
              v-permission="['SYSTEM_AGENT_PACKAGE:READ+UPDATE']"
              status="warning"
              @click="deactivate(record)"
            >
              {{ t('common.disable') }}
            </a-link>
            <a-link
              v-if="record.status !== 'ACTIVE'"
              v-permission="['SYSTEM_AGENT_PACKAGE:READ+DELETE']"
              status="danger"
              @click="remove(record)"
            >
              {{ t('common.delete') }}
            </a-link>
          </div>
        </template>
      </a-table-column>
    </a-table>

    <a-modal
      v-model:visible="uploadVisible"
      :title="t('system.agentIntegration.packageUpload')"
      :ok-loading="uploading"
      :mask-closable="false"
      unmount-on-close
      @before-ok="submitUpload"
      @cancel="resetUpload"
    >
      <a-form ref="formRef" :model="form" layout="vertical">
        <a-form-item
          field="version"
          :label="t('system.agentIntegration.packageVersion')"
          required
          :rules="[{ required: true, message: t('system.agentIntegration.packageVersionRequired') }]"
        >
          <a-input v-model="form.version" placeholder="0.1.0" :max-length="64" />
        </a-form-item>
        <div class="grid grid-cols-2 gap-3">
          <a-form-item field="osType" :label="t('system.agentIntegration.packageOs')">
            <a-select v-model="form.osType"><a-option value="WINDOWS">Windows</a-option></a-select>
          </a-form-item>
          <a-form-item field="architecture" :label="t('system.agentIntegration.packageArch')">
            <a-select v-model="form.architecture"><a-option value="X64">x64</a-option></a-select>
          </a-form-item>
        </div>
        <a-form-item field="description" :label="t('common.desc')">
          <a-textarea v-model="form.description" :max-length="1000" show-word-limit />
        </a-form-item>
        <a-form-item field="activate" :label="t('system.agentIntegration.packageActivateAfterUpload')">
          <a-switch v-model="form.activate" />
        </a-form-item>
        <MsUpload
          v-model:file-list="fileList"
          accept="zip"
          size-unit="MB"
          :show-file-list="true"
          :auto-upload="false"
          :limit="1"
          :max-size="500"
          :draggable="true"
          main-text="system.agentIntegration.packageDrop"
          sub-text="system.agentIntegration.packageLimit"
        />
      </a-form>
    </a-modal>
  </MsCard>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import MsCard from '@/components/pure/ms-card/index.vue';
  import MsUpload from '@/components/pure/ms-upload/index.vue';

  import {
    activateAgentBridgePackage,
    deactivateAgentBridgePackage,
    deleteAgentBridgePackage,
    downloadAgentBridgePackageById,
    listAgentBridgePackages,
    uploadAgentBridgePackage,
  } from '@/api/modules/setting/userAgent';
  import { useI18n } from '@/hooks/useI18n';
  import useModal from '@/hooks/useModal';
  import { downloadByteFile } from '@/utils';

  import type { AgentBridgePackage } from '@/models/setting/userAgent';

  import type { FileItem, FormInstance } from '@arco-design/web-vue';

  const { t } = useI18n();
  const { openModal } = useModal();
  const loading = ref(false);
  const uploading = ref(false);
  const uploadVisible = ref(false);
  const packages = ref<AgentBridgePackage[]>([]);
  const fileList = ref<FileItem[]>([]);
  const formRef = ref<FormInstance>();
  const form = reactive({ version: '', osType: 'WINDOWS', architecture: 'X64', description: '', activate: true });

  async function load() {
    loading.value = true;
    try {
      packages.value = await listAgentBridgePackages();
    } finally {
      loading.value = false;
    }
  }

  function openUpload() {
    uploadVisible.value = true;
  }

  function resetUpload() {
    form.version = '';
    form.osType = 'WINDOWS';
    form.architecture = 'X64';
    form.description = '';
    form.activate = true;
    fileList.value = [];
  }

  async function submitUpload(done: (closed: boolean) => void) {
    const errors = await formRef.value?.validate();
    const file = fileList.value[0]?.file;
    if (errors || !file) {
      if (!file) Message.warning(t('system.agentIntegration.packageFileRequired'));
      done(false);
      return;
    }
    uploading.value = true;
    try {
      await uploadAgentBridgePackage({ ...form }, file);
      Message.success(t('system.agentIntegration.packageUploadSuccess'));
      resetUpload();
      await load();
      done(true);
    } catch {
      done(false);
    } finally {
      uploading.value = false;
    }
  }

  async function activate(record: AgentBridgePackage) {
    await activateAgentBridgePackage(record.id);
    Message.success(t('common.updateSuccess'));
    await load();
  }

  async function deactivate(record: AgentBridgePackage) {
    await deactivateAgentBridgePackage(record.id);
    Message.success(t('common.updateSuccess'));
    await load();
  }

  async function download(record: AgentBridgePackage) {
    const bytes = await downloadAgentBridgePackageById(record.id);
    downloadByteFile(bytes, record.fileName);
    await load();
  }

  function remove(record: AgentBridgePackage) {
    openModal({
      type: 'error',
      title: t('common.deleteConfirmTitle'),
      content: t('system.agentIntegration.packageDeleteConfirm', { version: record.version }),
      onBeforeOk: async () => {
        await deleteAgentBridgePackage(record.id);
        Message.success(t('common.deleteSuccess'));
        await load();
      },
    });
  }

  function formatSize(bytes: number) {
    if (!bytes) return '0 B';
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
  }

  onMounted(load);
</script>
