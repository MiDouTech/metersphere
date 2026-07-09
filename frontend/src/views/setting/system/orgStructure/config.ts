import type { MsTableColumn } from '@/components/pure/ms-table/type';

export const MEMBER_SYNC_STATUS = {
  NOT_SYNCED: 0,
  SYNCED: 1,
  FAILED: 2,
} as const;

export const SYNC_LOG_STATUS = {
  SUCCESS: 'SUCCESS',
  PARTIAL: 'PARTIAL',
  FAILED: 'FAILED',
} as const;

export const SYNC_MODE = {
  MANUAL: 'MANUAL',
  SCHEDULE: 'SCHEDULE',
  LOGIN: 'LOGIN',
} as const;

export const memberSyncStatusOptions = [
  { label: 'orgStructure.member.syncStatus.notSynced', value: MEMBER_SYNC_STATUS.NOT_SYNCED },
  { label: 'orgStructure.member.syncStatus.synced', value: MEMBER_SYNC_STATUS.SYNCED },
  { label: 'orgStructure.member.syncStatus.failed', value: MEMBER_SYNC_STATUS.FAILED },
];

export const enableStatusOptions = [
  { label: 'orgStructure.member.status.enable', value: true },
  { label: 'orgStructure.member.status.disable', value: false },
];

export const syncLogStatusOptions = [
  { label: 'orgStructure.sync.status.success', value: SYNC_LOG_STATUS.SUCCESS },
  { label: 'orgStructure.sync.status.partial', value: SYNC_LOG_STATUS.PARTIAL },
  { label: 'orgStructure.sync.status.failed', value: SYNC_LOG_STATUS.FAILED },
];

export const memberTableColumns: MsTableColumn = [
  {
    title: 'orgStructure.member.name',
    dataIndex: 'name',
    slotName: 'name',
    width: 160,
    showTooltip: true,
  },
  {
    title: 'orgStructure.member.department',
    dataIndex: 'departmentName',
    showTooltip: true,
  },
  {
    title: 'orgStructure.member.position',
    dataIndex: 'position',
    width: 140,
    showTooltip: true,
  },
  {
    title: 'orgStructure.member.status',
    dataIndex: 'enable',
    slotName: 'enable',
    width: 100,
  },
  {
    title: 'orgStructure.member.syncStatus',
    dataIndex: 'syncStatus',
    slotName: 'syncStatus',
    width: 120,
  },
  {
    title: 'orgStructure.member.syncTime',
    dataIndex: 'syncTime',
    slotName: 'syncTime',
    width: 180,
  },
];

export const syncLogTableColumns: MsTableColumn = [
  {
    title: 'orgStructure.sync.log.time',
    dataIndex: 'createTime',
    slotName: 'createTime',
    width: 180,
  },
  {
    title: 'orgStructure.sync.log.mode',
    dataIndex: 'syncMode',
    slotName: 'syncMode',
    width: 100,
  },
  {
    title: 'orgStructure.sync.log.status',
    dataIndex: 'syncStatus',
    slotName: 'syncStatus',
    width: 100,
  },
  {
    title: 'orgStructure.sync.log.dept',
    dataIndex: 'deptStats',
    slotName: 'deptStats',
    width: 140,
  },
  {
    title: 'orgStructure.sync.log.user',
    dataIndex: 'userStats',
    slotName: 'userStats',
    width: 140,
  },
  {
    title: 'orgStructure.sync.log.duration',
    dataIndex: 'durationMs',
    slotName: 'durationMs',
    width: 100,
  },
  {
    title: 'orgStructure.sync.log.error',
    dataIndex: 'errorMessage',
    showTooltip: true,
    width: 200,
  },
];
