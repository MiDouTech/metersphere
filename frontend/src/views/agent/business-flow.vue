<template>
  <AgentPage>
    <a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert>
    <MsCard simple>
      <div class="mb-4 flex justify-between"
        ><div
          ><div class="text-base font-medium">业务流</div
          ><div class="text-sm text-[var(--color-text-3)]">版本化保存节点、边与退出条件；发布后供执行使用。</div></div
        ><a-button type="primary" @click="open()">新建</a-button></div
      >
      <a-table :data="items" :loading="loading" row-key="id" :pagination="false"
        ><template #columns
          ><a-table-column title="名称" data-index="name" /><a-table-column
            title="入口"
            data-index="entryNodeId"
          /><a-table-column title="状态" data-index="status" /><a-table-column
            title="版本"
            data-index="version"
          /><a-table-column title="操作"
            ><template #cell="{ record }"><a-link @click="open(record)">编辑</a-link></template></a-table-column
          ></template
        ></a-table
      >
    </MsCard>
    <a-modal v-model:visible="visible" title="业务流" :ok-loading="saving" width="820px" @before-ok="save"
      ><a-form :model="form" layout="vertical"
        ><a-form-item label="名称" required><a-input v-model="form.name" /></a-form-item
        ><a-form-item label="节点 JSON" required
          ><a-textarea v-model="form.nodesJson" :auto-size="{ minRows: 5, maxRows: 12 }" /></a-form-item
        ><a-form-item label="边 JSON" required
          ><a-textarea v-model="form.edgesJson" :auto-size="{ minRows: 4, maxRows: 10 }" /></a-form-item
        ><a-form-item label="入口节点 ID" required><a-input v-model="form.entryNodeId" /></a-form-item
        ><a-form-item label="退出条件 JSON" required><a-textarea v-model="form.exitJson" /></a-form-item
        ><a-form-item label="允许动作"
          ><a-select v-model="form.allowedActions" multiple
            ><a-option v-for="value in actions" :key="value" :value="value">{{ value }}</a-option></a-select
          ></a-form-item
        ><a-form-item label="状态"
          ><a-select v-model="form.status"
            ><a-option value="DRAFT">DRAFT</a-option><a-option value="PUBLISHED">PUBLISHED</a-option
            ><a-option value="DISABLED">DISABLED</a-option></a-select
          ></a-form-item
        ></a-form
      ></a-modal
    >
  </AgentPage>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import AgentPage from './components/AgentPage.vue';

  import {
    type AiBusinessFlow,
    createAiBusinessFlow,
    listAiBusinessFlows,
    updateAiBusinessFlow,
  } from '@/api/modules/ai-execution';
  import { useAppStore } from '@/store';

  const app = useAppStore();
  const items = ref<AiBusinessFlow[]>([]);
  const loading = ref(false);
  const error = ref('');
  const saving = ref(false);
  const visible = ref(false);
  const actions = ['NAVIGATE', 'CLICK', 'FILL', 'SELECT', 'WAIT', 'ASSERT', 'UPLOAD', 'KEYBOARD'];
  const empty = () => ({
    id: '',
    name: '',
    nodesJson: '[{"id":"start","name":"开始"}]',
    edgesJson: '[]',
    entryNodeId: 'start',
    exitJson: '[{"type":"SUCCESS"}]',
    allowedActions: ['NAVIGATE', 'CLICK', 'FILL', 'ASSERT'],
    status: 'DRAFT' as AiBusinessFlow['status'],
    version: 0,
  });
  const form = reactive(empty());
  async function load() {
    loading.value = true;
    error.value = '';
    try {
      items.value = await listAiBusinessFlows(app.currentProjectId);
    } catch (reason: any) {
      error.value = reason?.message || '业务流加载失败，请稍后重试';
    } finally {
      loading.value = false;
    }
  }
  function open(value?: AiBusinessFlow) {
    Object.assign(
      form,
      value
        ? {
            id: value.id,
            name: value.name,
            nodesJson: JSON.stringify(value.nodes, null, 2),
            edgesJson: JSON.stringify(value.edges, null, 2),
            entryNodeId: value.entryNodeId,
            exitJson: JSON.stringify(value.exitConditions, null, 2),
            allowedActions: [...value.allowedActions],
            status: value.status,
            version: value.version,
          }
        : empty()
    );
    visible.value = true;
  }
  async function save(done: (closed: boolean) => void) {
    saving.value = true;
    try {
      const data = {
        projectId: app.currentProjectId,
        name: form.name.trim(),
        nodes: JSON.parse(form.nodesJson),
        edges: JSON.parse(form.edgesJson),
        entryNodeId: form.entryNodeId.trim(),
        exitConditions: JSON.parse(form.exitJson),
        allowedActions: form.allowedActions,
        status: form.status,
        version: form.version,
      };
      if (form.id) await updateAiBusinessFlow(form.id, data);
      else await createAiBusinessFlow(data);
      Message.success('保存成功');
      await load();
      done(true);
    } catch (reason) {
      Message.error(reason instanceof SyntaxError ? 'JSON 格式错误' : '保存失败');
      done(false);
    } finally {
      saving.value = false;
    }
  }
  onMounted(load);
</script>
