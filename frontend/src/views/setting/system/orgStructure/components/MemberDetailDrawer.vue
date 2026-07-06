<template>
  <MsDrawer
    :visible="visible"
    :width="480"
    :title="t('orgStructure.member.detail')"
    :footer="false"
    unmount-on-close
    @cancel="handleClose"
  >
    <a-spin class="w-full" :loading="loading">
      <a-descriptions v-if="detail" :column="1" bordered size="large">
        <a-descriptions-item :label="t('orgStructure.member.name')">{{ detail.name || '-' }}</a-descriptions-item>
        <a-descriptions-item :label="t('orgStructure.member.email')">{{ detail.email || '-' }}</a-descriptions-item>
        <a-descriptions-item :label="t('orgStructure.member.phone')">{{ detail.phone || '-' }}</a-descriptions-item>
        <a-descriptions-item :label="t('orgStructure.member.wecomUserid')">{{
          detail.wecomUserid || '-'
        }}</a-descriptions-item>
        <a-descriptions-item :label="t('orgStructure.member.department')">{{
          detail.departmentName || '-'
        }}</a-descriptions-item>
        <a-descriptions-item :label="t('orgStructure.member.position')">{{
          detail.position || '-'
        }}</a-descriptions-item>
        <a-descriptions-item :label="t('orgStructure.member.status')">
          {{ detail.enable ? t('orgStructure.member.status.enable') : t('orgStructure.member.status.disable') }}
        </a-descriptions-item>
        <a-descriptions-item :label="t('orgStructure.member.syncStatus')">
          {{ getMemberSyncStatusLabel(detail.syncStatus) }}
        </a-descriptions-item>
        <a-descriptions-item :label="t('orgStructure.member.syncTime')">
          {{ formatTime(detail.syncTime) }}
        </a-descriptions-item>
      </a-descriptions>
      <a-empty v-else-if="!loading" />
    </a-spin>
  </MsDrawer>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue';
  import dayjs from 'dayjs';

  import MsDrawer from '@/components/pure/ms-drawer/index.vue';

  import { getMemberDetail } from '@/api/modules/setting/orgStructure';
  import { useI18n } from '@/hooks/useI18n';

  import type { OrgStructureMemberDetail } from '@/models/setting/orgStructure';

  import { MEMBER_SYNC_STATUS } from '../config';

  const props = defineProps<{
    visible: boolean;
    memberId: string;
    organizationId: string;
  }>();

  const emit = defineEmits<{
    (e: 'update:visible', value: boolean): void;
  }>();

  const { t } = useI18n();
  const loading = ref(false);
  const detail = ref<OrgStructureMemberDetail>();

  function formatTime(time?: number) {
    return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-';
  }

  function getMemberSyncStatusLabel(status?: number) {
    if (status === MEMBER_SYNC_STATUS.SYNCED) {
      return t('orgStructure.member.syncStatus.synced');
    }
    if (status === MEMBER_SYNC_STATUS.FAILED) {
      return t('orgStructure.member.syncStatus.failed');
    }
    return t('orgStructure.member.syncStatus.notSynced');
  }

  async function loadDetail() {
    if (!props.visible || !props.memberId || !props.organizationId) {
      return;
    }
    try {
      loading.value = true;
      detail.value = await getMemberDetail(props.memberId, props.organizationId);
    } finally {
      loading.value = false;
    }
  }

  function handleClose() {
    emit('update:visible', false);
  }

  watch(
    () => [props.visible, props.memberId, props.organizationId],
    () => {
      if (props.visible) {
        loadDetail();
      } else {
        detail.value = undefined;
      }
    }
  );
</script>
