<template>
  <section>
    <a-alert class="mb-4">以下配置对当前平台生效，不区分项目；业务记录按原有权限访问。</a-alert>
    <a-tabs v-model:active-key="section">
      <a-tab-pane v-if="userStore.isAdmin" key="webhook" title="Webhook测试计划推送">
        <Webhook />
      </a-tab-pane>
      <a-tab-pane v-if="canWecom" key="wecom" title="企微智能机器人">
        <Wecom />
      </a-tab-pane>
      <a-tab-pane v-if="userStore.isAdmin" key="messages" title="消息管理">
        <TemplateEditor v-if="route.query.notificationTemplate === 'true'" :key="route.fullPath" />
        <template v-else>
          <a-alert class="mb-4"
            >无自定义配置不表示内置通知关闭；原六类@与回复通知由业务事件决定接收人，站内信机器人停用后不产生新通知。</a-alert
          >
          <Messages />
        </template>
      </a-tab-pane>
    </a-tabs>
  </section>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';

  import Webhook from '@/components/business/hifox-handoff/configuration.vue';
  import Wecom from './wecomBotConfig.vue';
  import TemplateEditor from '@/views/project-management/messageManagement/edit.vue';
  import Messages from '@/views/project-management/messageManagement/index.vue';

  import useUserStore from '@/store/modules/user';
  import { hasAnyPermission, hasTabVisible } from '@/utils/permission';

  const userStore = useUserStore();
  const route = useRoute();
  const canWecom = computed(
    () =>
      hasAnyPermission(
        ['SYSTEM_CONFIG_WECOM_BOT:READ', 'SYSTEM_NOTIFICATION_RULE:READ', 'SYSTEM_NOTIFICATION_LOG:READ'],
        ['SYSTEM']
      ) && hasTabVisible('SYSTEM_CONFIG_WECOM_BOT_TAB', ['SYSTEM'])
  );
  const section = ref('');
  watch(
    () => route.query.notificationSection,
    (value) => {
      if (!userStore.isAdmin) section.value = 'wecom';
      else section.value = value === 'messages' ? 'messages' : 'webhook';
    },
    { immediate: true }
  );
</script>
