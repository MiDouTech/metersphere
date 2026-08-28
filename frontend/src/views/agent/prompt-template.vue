<template
  ><AgentPage
    ><MsCard simple
      ><div class="mb-4 flex items-center justify-between"
        ><div
          ><div class="text-base font-medium">Prompt 模板版本</div
          ><div class="text-sm text-[var(--color-text-3)]"
            >版本不可修改；新版本发布后自动归档同模板的旧发布版本。</div
          ></div
        ><a-space
          ><a-button :loading="loading" @click="load">刷新</a-button
          ><a-button v-permission="['AI_MODEL:MANAGE']" type="primary" @click="open">新建版本</a-button></a-space
        ></div
      ><a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert
      ><a-table :data="items" :loading="loading" :pagination="false" row-key="id"
        ><template #empty><a-empty description="暂无 Prompt 模板" /></template
        ><template #columns
          ><a-table-column title="模板 ID" data-index="promptTemplateId" /><a-table-column
            title="名称"
            data-index="name"
          /><a-table-column title="版本" data-index="versionNo" /><a-table-column
            title="输出协议"
            data-index="outputSchemaVersion"
          /><a-table-column title="状态"
            ><template #cell="{ record }"
              ><a-tag
                :color="record.status === 'PUBLISHED' ? 'green' : record.status === 'DRAFT' ? 'orange' : 'gray'"
                >{{ record.status }}</a-tag
              ></template
            ></a-table-column
          ><a-table-column title="Hash" data-index="contentHash" /><a-table-column title="操作"
            ><template #cell="{ record }"
              ><a-button
                v-if="record.status === 'DRAFT'"
                v-permission="['AI_MODEL:MANAGE']"
                type="text"
                @click="publish(record)"
                >发布</a-button
              ></template
            ></a-table-column
          ></template
        ></a-table
      ></MsCard
    ><a-modal v-model:visible="visible" title="新建 Prompt 版本" :ok-loading="saving" width="760px" @before-ok="save"
      ><a-form :model="form" layout="vertical"
        ><a-form-item label="模板 ID" extra="留空创建新模板；填写已有模板 ID 创建下一版本"
          ><a-input v-model="form.promptTemplateId" /></a-form-item
        ><a-form-item label="名称" required><a-input v-model="form.name" /></a-form-item
        ><a-form-item label="系统模板" required
          ><a-textarea v-model="form.systemTemplate" :auto-size="{ minRows: 4, maxRows: 8 }" /></a-form-item
        ><a-form-item label="业务模板" required
          ><a-textarea v-model="form.businessTemplate" :auto-size="{ minRows: 4, maxRows: 8 }" /></a-form-item
        ><a-form-item label="变量 JSON Schema" required
          ><a-textarea v-model="form.variableSchema" :auto-size="{ minRows: 3, maxRows: 8 }" /></a-form-item
        ><a-form-item label="输出 Schema 版本" required
          ><a-input v-model="form.outputSchemaVersion" /></a-form-item></a-form></a-modal></AgentPage
></template>

<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import AgentPage from './components/AgentPage.vue';

  import type { AiPromptTemplateVersion } from '@/api/modules/ai-execution';
  import {
    createAiPromptTemplateVersion,
    listAiPromptTemplateVersions,
    publishAiPromptTemplateVersion,
  } from '@/api/modules/ai-execution';
  import useAppStore from '@/store/modules/app';

  const app = useAppStore();
  const items = ref<AiPromptTemplateVersion[]>([]);
  const loading = ref(false);
  const saving = ref(false);
  const visible = ref(false);
  const error = ref('');
  const form = reactive({
    promptTemplateId: '',
    name: '',
    systemTemplate: '',
    businessTemplate: '',
    variableSchema: '{"type":"object","additionalProperties":false,"properties":{}}',
    outputSchemaVersion: 'v1',
  });
  const msg = (e: unknown) => (e as { message?: string })?.message || '请求失败';
  async function load() {
    loading.value = true;
    error.value = '';
    try {
      items.value = await listAiPromptTemplateVersions(app.currentProjectId);
    } catch (e) {
      error.value = msg(e);
    } finally {
      loading.value = false;
    }
  }
  function open() {
    Object.assign(form, {
      promptTemplateId: '',
      name: '',
      systemTemplate: '',
      businessTemplate: '',
      variableSchema: '{"type":"object","additionalProperties":false,"properties":{}}',
      outputSchemaVersion: 'v1',
    });
    visible.value = true;
  }
  async function save(done: (v: boolean) => void) {
    if (
      !form.name.trim() ||
      !form.systemTemplate.trim() ||
      !form.businessTemplate.trim() ||
      !form.variableSchema.trim() ||
      !form.outputSchemaVersion.trim()
    ) {
      Message.warning('请填写全部必填字段');
      done(false);
      return;
    }
    try {
      JSON.parse(form.variableSchema);
    } catch {
      Message.warning('变量 Schema 必须是合法 JSON');
      done(false);
      return;
    }
    saving.value = true;
    try {
      await createAiPromptTemplateVersion({
        projectId: app.currentProjectId,
        promptTemplateId: form.promptTemplateId.trim() || undefined,
        name: form.name.trim(),
        systemTemplate: form.systemTemplate,
        businessTemplate: form.businessTemplate,
        variableSchema: form.variableSchema,
        outputSchemaVersion: form.outputSchemaVersion.trim(),
      });
      await load();
      Message.success('草稿版本已创建');
      done(true);
    } catch (e) {
      Message.error(msg(e));
      done(false);
    } finally {
      saving.value = false;
    }
  }
  async function publish(r: AiPromptTemplateVersion) {
    try {
      await publishAiPromptTemplateVersion(r.id, app.currentProjectId);
      await load();
      Message.success('发布成功');
    } catch (e) {
      Message.error(msg(e));
    }
  }
  onMounted(load);
</script>
