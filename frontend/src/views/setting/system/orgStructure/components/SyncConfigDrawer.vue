<template>
  <MsDrawer
    :visible="visible"
    :width="560"
    :title="t('orgStructure.config.title')"
    :ok-loading="saving"
    :ok-permission="['SYSTEM_ORGANIZATION_PROJECT:READ+UPDATE', 'ORGANIZATION_MEMBER:READ+UPDATE']"
    unmount-on-close
    @cancel="handleClose"
  >
    <a-alert class="mb-[16px]" type="info">
      {{ t('orgStructure.config.tip') }}
    </a-alert>
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-form-item
        field="corpId"
        :label="t('orgStructure.config.corpId')"
        :rules="[{ required: true, message: t('orgStructure.config.corpIdRequired') }]"
        asterisk-position="end"
      >
        <a-input v-model="form.corpId" :max-length="100" :placeholder="t('formCreate.PleaseEnter')" />
      </a-form-item>
      <a-form-item
        field="contactSecret"
        :label="t('orgStructure.config.contactSecret')"
        :rules="contactSecretRules"
        asterisk-position="end"
      >
        <a-input-password
          v-model="form.contactSecret"
          allow-clear
          :max-length="255"
          :placeholder="t('orgStructure.config.contactSecretPlaceholder')"
        />
        <template #extra>
          <span class="text-[12px] text-[var(--color-text-4)]">{{ t('orgStructure.config.contactSecretTip') }}</span>
        </template>
      </a-form-item>
      <a-form-item field="scheduleEnabled" :label="t('orgStructure.config.scheduleEnabled')">
        <a-switch v-model="form.scheduleEnabled" size="small" />
      </a-form-item>
      <a-form-item
        v-if="form.scheduleEnabled"
        field="scheduleCron"
        :label="t('orgStructure.config.scheduleCron')"
        :rules="[{ required: true, message: t('orgStructure.config.scheduleCronRequired') }]"
        asterisk-position="end"
      >
        <div class="flex w-full items-center gap-[8px]">
          <MsCronSelect v-model:model-value="form.scheduleCron" class="flex-1" />
          <a-link
            href="https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html"
            target="_blank"
          >
            {{ t('orgStructure.config.cronHelp') }}
          </a-link>
        </div>
      </a-form-item>
      <a-form-item field="retryTimes" :label="t('orgStructure.config.retryTimes')">
        <a-input-number v-model="form.retryTimes" :min="0" :max="10" :step="1" mode="button" />
      </a-form-item>
    </a-form>
    <template #footer>
      <div class="flex w-full items-center justify-between">
        <a-button
          v-permission="['SYSTEM_ORGANIZATION_PROJECT:READ+UPDATE', 'ORGANIZATION_MEMBER:READ+UPDATE']"
          type="outline"
          :loading="testing"
          :disabled="!organizationId"
          @click="handleTest"
        >
          {{ t('organization.service.testLink') }}
        </a-button>
        <div class="flex gap-[12px]">
          <a-button @click="handleClose">{{ t('common.cancel') }}</a-button>
          <a-button
            v-permission="['SYSTEM_ORGANIZATION_PROJECT:READ+UPDATE', 'ORGANIZATION_MEMBER:READ+UPDATE']"
            type="primary"
            :loading="saving"
            @click="handleSave"
          >
            {{ t('common.confirm') }}
          </a-button>
        </div>
      </div>
    </template>
  </MsDrawer>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import MsCronSelect from '@/components/pure/ms-cron-select/index.vue';
  import MsDrawer from '@/components/pure/ms-drawer/index.vue';

  import { getSyncConfig, saveSyncConfig, testSyncConfig } from '@/api/modules/setting/orgStructure';
  import { useI18n } from '@/hooks/useI18n';

  import type { OrgWecomSyncConfig } from '@/models/setting/orgStructure';

  import type { FieldRule, FormInstance } from '@arco-design/web-vue';

  const props = defineProps<{
    visible: boolean;
    organizationId: string;
  }>();

  const emit = defineEmits<{
    (e: 'update:visible', value: boolean): void;
    (e: 'saved'): void;
  }>();

  const { t } = useI18n();
  const formRef = ref<FormInstance>();
  const saving = ref(false);
  const testing = ref(false);
  const configured = ref(false);

  const form = ref({
    corpId: '',
    contactSecret: '',
    scheduleEnabled: false,
    scheduleCron: '0 0 2 * * ?',
    retryTimes: 3,
  });

  const contactSecretRules = computed<FieldRule[]>(() => [
    {
      required: !configured.value,
      message: t('orgStructure.config.contactSecretRequired'),
    },
  ]);

  async function loadConfig() {
    if (!props.visible || !props.organizationId) {
      return;
    }
    const config: OrgWecomSyncConfig = await getSyncConfig(props.organizationId);
    configured.value = !!config.configured;
    form.value = {
      corpId: config.corpId || '',
      contactSecret: config.contactSecret || '',
      scheduleEnabled: !!config.scheduleEnabled,
      scheduleCron: config.scheduleCron || '0 0 2 * * ?',
      retryTimes: config.retryTimes ?? 3,
    };
  }

  function handleClose() {
    emit('update:visible', false);
  }

  async function handleTest() {
    if (!props.organizationId) {
      return;
    }
    const validateResult = await formRef.value?.validateField(['corpId', 'contactSecret']);
    if (validateResult) {
      return;
    }
    try {
      testing.value = true;
      const result = await testSyncConfig({
        organizationId: props.organizationId,
        corpId: form.value.corpId,
        contactSecret: form.value.contactSecret,
      });
      if (result.success) {
        Message.success(`${result.message || t('orgStructure.config.testSuccess')} (${result.deptCount ?? 0})`);
      } else {
        Message.error(result.message || t('orgStructure.config.testFailed'));
      }
    } finally {
      testing.value = false;
    }
  }

  async function handleSave() {
    const validateResult = await formRef.value?.validate();
    if (validateResult) {
      return;
    }
    if (!configured.value && !form.value.contactSecret) {
      Message.error(t('orgStructure.config.contactSecretRequired'));
      return;
    }
    try {
      saving.value = true;
      const payload = {
        organizationId: props.organizationId,
        corpId: form.value.corpId,
        contactSecret: form.value.contactSecret,
        scheduleEnabled: form.value.scheduleEnabled,
        scheduleCron: form.value.scheduleEnabled ? form.value.scheduleCron : undefined,
        retryTimes: form.value.retryTimes,
      };
      await saveSyncConfig(payload);
      Message.success(t('common.saveSuccess'));
      emit('saved');
      handleClose();
    } finally {
      saving.value = false;
    }
  }

  watch(
    () => [props.visible, props.organizationId],
    () => {
      if (props.visible) {
        loadConfig();
      }
    }
  );
</script>
