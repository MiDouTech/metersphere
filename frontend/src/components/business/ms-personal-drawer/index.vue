<template>
  <Teleport to="body">
    <div v-if="innerVisible" class="ms-personal-page-window">
      <div class="ms-personal-page-window__mask" @click="closePersonalCenter"></div>
      <div class="ms-personal-page-window__panel" :style="{ left: `${panelLeft}px` }">
        <div class="ms-personal-page-window__header">
          <div class="text-base font-medium text-[var(--color-text-1)]">{{ t('ms.personal') }}</div>
          <button class="ms-personal-page-window__close" type="button" @click="closePersonalCenter">
            <icon-close />
          </button>
        </div>
        <div class="ms-personal-page-window__body">
          <div class="h-full w-[208px] shrink-0 bg-[var(--color-text-n9)]">
            <MsMenuPanel
              class="h-full !rounded-none bg-[var(--color-text-n9)] p-[16px_24px]"
              :default-key="activeMenu"
              :menu-list="menuList"
              active-class="!bg-transparent font-medium"
              @toggle-menu="(val: string) => (activeMenu = val)"
            />
          </div>
          <div
            :class="[
              'h-full min-w-0 flex-1 overflow-y-auto overflow-x-hidden bg-[var(--color-text-fff)]',
              activeMenu === 'modelConfig' ? 'p-0' : 'p-[24px]',
            ]"
          >
            <baseInfo v-if="activeMenu === 'baseInfo'" />
            <setPsw v-else-if="activeMenu === 'setPsw'" />
            <apiKey v-else-if="activeMenu === 'apiKey'" />
            <localExec v-else-if="activeMenu === 'local'" />
            <tripartite v-else-if="activeMenu === 'tripartite'" />
            <AgentIntegration v-else-if="activeMenu === 'agentIntegration'" compact />
            <modelConfig v-else-if="activeMenu === 'modelConfig'" model-key="personal" />
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue';
  import { useVModel } from '@vueuse/core';

  import MsMenuPanel from '@/components/pure/ms-menu-panel/index.vue';
  import apiKey from './components/apiKey.vue';
  import baseInfo from './components/baseInfo.vue';
  import localExec from './components/localExec.vue';
  import modelConfig from './components/modelConfig.vue';
  import setPsw from './components/setPsw.vue';
  import tripartite from './components/tripartite.vue';
  import AgentIntegration from '@/views/setting/system/agentIntegration/index.vue';

  import { useI18n } from '@/hooks/useI18n';
  import { useAppStore } from '@/store';
  import { hasAnyPermission } from '@/utils/permission';

  const props = defineProps<{
    visible: boolean;
  }>();
  const emit = defineEmits<{
    (e: 'update:visible', val: boolean): void;
  }>();

  const { t } = useI18n();
  const appStore = useAppStore();

  const innerVisible = useVModel(props, 'visible', emit);
  const activeMenu = ref('baseInfo');
  const panelLeft = computed(() => (appStore.menuCollapse ? 56 : 196));

  function closePersonalCenter() {
    innerVisible.value = false;
  }

  const baseMenuList = [
    {
      name: 'personal',
      title: t('ms.personal.info'),
      level: 1,
    },
    {
      name: 'baseInfo',
      title: t('ms.personal.baseInfo'),
      level: 2,
    },
    {
      name: 'setPsw',
      title: t('ms.personal.setPsw'),
      level: 2,
    },
    {
      name: 'setting',
      title: t('ms.personal.setting'),
      level: 1,
    },
    {
      name: 'apiKey',
      title: t('ms.personal.apiKey'),
      level: 2,
    },
    {
      name: 'local',
      title: t('ms.personal.localExecution'),
      level: 2,
    },
    {
      name: 'tripartite',
      title: t('ms.personal.tripartite'),
      level: 2,
    },
    {
      name: 'agentIntegration',
      title: t('ms.personal.agentIntegration'),
      level: 2,
    },
    {
      name: 'modelConfig',
      title: t('system.config.modelConfig.modelConfigSet'),
      level: 2,
    },
  ];
  const menuList = computed(() =>
    baseMenuList.filter(
      (item) => item.name !== 'agentIntegration' || hasAnyPermission(['SYSTEM_PERSONAL_AI_AGENT:READ'], ['SYSTEM'])
    )
  );
</script>

<style lang="less" scoped>
  .ms-personal-page-window {
    position: fixed;
    inset: 0;
    z-index: 1001;
  }
  .ms-personal-page-window__mask {
    position: absolute;
    inset: 0;
    background: rgb(0 0 0 / 48%);
  }
  .ms-personal-page-window__panel {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: var(--color-text-fff);
  }
  .ms-personal-page-window__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 16px;
    height: 56px;
    border-bottom: 1px solid var(--color-text-n8);
  }
  .ms-personal-page-window__close {
    display: inline-flex;
    justify-content: center;
    align-items: center;
    width: 32px;
    height: 32px;
    border: none;
    border-radius: var(--border-radius-small);
    color: var(--color-text-2);
    background: transparent;
    cursor: pointer;
    &:hover {
      background: var(--color-fill-2);
    }
  }
  .ms-personal-page-window__body {
    display: flex;
    flex: 1;
    min-width: 0;
    min-height: 0;
  }
</style>
