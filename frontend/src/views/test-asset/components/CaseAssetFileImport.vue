<template>
  <a-modal
    :visible="visible"
    title="导入资产用例"
    :ok-loading="loading"
    @update:visible="emit('update:visible', $event)"
    @ok="submit"
  >
    <a-form :model="{ cover }" layout="vertical">
      <a-form-item label="用例文件" required>
        <a-upload :auto-upload="false" :limit="1" accept=".xlsx,.xls,.xmind" @change="onFileChange" />
      </a-form-item>
      <a-form-item label="重名用例">
        <a-radio-group v-model="cover"
          ><a-radio :value="false">跳过</a-radio><a-radio :value="true">覆盖</a-radio></a-radio-group
        >
      </a-form-item>
    </a-form>
    <a-alert>支持 Excel 和 XMind；导入目标为当前用例项目，不会创建业务项目。</a-alert>
    <a-alert
      v-if="job"
      class="mt-4"
      :type="job.status === 'FAILED' ? 'error' : job.status === 'PARTIAL_SUCCESS' ? 'warning' : 'info'"
    >
      导入状态：{{ job.status }}；成功 {{ job.successCount || 0 }} 条，失败 {{ job.failCount || 0 }} 条
      <template v-if="job.errorDetail"><br />{{ job.errorDetail }}</template>
      <template v-if="job.errorDetail"><br /><a-link @click="downloadErrors">下载错误明细</a-link></template>
    </a-alert>
  </a-modal>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import {
    type CaseAssetFileImportJob,
    downloadCaseAssetFileImportErrors,
    getCaseAssetFileImportJob,
    getLatestCaseAssetFileImportJob,
    importCaseAssetFile,
  } from '@/api/modules/case-management/featureCase';
  import { downloadByteFile } from '@/utils';

  import type { FileItem } from '@arco-design/web-vue';

  const props = defineProps<{ visible: boolean; catalogId: string }>();
  const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'success'): void }>();
  const file = ref<File>();
  const cover = ref(false);
  const loading = ref(false);
  const job = ref<CaseAssetFileImportJob>();
  let pollTimer: ReturnType<typeof setTimeout> | undefined;
  let pollDeadline = 0;
  onBeforeUnmount(() => pollTimer && clearTimeout(pollTimer));
  async function pollJob(jobId: string) {
    try {
      job.value = await getCaseAssetFileImportJob(jobId);
    } catch (error) {
      loading.value = false;
      Message.error('查询导入任务失败，可稍后重新打开页面查看导入结果');
      return;
    }
    if (job.value.status === 'RUNNING') {
      if (Date.now() >= pollDeadline) {
        loading.value = false;
        Message.warning('导入仍在后台执行，请稍后刷新资产用例列表');
        return;
      }
      pollTimer = setTimeout(() => pollJob(jobId), 1000);
      return;
    }
    loading.value = false;
    if (job.value.status === 'SUCCESS') Message.success('导入完成');
    else if (job.value.status === 'PARTIAL_SUCCESS') Message.warning('导入完成，部分用例失败');
    else Message.error(job.value.errorDetail || '导入失败');
    if (job.value.status !== 'FAILED') emit('success');
  }
  watch(
    () => [props.visible, props.catalogId] as const,
    async ([visible, catalogId]) => {
      if (!visible || !catalogId || loading.value) return;
      const latest = await getLatestCaseAssetFileImportJob(catalogId);
      job.value = latest.exists === false ? undefined : latest;
      if (job.value?.status === 'RUNNING') {
        pollDeadline = Date.now() + 5 * 60 * 1000;
        await pollJob(job.value.id);
      }
    },
    { immediate: true }
  );
  function onFileChange(fileList: FileItem[]) {
    file.value = fileList[0]?.file;
  }
  async function downloadErrors() {
    if (!job.value?.id) return;
    const content = await downloadCaseAssetFileImportErrors(job.value.id);
    downloadByteFile(content, `case-asset-import-errors-${job.value.id}.txt`);
  }
  async function submit() {
    if (!file.value) {
      Message.warning('请选择导入文件');
      return false;
    }
    const type = file.value.name.toLowerCase().endsWith('.xmind') ? 'xmind' : 'excel';
    loading.value = true;
    try {
      const result = await importCaseAssetFile(props.catalogId, file.value, type, cover.value);
      Message.success('导入任务已提交');
      file.value = undefined;
      pollDeadline = Date.now() + 5 * 60 * 1000;
      await pollJob(result.jobId);
      return true;
    } catch (e) {
      loading.value = false;
      throw e;
    }
  }
</script>
