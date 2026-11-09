<template>
  <MsCard
    ref="fullRef"
    :special-height="127"
    show-full-screen
    hide-back
    hide-footer
    @toggle-full-screen="handleToggleFullScreen"
  >
    <template #headerLeft>
      <div class="font-medium text-[var(--color-text-1)]">{{ t('project.messageManagement.config') }}</div>
    </template>
    <template #headerRight>
      <MsSelect
        v-model:model-value="robotFilters"
        :options="robotOptions"
        :allow-search="false"
        allow-clear
        class="mr-[8px] !w-[240px]"
        :prefix="t('project.messageManagement.robot')"
        :multiple="true"
        :has-all-select="true"
        :default-all-select="true"
      >
        <template #footer>
          <div class="mb-[6px] mt-[4px] p-[3px_8px]">
            <MsButton v-permission="['PROJECT_MESSAGE:READ+ADD']" type="text" @click="emit('createRobot')">
              <MsIcon type="icon-icon_add_outlined" class="mr-[8px] text-[rgb(var(--primary-6))]" size="14" />
              {{ t('project.messageManagement.createBot') }}
            </MsButton>
          </div>
        </template>
      </MsSelect>
    </template>
    <a-alert v-if="readError" type="error">读取失败，请重试 <a-button @click="initialize">重试</a-button></a-alert>
    <ms-base-table
      ref="tableRef"
      v-bind="propsRes"
      v-model:expandedKeys="expandedKeys"
      no-disable
      :indent-size="0"
      v-on="propsEvent"
    >
      <template #name="{ record }">
        <span class="font-medium text-[var(--color-text-1)]">{{ record.name }}</span>
      </template>
      <template #eventName="{ record }">
        <span>{{ record.eventName || '' }}</span>
      </template>
      <template #receiver="{ record, dataIndex }">
        <MsSelect
          v-if="!record.children && hasAnyPermission(['PROJECT_MESSAGE:READ+ADD'])"
          v-model:model-value="record.receivers"
          v-model:loading="record.loading"
          :disabled="initializing || readError || propsRes.loading || record.loading || receiverPending.has(record.key)"
          class="w-full"
          mode="remote"
          :options="defaultReceivers"
          :remote-filter-func="(opts) => getReceiverOptions(opts, record.event, record.taskType)"
          :search-keys="['label']"
          allow-search
          :at-least-one="true"
          value-key="id"
          label-key="name"
          :multiple="true"
          :placeholder="t('project.messageManagement.receiverPlaceholder')"
          :remote-extra-params="{ projectId: '__MSP_PLATFORM_NOTIFICATIONS__' }"
          :remote-func="getMessageUserList"
          :remote-fields-map="{ label: 'name', value: 'id', id: 'id' }"
          :not-auto-init-search="true"
          :fallback-option="(val) => ({
              label: (val as Record<string, any>).name,
              value: val,
            })"
          :object-value="true"
          @update:model-value="updateReceiverDraft(record, $event)"
          @remove="changeMessageReceivers(false, record, dataIndex as string)"
          @popup-visible-change="changeMessageReceivers($event, record, dataIndex as string)"
        />
        <MsTagGroup
          v-else-if="!record.children && hasAnyPermission(['PROJECT_MESSAGE:READ'])"
          is-string-tag
          :tag-list="record.receivers?.map((e: Record<string,any>) => e.name) || []"
          theme="outline"
        />
        <span v-else></span>
        <div v-if="receiverDrafts.has(record.key)" role="alert" class="text-[var(--color-danger-6)]">
          接收人保存失败，当前为未保存草稿。
          <a-button
            type="text"
            :disabled="
              record.loading || initializing || readError || propsRes.loading || receiverPending.has(record.key)
            "
            @click="changeMessageReceivers(false, record, receiverDrafts.get(record.key)!.dataIndex)"
            >重试</a-button
          >
          <a-button
            type="text"
            :disabled="record.loading || receiverPending.has(record.key)"
            @click="cancelReceiverDraft(record)"
            >取消编辑</a-button
          >
        </div>
      </template>
      <template #robot="{ record, dataIndex }">
        <div v-if="!record.children && record.projectRobotConfigMap?.[dataIndex as string]" class="flex items-center">
          <a-switch
            v-model:model-value="record.projectRobotConfigMap[dataIndex as string].enable"
            v-permission="['PROJECT_MESSAGE:READ+UPDATE', 'PROJECT_MESSAGE:READ']"
            :disabled="
              robotEnable ||
              initializing ||
              readError ||
              propsRes.loading ||
              record.loading ||
              receiverDrafts.has(record.key)
            "
            :before-change="(val) => handleChangeIntercept(!!val, record, dataIndex as string)"
            size="small"
            type="line"
          />
          <a-popover position="right">
            <div class="ml-[8px] mr-[4px] cursor-pointer text-[var(--color-text-1)] hover:text-[rgb(var(--primary-6))]">
              {{ t('common.preview') }}
            </div>
            <template #content>
              <MessagePreview
                :robot="record.projectRobotConfigMap[dataIndex as string]"
                :function-name="record.functionName"
                :event-name="record.eventName"
              />
            </template>
          </a-popover>
          <MsButton
            v-permission="['PROJECT_MESSAGE:READ+UPDATE']"
            v-xpack
            type="button"
            @click="editRobot(record, dataIndex as string)"
            >{{ t('common.setting') }}
          </MsButton>
        </div>
        <span v-else></span>
      </template>
    </ms-base-table>
  </MsCard>
