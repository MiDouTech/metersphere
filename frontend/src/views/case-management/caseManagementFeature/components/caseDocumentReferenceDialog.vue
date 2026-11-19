<template>
  <a-modal
    :visible="visible"
    title="引用产品文档版本"
    ok-text="引用所选版本"
    :ok-loading="saving"
    :ok-button-props="{ disabled: loading || !selected }"
    :mask-closable="false"
    @cancel="emit('update:visible', false)"
    @before-ok="save"
  >
    <a-alert v-if="error" class="mb-3" type="error">{{ error }}</a-alert>
    <a-form :model="{ keyword, selected }" layout="vertical">
      <a-form-item label="搜索文档" field="keyword">
        <a-input-search v-model="keyword" placeholder="输入文档名称或 ID" :loading="loading" @search="load" />
      </a-form-item>
      <a-form-item label="已发布版本" field="selected" required>
        <a-select v-model="selected" :loading="loading" placeholder="选择要引用的版本">
          <a-option v-for="version in versions" :key="version.id" :value="version.id">
            {{ version.assetName || version.assetId }} · v{{ version.versionNo }}
          </a-option>
        </a-select>
      </a-form-item>
    </a-form>
    <a-empty v-if="!loading && !error && !versions.length" description="未找到可引用的版本" />
  </a-modal>
</template>

<script setup lang="ts">
  import { onMounted, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import { pageTestAssetVersions, referenceTestAssetDocument, type TestAssetVersion } from '@/api/modules/ai-execution';

  const props = defineProps<{ visible: boolean; projectId: string; caseId: string }>();
  const emit = defineEmits<{ 'update:visible': [visible: boolean] }>();
  const keyword = ref('');
  const selected = ref<string>();
  const versions = ref<TestAssetVersion[]>([]);
  const loading = ref(false);
  const saving = ref(false);
  const error = ref('');
  let request = 0;

  async function load() {
    const generation = ++request;
    error.value = '';
    selected.value = undefined;
    versions.value = [];
    loading.value = true;
    try {
      const result = await pageTestAssetVersions({
        projectId: props.projectId,
        assetType: 'DOCUMENT',
        keyword: keyword.value.trim() || undefined,
        pageSize: 100,
      });
      if (generation === request) versions.value = (result.list || []).filter((item) => item.status === 'PUBLISHED');
    } catch {
      if (generation === request) error.value = '暂时无法读取文档版本，请检查权限后重试。';
    } finally {
      if (generation === request) loading.value = false;
    }
  }

  async function save() {
    const version = versions.value.find((item) => item.id === selected.value);
    if (!version || saving.value || loading.value) return false;
    error.value = '';
    saving.value = true;
    try {
      await referenceTestAssetDocument(props.caseId, {
        projectId: props.projectId,
        documentId: version.assetId,
        documentVersionId: version.id,
      });
      Message.success('已引用所选文档版本');
      emit('update:visible', false);
      return true;
    } catch {
      error.value = '引用失败，请确认所选版本仍已发布，并检查用例编辑权限。';
      return false;
    } finally {
      saving.value = false;
    }
  }

  watch(
    () => [props.projectId, props.caseId],
    () => {
      request++;
      emit('update:visible', false);
    }
  );
  onMounted(load);
</script>
