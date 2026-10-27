<template>
  <a-space wrap>
    <a-button
      v-if="canDelete"
      v-operable-permission="{
        code: 'CASE_ASSET_DELETE_BUTTON',
        permissions: ['CASE_ASSET:READ+DELETE'],
        typeList: ['ORGANIZATION'],
      }"
      :disabled="disabled"
      @click="open('DELETE')"
      >批量删除</a-button
    >
    <a-button v-if="canCategory" :disabled="disabled" @click="open('CATEGORY')">批量修改资产分类</a-button>
    <a-button
      v-if="canUpdate"
      v-operable-permission="{
        code: 'CASE_ASSET_UPDATE_BUTTON',
        permissions: ['CASE_ASSET:READ+UPDATE'],
        typeList: ['ORGANIZATION'],
      }"
      :disabled="disabled"
      @click="open('PRIORITY')"
      >批量修改用例等级</a-button
    >
    <a-button
      v-if="canUpdate"
      v-operable-permission="{
        code: 'CASE_ASSET_UPDATE_BUTTON',
        permissions: ['CASE_ASSET:READ+UPDATE'],
        typeList: ['ORGANIZATION'],
      }"
      :disabled="disabled"
      @click="open('TAGS')"
      >批量修改标签</a-button
    >
    <a-button v-if="task?.state === 'PENDING'" :loading="running" @click="resume">继续批量任务</a-button>
    <a-button v-if="task?.state === 'PARTIAL_SUCCESS'" :disabled="running" @click="retryFailed">重试失败项</a-button>
    <a-button v-if="task" @click="resultVisible = true">批量处理结果</a-button>
  </a-space>
  <a-modal v-model:visible="visible" title="确认批量操作" :ok-loading="running" @before-ok="submit">
    <a-alert class="mb-3">将处理当前勾选的 {{ ids.length }} 条用例。每条独立处理，失败原因可在结果中查看。</a-alert>
    <a-alert v-if="action === 'DELETE'" type="warning"
      >删除会进入回收站。被业务用例持续同步的资产可能再次同步出现；不会删除源用例。已有执行任务等业务限制仍生效。</a-alert
    >
    <a-form :model="{ categoryId, priority, tags, tagMode, sourceEditMode }" layout="vertical">
      <a-form-item v-if="action === 'CATEGORY'" label="资产分类（留空表示移出分类）">
        <a-tree-select
          v-model="categoryId"
          :data="categories"
          :field-names="{ key: 'id', title: 'name', children: 'children' }"
          allow-clear
        />
      </a-form-item>
      <a-form-item v-if="action === 'PRIORITY'" label="用例等级">
        <a-select v-model="priority"
          ><a-option v-for="p in ['P0', 'P1', 'P2', 'P3']" :key="p" :value="p">{{ p }}</a-option></a-select
        >
      </a-form-item>
      <template v-if="action === 'TAGS'">
        <a-form-item label="标签操作"
          ><a-radio-group v-model="tagMode"
            ><a-radio value="ADD">追加</a-radio><a-radio value="REMOVE">移除</a-radio
            ><a-radio value="REPLACE">替换</a-radio></a-radio-group
          ></a-form-item
        >
        <a-form-item label="标签"><a-input-tag v-model="tags" :max-tag-count="10" allow-clear /></a-form-item>
      </template>
      <a-form-item v-if="['PRIORITY', 'TAGS'].includes(action)" label="如遇来源绑定资产，明确选择处理方式">
        <a-radio-group v-model="sourceEditMode" direction="vertical"
          ><a-radio value="WRITE_BACK">回写源用例（需要源编辑权限；多个来源须单独编辑）</a-radio
          ><a-radio value="DETACH">脱离来源后修改（后续源同步生成独立镜像）</a-radio></a-radio-group
        >
      </a-form-item>
    </a-form>
  </a-modal>
  <a-modal v-model:visible="resultVisible" title="批量处理结果" :footer="false" width="720px">
    <a-alert class="mb-3">{{ task?.state }}。关闭页面会暂停处理，重新进入可继续；已成功的条目不会重复执行。</a-alert>
    <a-table :data="task?.items || []" :pagination="{ pageSize: 10 }" :columns="columns" row-key="caseId" />
  </a-modal>
</template>

<script setup lang="ts">
  import { computed, onMounted, onUnmounted, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import type { TestAssetCategory } from '@/api/modules/ai-execution';
  import {
    advanceAssetBatch,
    type AssetBatchTask,
    createAssetBatch,
    latestAssetBatch,
    retryAssetBatch,
  } from '@/api/modules/case-management/featureCase';
  import { hasAnyPermission } from '@/utils/permission';

  const props = defineProps<{ catalogId: string; ids: string[]; categories: TestAssetCategory[] }>();
  const emit = defineEmits<{ (event: 'changed'): void }>();
  const canDelete = hasAnyPermission(['CASE_ASSET:READ+DELETE']);
  const canUpdate = hasAnyPermission(['CASE_ASSET:READ+UPDATE']);
  const canCategory = hasAnyPermission(['TEST_ASSET_CATEGORY:ASSIGN']);
  const running = ref(false);
  const visible = ref(false);
  const resultVisible = ref(false);
  const action = ref('DELETE');
  const submissionId = ref('');
  const priority = ref('P1');
  const categoryId = ref<string>();
  const tags = ref<string[]>([]);
  const tagMode = ref('ADD');
  const sourceEditMode = ref('');
  const task = ref<AssetBatchTask>();
  const disabled = computed(() => !props.catalogId || !props.ids.length || running.value);
  const columns = [
    { title: '用例 ID', dataIndex: 'caseId' },
    { title: '状态', dataIndex: 'state' },
    { title: '结果 / 原因', dataIndex: 'message' },
  ];
  let disposed = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  function open(value: string) {
    submissionId.value = crypto.randomUUID();
    action.value = value;
    sourceEditMode.value = '';
    visible.value = true;
  }
  async function step() {
    if (disposed || !task.value) return;
    try {
      task.value = await advanceAssetBatch(task.value.id);
      if (task.value.state === 'PENDING') timer = setTimeout(step, 350);
      else {
        running.value = false;
        emit('changed');
      }
    } catch {
      running.value = false;
      Message.error('批量处理已暂停，可稍后继续；已提交结果保留');
    }
  }
  function resume() {
    if (running.value) return;
    running.value = true;
    step();
  }
  async function retryFailed() {
    if (!task.value || running.value) return;
    task.value = await retryAssetBatch(task.value.id);
    resume();
  }
  async function submit() {
    if (['PRIORITY', 'TAGS'].includes(action.value) && !sourceEditMode.value) {
      Message.warning('请选择来源资产处理方式');
      return false;
    }
    if (task.value?.state === 'PENDING') {
      Message.warning('请先继续现有批量任务');
      return false;
    }
    try {
      running.value = true;
      task.value = await createAssetBatch({
        catalogId: props.catalogId,
        ids: [...props.ids],
        requestId: submissionId.value,
        action: action.value,
        categoryId: categoryId.value,
        priority: priority.value,
        tags: tags.value,
        tagMode: tagMode.value,
        sourceEditMode: sourceEditMode.value,
      });
      resultVisible.value = true;
      step();
      return true;
    } catch {
      running.value = false;
      return false;
    }
  }
  onMounted(async () => {
    const latest = await latestAssetBatch();
    if (latest.id) task.value = latest as AssetBatchTask;
  });
  onUnmounted(() => {
    disposed = true;
    clearTimeout(timer);
  });
</script>
