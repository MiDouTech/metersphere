<template
  ><AgentPage
    ><MsCard simple
      ><div class="mb-4 flex justify-between"
        ><div
          ><div class="text-base font-medium">Page Object</div
          ><div class="text-sm text-[var(--color-text-3)]">复用经过治理的语义定位器；禁止 XPath 和脚本定位。</div></div
        ><a-space
          ><a-button :loading="loading" @click="load">刷新</a-button
          ><a-button v-permission="['AI_EXECUTION:RUN']" type="primary" @click="open()">新增</a-button></a-space
        ></div
      ><a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert
      ><a-table :data="items" :loading="loading" :pagination="false"
        ><template #empty><a-empty description="暂无 Page Object" /></template
        ><template #columns
          ><a-table-column title="名称" data-index="name" /><a-table-column
            title="路由"
            data-index="routePattern"
          /><a-table-column title="状态" data-index="status" /><a-table-column title="元素数"
            ><template #cell="{ record }">{{ record.elements.length }}</template></a-table-column
          ><a-table-column title="版本" data-index="version" /><a-table-column title="操作"
            ><template #cell="{ record }"
              ><a-button type="text" @click="open(record)">编辑</a-button></template
            ></a-table-column
          ></template
        ></a-table
      ></MsCard
    ><a-modal
      v-model:visible="visible"
      :title="form.id ? '编辑 Page Object' : '新增 Page Object'"
      width="820px"
      :ok-loading="saving"
      @before-ok="save"
      ><a-form :model="form" layout="vertical"
        ><a-form-item label="名称" required><a-input v-model="form.name" /></a-form-item
        ><a-form-item label="路由模式"><a-input v-model="form.routePattern" placeholder="/orders/:id" /></a-form-item
        ><a-form-item label="允许 Origin" required extra="每行一个 HTTPS Origin"
          ><a-textarea v-model="form.allowedOrigins" /></a-form-item
        ><a-form-item label="状态"
          ><a-select v-model="form.status"
            ><a-option value="DRAFT">DRAFT</a-option><a-option value="PUBLISHED">PUBLISHED</a-option
            ><a-option value="DISABLED">DISABLED</a-option></a-select
          ></a-form-item
        ><a-form-item
          label="元素 JSON"
          required
          extra="数组字段：name,strategy,selectorValue,fallbackLocators?,sensitive,riskLevel；ROLE 的 selectorValue 格式为 role|name"
          ><a-textarea
            v-model="form.elementsJson"
            :auto-size="{ minRows: 10, maxRows: 18 }" /></a-form-item></a-form></a-modal></AgentPage
></template>

<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import AgentPage from './components/AgentPage.vue';

  import type { AiPageElement, AiPageObject, AiPageObjectRequest } from '@/api/modules/ai-execution';
  import { createAiPageObject, listAiPageObjects, updateAiPageObject } from '@/api/modules/ai-execution';
  import useAppStore from '@/store/modules/app';

  const app = useAppStore();
  const items = ref<AiPageObject[]>([]);
  const loading = ref(false);
  const saving = ref(false);
  const visible = ref(false);
  const error = ref('');
  const example =
    '[{"name":"登录按钮","strategy":"ROLE","selectorValue":"button|登录","sensitive":false,"riskLevel":"LOW"}]';
  const form = reactive({
    id: '',
    name: '',
    routePattern: '',
    allowedOrigins: '',
    status: 'DRAFT' as AiPageObject['status'],
    version: 0,
    elementsJson: example,
  });
  const msg = (e: unknown) => (e as { message?: string })?.message || '请求失败';
  async function load() {
    loading.value = true;
    error.value = '';
    try {
      items.value = await listAiPageObjects(app.currentProjectId);
    } catch (e) {
      error.value = msg(e);
    } finally {
      loading.value = false;
    }
  }
  function open(r?: AiPageObject) {
    Object.assign(
      form,
      r
        ? {
            id: r.id,
            name: r.name,
            routePattern: r.routePattern || '',
            allowedOrigins: r.allowedOrigins.join('\n'),
            status: r.status,
            version: r.version,
            elementsJson: JSON.stringify(r.elements, null, 2),
          }
        : { id: '', name: '', routePattern: '', allowedOrigins: '', status: 'DRAFT', version: 0, elementsJson: example }
    );
    visible.value = true;
  }
  async function save(done: (v: boolean) => void) {
    let elements: AiPageElement[];
    try {
      elements = JSON.parse(form.elementsJson);
      if (!Array.isArray(elements) || !elements.length) throw new Error();
    } catch {
      Message.warning('元素 JSON 必须是非空数组');
      done(false);
      return;
    }
    const origins = form.allowedOrigins
      .split(/\r?\n/)
      .map((x) => x.trim())
      .filter(Boolean);
    if (!form.name.trim() || !origins.length) {
      Message.warning('请填写名称和允许 Origin');
      done(false);
      return;
    }
    const data: AiPageObjectRequest = {
      projectId: app.currentProjectId,
      name: form.name.trim(),
      routePattern: form.routePattern.trim() || undefined,
      allowedOrigins: origins,
      status: form.status,
      version: form.version,
      elements,
    };
    saving.value = true;
    try {
      if (form.id) await updateAiPageObject(form.id, data);
      else await createAiPageObject(data);
      await load();
      Message.success('保存成功');
      done(true);
    } catch (e) {
      Message.error(msg(e));
      done(false);
    } finally {
      saving.value = false;
    }
  }
  onMounted(load);
</script>