</template>

<script setup lang="ts">
  import { computed, onBeforeMount, reactive, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';

  import MsButton from '@/components/pure/ms-button/index.vue';
  import MsCard from '@/components/pure/ms-card/index.vue';
  import MsIcon from '@/components/pure/ms-icon-font/index.vue';
  import MsBaseTable from '@/components/pure/ms-table/base-table.vue';
  import type { MsTableColumn } from '@/components/pure/ms-table/type';
  import useTable from '@/components/pure/ms-table/useTable';
  import MsTagGroup from '@/components/pure/ms-tag/ms-tag-group.vue';
  import MsSelect from '@/components/business/ms-select';
  import MessagePreview from './messagePreview.vue';

  import {
    getMessageList,
    getMessageUserList,
    getRobotList,
    saveMessageConfig,
  } from '@/api/modules/project-management/messageManagement';
  import { useI18n } from '@/hooks/useI18n';
  import { hasAnyPermission } from '@/utils/permission';

  import type { MessageItem, ProjectRobotConfig, Receiver, RobotItem } from '@/models/projectManagement/message';

  import type { SelectOptionData, TableColumnData, TableData } from '@arco-design/web-vue';
  import type { TableOperationColumn } from '@arco-design/web-vue/es/table/interface';

  const emit = defineEmits(['createRobot']);

  const router = useRouter();
  const { t } = useI18n();

  const readError = ref(false);
  const initializing = ref(true);
  const receiverPending = reactive(new Set<string>());
  const receiverDrafts = reactive(
    new Map<string, { receivers: Receiver[]; persisted: Receiver[]; dataIndex: string }>()
  );
  const copyReceivers = (receivers: Receiver[]) => receivers.map((receiver) => ({ ...receiver }));
  const robotFilters = ref<string[]>([]);
  const robotOptions = ref<(SelectOptionData & RobotItem)[]>([]);
  const robotEnable = ref<boolean>(!hasAnyPermission(['PROJECT_MESSAGE:READ+UPDATE']));

  const tableRef = ref<InstanceType<typeof MsBaseTable> | null>(null);
  const staticColumns: MsTableColumn = [
    {
      title: 'project.messageManagement.function',
      dataIndex: 'name',
      slotName: 'name',
      width: 150,
      fixed: 'left',
      headerCellClass: 'pl-[16px]',
      bodyCellClass: (record) => {
        if (record.children) {
          return '';
        }
        return 'border-r border-[var(--color-text-n8)]';
      },
    },
    {
      title: 'project.messageManagement.noticeEvent',
      dataIndex: 'eventName',
      slotName: 'eventName',
      width: 150,
      fixed: 'left',
    },
    {
      title: 'project.messageManagement.receiver',
      dataIndex: 'receiver',
      slotName: 'receiver',
      width: 208,
      fixed: 'left',
    },
  ];

  // 根据机器人筛选器动态生成表格列
  const columns = computed(() => {
    if (robotFilters.value.length === 0) {
      return staticColumns;
    }
    const tempArr = [...staticColumns];
    for (let i = 0; i < robotFilters.value.length; i++) {
      const robotId = robotFilters.value[i];
      tempArr.push({
        title: robotOptions.value.find((e) => e.id === robotId)?.label,
        dataIndex: robotId,
        slotName: 'robot',
        width: 180,
      });
    }
    return tempArr;
  });

  watch(
    () => columns.value,
    (arr) => {
      tableRef.value?.initColumn(arr);
    }
  );

  const expandedKeys = ref<string[]>([]);
  const heightUsed = ref(428);

  interface TableMessageChildrenItem {
    functionName: string;
    taskType: string;
    name: string;
    rowspan?: number;
    projectRobotConfigMap: Record<string, ProjectRobotConfig>;

    [key: string]: any;
  }

  const receiverRows = reactive(new Map<string, TableMessageChildrenItem>());

  interface TableMessageItem {
    name: string;
    type: string;
    children?: TableMessageChildrenItem[];
  }

  const { propsRes, propsEvent, loadList, setLoadListParams } = useTable(
    async (params) => {
      try {
        const result = await getMessageList(params);
        readError.value = false;
        return result;
      } catch (error) {
        readError.value = true;
        throw error;
      }
    },
    {
      columns: columns.value as MsTableColumn,
      scroll: { x: 'auto' },
      showPagination: false,
      hoverable: false,
      showExpand: true,
      heightUsed: heightUsed.value,
      rowKey: 'key',
      rowClass: (record: TableMessageItem) => {
        if (record.children) {
          return 'gray-td-bg';
        }
      },
    },
    (item) => {
      const children: TableMessageChildrenItem[] = [];
      for (let i = 0; i < (item as unknown as MessageItem).messageTaskTypeDTOList.length; i++) {
        const child = (item as unknown as MessageItem).messageTaskTypeDTOList[i];
        for (let j = 0; j < child.messageTaskDetailDTOList.length; j++) {
          const grandson = child.messageTaskDetailDTOList[j];
          if (grandson.projectRobotConfigMap) {
            const firstRobot = Object.values(grandson.projectRobotConfigMap)[0];
            const openRobotKeys = Object.keys(grandson.projectRobotConfigMap); // 当前消息配置已经配置的机器人
            robotOptions.value.forEach((e) => {
              if (!openRobotKeys.includes(e.id)) {
                // 如果当前机器人未配置，则需要添加默认机器人信息到列表数据内
                grandson.projectRobotConfigMap[e.id] = {
                  ...e,
                  dingType: e.type,
                  robotId: e.id,
                  robotName: e.name,
                  enable: false,
                  previewTemplate: firstRobot.previewTemplate, // 消息配置机器人预览模板
                  template: firstRobot.defaultTemplate, // 消息配置机器人发送模板
                  defaultTemplate: firstRobot.defaultTemplate, // 消息配置机器人默认发送模板
                  useDefaultTemplate: true, // 消息配置机器人是否使用默认模板
                  previewSubject: firstRobot.previewSubject, // 消息模板配置的标题
                  subject: firstRobot.defaultSubject, // 消息模板配置的标题
                  defaultSubject: firstRobot.defaultSubject, // 消息模板配置的默认标题
                  useDefaultSubject: true, // 消息模板是否使用默认标题
                };
              }
            });
          }
          const key = `${(item as unknown as MessageItem).type}-${child.taskType}-${grandson.event}`;
          const draft = receiverDrafts.get(key);
          const row = {
            key,
            functionName: (item as unknown as MessageItem).name,
            taskType: child.taskType,
            name: child.taskTypeName,
            rowspan: child.messageTaskDetailDTOList.length || 1,
            ...grandson,
            persistedReceivers: copyReceivers(grandson.receivers),
            receivers: copyReceivers(draft?.receivers || grandson.receivers),
            loading: receiverPending.has(key),
          };
          receiverRows.set(key, row);
          children.push(row);
        }
      }
      return {
        key: (item as unknown as MessageItem).type,
        name: (item as unknown as MessageItem).name,
        type: (item as unknown as MessageItem).type,
        loading: false,
        hasSubtree: true,
        children,
      };
    }
  );

  function handleToggleFullScreen(val: boolean) {
    propsRes.value.heightUsed = val ? 214 : 428;
  }

  // TODO:合并单元格 arco 组件暂时有 bug 未解决，已提 issue
  function spanMethod(data: {
    record: TableData;
    column: TableColumnData | TableOperationColumn;
    rowIndex: number;
    columnIndex: number;
  }): { rowspan?: number; colspan?: number } | void {
    const { record, columnIndex } = data;
    if (record.rowspan && columnIndex === 0) {
      return {
        rowspan: record.rowspan,
      };
    }
  }

  const defaultReceivers = ref<SelectOptionData[]>([]);

  async function initReceivers() {
    const res = await getMessageUserList({ projectId: '__MSP_PLATFORM_NOTIFICATIONS__', keyword: '' });
    defaultReceivers.value = res.map((e) => ({
      label: e.name,
      ...e,
    }));
  }

  function getReceiverOptions(options: SelectOptionData[], event: string, taskType: string) {
    if (event === 'CREATE' || event === 'CASE_CREATE' || event === 'MOCK_CREATE') {
      // 创建事件的接收人不包含操作人、创建人、关注人
      options = options.filter((e) => !['OPERATOR', 'CREATE_USER', 'FOLLOW_PEOPLE', 'HANDLE_USER'].includes(e.id));
    }
    if (event.indexOf('EXECUTE') === -1) {
      // 除执行事件，都不显示操作人
      options = options.filter((e) => !['OPERATOR'].includes(e.id));
    }
    if (taskType === 'BUG_SYNC_TASK') {
      // 缺陷同步任务, 只显示操作人
      options = options.filter((e) => !['CREATE_USER', 'FOLLOW_PEOPLE', 'HANDLE_USER'].includes(e.id));
    }
    if (taskType.indexOf('BUG') === -1) {
      // 非缺陷任务，不显示处理人
      options = options.filter((e) => !['HANDLE_USER'].includes(e.id));
    }
    return options;
  }

  async function initRobotList() {
    const res = await getRobotList('__MSP_PLATFORM_NOTIFICATIONS__');
    robotOptions.value = res
      .filter((e) => e.enable)
      .map((e) => ({
        label: e.name,
        value: e.id,
        ...e,
      }));
  }

  async function initialize() {
    initializing.value = true;
    readError.value = false;
    try {
      await Promise.all([initReceivers(), initRobotList()]);
      setLoadListParams({ projectId: '__MSP_PLATFORM_NOTIFICATIONS__' });
      await loadList();
    } catch {
      readError.value = true;
    } finally {
      initializing.value = false;
    }
  }
  onBeforeMount(initialize);

  function editRobot(record: TableMessageChildrenItem, dataIndex: string) {
    if (receiverDrafts.has(record.key)) return Message.warning('请先重试保存或取消接收人草稿');
    if (record.receivers?.length === 0) {
      Message.warning(t('project.messageManagement.unsetReceiversTip'));
      return;
    }
    router.push({
      name: 'settingSystemParameter',
      query: {
        tab: 'messageNotification',
        notificationSection: 'messages',
        notificationTemplate: 'true',
        id: record.projectRobotConfigMap?.[dataIndex].robotId,
        taskType: record.taskType,
        event: record.event,
      },
    });
  }

  /**
   * 消息配置机器人切换启用禁用
   * @param val 开启/关闭
   * @param record 操作行记录
   * @param dataIndex 操作的机器人列
   */
  async function handleChangeIntercept(val: boolean, record: TableMessageChildrenItem, dataIndex: string) {
    if (
      record.loading ||
      initializing.value ||
      readError.value ||
      propsRes.value.loading ||
      receiverDrafts.has(record.key)
    )
      return false;
    if (record.receivers.length === 0) {
      Message.warning(t('project.messageManagement.unsetReceiverTip'));
      return false;
    }
    record.loading = true;
    try {
      await saveMessageConfig({
        ...record.projectRobotConfigMap?.[dataIndex],
        projectId: '__MSP_PLATFORM_NOTIFICATIONS__',
        taskType: record.taskType,
        event: record.event,
        receiverIds: record.receivers.map((e: Receiver) => (e.id !== undefined ? e.id : e)),
        enable: val, // 消息配置是否启用
      });
      Message.success(
        t(val ? 'project.messageManagement.enableRobotSuccess' : 'project.messageManagement.disableRobotSuccess', {
          name: `${record.functionName}-${record.eventName}-${record.projectRobotConfigMap?.[dataIndex].robotName}`,
        })
      );
      return true;
    } catch (error: any) {
      // eslint-disable-next-line no-console
      console.log(error);
      return false;
    } finally {
      record.loading = false;
    }
  }

  function updateReceiverDraft(record: TableMessageChildrenItem, receivers: Receiver[]) {
    const draft = receiverDrafts.get(record.key);
    if (draft) draft.receivers = copyReceivers(receivers);
  }

  function cancelReceiverDraft(record: TableMessageChildrenItem) {
    const draft = receiverDrafts.get(record.key);
    if (!draft || record.loading || receiverPending.has(record.key)) return;
    record.receivers = copyReceivers(draft.persisted);
    const current = receiverRows.get(record.key);
    if (current) current.receivers = copyReceivers(draft.persisted);
    receiverDrafts.delete(record.key);
  }

  async function changeMessageReceivers(val: boolean, record: TableMessageChildrenItem, dataIndex: string) {
    const { key } = record;
    if (
      receiverPending.has(key) ||
      record.loading ||
      initializing.value ||
      readError.value ||
      propsRes.value.loading ||
      val
    )
      return false;
    if (record.receivers.length === 0) {
      Message.warning(t('project.messageManagement.receiverNotNull'));
      return false;
    }
    const receivers = copyReceivers(record.receivers);
    const persisted = copyReceivers(receiverDrafts.get(key)?.persisted || record.persistedReceivers);
    receiverPending.add(key);
    record.loading = true;
    try {
      try {
        await saveMessageConfig({
          ...record.projectRobotConfigMap?.[dataIndex],
          projectId: '__MSP_PLATFORM_NOTIFICATIONS__',
          taskType: record.taskType,
          event: record.event,
          receiverIds: receivers.map((receiver) => receiver.id),
        });
      } catch {
        receiverDrafts.set(key, { receivers, persisted, dataIndex });
        const current = receiverRows.get(key);
        if (current) current.receivers = copyReceivers(receivers);
        Message.error('接收人保存失败，已保留草稿，请重试或取消编辑');
        return false;
      }
      receiverDrafts.delete(key);
      record.persistedReceivers = copyReceivers(receivers);
      const current = receiverRows.get(key);
      if (current) {
        current.persistedReceivers = copyReceivers(receivers);
        current.receivers = copyReceivers(receivers);
      }
      Message.success(t('project.messageManagement.saveReceiverSuccess'));
      await loadList();
      return true;
    } finally {
      // GET can replace record while the request is pending. Release by business key
      // and restore the current row, not only the stale callback's row reference.
      receiverPending.delete(key);
      record.loading = false;
      const current = receiverRows.get(key);
      if (current) current.loading = false;
    }
  }
</script>

<style lang="less" scoped>
  :deep(.arco-table-cell-expand-icon) {
    .arco-table-cell-inline-icon {
      margin-right: 8px;
    }
  }
  :deep(.gray-td-bg) {
    td {
      background-color: var(--color-text-n9);
    }
  }
  :deep(.arco-select-view-multiple.arco-select-view-size-medium .arco-select-view-tag) {
    margin-top: 1px;
    margin-bottom: 1px;
    max-width: 80px;
    height: auto;
    min-height: 24px;
    line-height: 22px;
    vertical-align: middle;
  }
</style>
