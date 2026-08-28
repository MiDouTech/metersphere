<template>
  <a-modal
    :visible="visible"
    :title="form.id ? '编辑调度规则' : '新建调度规则'"
    :ok-loading="saving"
    width="720px"
    unmount-on-close
    @update:visible="$emit('update:visible', $event)"
    @before-ok="$emit('save', $event)"
  >
    <a-form :model="form" layout="vertical">
      <div class="grid grid-cols-2 gap-x-4">
        <a-form-item label="规则名称" required><a-input v-model="form.name" /></a-form-item>
        <a-form-item label="触发类型" required
          ><a-select v-model="form.triggerType" :disabled="!!form.id"
            ><a-option value="CRON">Cron 定时</a-option><a-option value="EVENT">Webhook 事件</a-option
            ><a-option value="MANUAL">仅手动</a-option></a-select
          ></a-form-item
        >
        <a-form-item v-if="form.triggerType === 'CRON'" label="Quartz Cron" required
          ><a-input v-model="form.cronExpression" placeholder="0 0 2 * * ?"
        /></a-form-item>
        <a-form-item v-if="form.triggerType === 'CRON'" label="时区" required
          ><a-input v-model="form.timezone"
        /></a-form-item>
        <a-form-item v-if="form.triggerType === 'EVENT'" label="事件类型" required
          ><a-input v-model="form.eventType" placeholder="CI_BUILD_COMPLETED"
        /></a-form-item>
        <a-form-item v-if="form.triggerType === 'EVENT'" label="事件过滤 JSON"
          ><a-input v-model="form.eventFilter" placeholder='{"branch":"main"}'
        /></a-form-item>
        <a-form-item label="并发策略"
          ><a-select v-model="form.concurrencyPolicy"
            ><a-option value="FORBID">禁止重叠</a-option><a-option value="ALLOW">允许并发</a-option></a-select
          ></a-form-item
        >
        <a-form-item label="错过执行"
          ><a-select v-model="form.missedPolicy"
            ><a-option value="FIRE_ONCE">补执行一次</a-option><a-option value="SKIP">跳过</a-option></a-select
          ></a-form-item
        >
        <a-form-item label="任务名称" required><a-input v-model="form.taskName" /></a-form-item
        ><a-form-item label="执行通道"><a-input model-value="平台模型执行器" disabled /></a-form-item>
        <a-form-item label="环境执行配置" required
          ><a-select v-model="form.environmentProfileId" @change="$emit('environmentChange')"
            ><a-option v-for="item in environmentProfiles" :key="item.id" :value="item.id"
              >{{ item.name }} · {{ item.baseUrl }}</a-option
            ></a-select
          ></a-form-item
        >
        <a-form-item label="MAP Gateway 模型配置" required
          ><a-select v-model="form.modelProfileId"
            ><a-option v-for="item in modelProfiles" :key="item.id" :value="item.id"
              >{{ item.name }} · {{ item.logicalModelPublicId }}</a-option
            ></a-select
          ></a-form-item
        >
        <a-form-item label="已发布 Prompt 模板" required
          ><a-select v-model="form.promptTemplateId"
            ><a-option v-for="item in promptTemplates" :key="item.id" :value="item.promptTemplateId"
              >{{ item.name }} · v{{ item.versionNo }}</a-option
            ></a-select
          ></a-form-item
        >
        <a-form-item label="凭据引用"
          ><a-select v-model="form.credentialReferenceId" allow-clear
            ><a-option v-for="item in credentialReferences" :key="item.id" :value="item.id"
              >{{ item.name }} · {{ item.businessRole }}</a-option
            ></a-select
          ></a-form-item
        >
        <a-form-item label="Runner 类型" required
          ><a-select v-model="form.runnerType"
            ><a-option value="BROWSER">BROWSER</a-option><a-option value="API">API</a-option></a-select
          ></a-form-item
        >
      </div>
      <a-form-item label="用例 ID" required extra="规则最终只保存当前项目用例 ID；保存前执行 Preflight。"
        ><div class="w-full"
          ><a-textarea v-model="form.caseIds" :auto-size="{ minRows: 3, maxRows: 6 }" /><div class="mt-2"
            ><a-button size="small" @click="$emit('selectAssets')">从用例资产导入</a-button></div
          ></div
        ></a-form-item
      >
      <a-form-item label="任务目标"
        ><a-textarea v-model="form.objective" :auto-size="{ minRows: 2, maxRows: 4 }"
      /></a-form-item>
      <a-form-item label="三位责任人用户 ID" required extra="同时通知三人；任一合法责任人响应后即完成。"
        ><a-textarea
          v-model="form.responsibleUserIds"
          placeholder="每行一个用户 ID"
          :auto-size="{ minRows: 3, maxRows: 3 }"
      /></a-form-item>
      <a-form-item label="Runner 必需能力"
        ><a-select v-model="form.requiredCapabilities" multiple allow-create
          ><a-option value="BROWSER">BROWSER</a-option><a-option value="SCREENSHOT">SCREENSHOT</a-option
          ><a-option value="VIDEO">VIDEO</a-option></a-select
        ></a-form-item
      >
      <a-form-item label="启用"><a-switch v-model="form.enabled" /></a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
  import { computed } from 'vue';

  import type {
    AiCredentialReference,
    AiEnvironmentProfile,
    AiModelProfile,
    AiPromptTemplateVersion,
    AiTaskTrigger,
  } from '@/api/modules/ai-execution';

  interface FormModel {
    id: string;
    version?: number;
    name: string;
    triggerType: AiTaskTrigger['triggerType'];
    cronExpression: string;
    timezone: string;
    eventType: string;
    eventFilter: string;
    concurrencyPolicy: 'FORBID' | 'ALLOW';
    missedPolicy: 'SKIP' | 'FIRE_ONCE';
    enabled: boolean;
    taskName: string;
    caseIds: string;
    objective: string;
    environmentProfileId: string;
    credentialReferenceId: string;
    modelProfileId: string;
    promptTemplateId: string;
    runnerType: string;
    requiredCapabilities: string[];
    responsibleUserIds: string;
  }
  const props = defineProps<{
    visible: boolean;
    saving: boolean;
    model: FormModel;
    environmentProfiles: AiEnvironmentProfile[];
    credentialReferences: AiCredentialReference[];
    modelProfiles: AiModelProfile[];
    promptTemplates: AiPromptTemplateVersion[];
  }>();
  const form = computed(() => props.model);
  defineEmits<{
    'update:visible': [boolean];
    'save': [(closed: boolean) => void];
    'environmentChange': [];
    'selectAssets': [];
  }>();
</script>
