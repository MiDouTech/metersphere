<template>
  <MsCard simple auto-height :loading="loading">
    <a-tabs v-model:active-key="activeKey" @change="loadActiveTab">
      <a-tab-pane key="config" :title="t('system.wecomBot.configTab')">
        <a-alert class="mb-4">{{ t('system.wecomBot.secretTip') }}</a-alert>
        <a-form :model="configForm" layout="vertical" class="max-w-[720px]">
          <a-form-item :label="t('system.wecomBot.name')" required><a-input v-model="configForm.name" /></a-form-item>
          <a-form-item label="BotID" required><a-input v-model="configForm.botId" /></a-form-item>
          <a-form-item :label="t('system.wecomBot.secretRef')"
            ><a-input
              v-model="configForm.secretRef"
              :disabled="!!configForm.secret"
              placeholder="env:MS_WECOM_BOT_SECRET"
          /></a-form-item>
          <a-form-item :label="t('system.wecomBot.rotateSecret')">
            <a-input-password
              v-model="configForm.secret"
              :disabled="!!configForm.secretRef"
              :placeholder="
                config.secretConfigured ? t('system.wecomBot.secretKept') : t('system.wecomBot.secretInput')
              "
            />
          </a-form-item>
          <a-descriptions :column="2" bordered class="mb-4">
            <a-descriptions-item :label="t('system.wecomBot.connectionStatus')"
              ><a-tag :color="statusColor(config.status)">{{ config.status }}</a-tag></a-descriptions-item
            >
            <a-descriptions-item label="Secret">{{
              config.secretConfigured ? t('system.wecomBot.configured') : t('system.wecomBot.unconfigured')
            }}</a-descriptions-item>
            <a-descriptions-item :label="t('system.wecomBot.lastHeartbeat')">{{
              formatTime(config.lastHeartbeatAt)
            }}</a-descriptions-item>
            <a-descriptions-item :label="t('system.wecomBot.lastConnected')">{{
              formatTime(config.lastConnectedAt)
            }}</a-descriptions-item>
            <a-descriptions-item :label="t('system.wecomBot.lastError')">{{
              config.lastErrorMessage || '-'
            }}</a-descriptions-item>
          </a-descriptions>
          <a-form-item :label="t('system.wecomBot.testRecipient')">
            <a-select v-model="testUserId" allow-search :placeholder="t('system.wecomBot.selectTestRecipient')">
              <a-option v-for="user in recipientUsers" :key="user.id" :value="user.id" :disabled="!asBool(user.mapped)"
                >{{ user.name }}{{ asBool(user.mapped) ? '' : t('system.wecomBot.unmapped') }}</a-option
              >
            </a-select>
          </a-form-item>
          <a-space>
            <a-button
              v-permission="['SYSTEM_CONFIG_WECOM_BOT:UPDATE']"
              type="primary"
              :loading="saving"
              @click="saveConfig"
              >{{ t('common.save') }}</a-button
            >
            <a-button v-permission="['SYSTEM_CONFIG_WECOM_BOT:UPDATE']" @click="testConnection">{{
              t('system.wecomBot.testConnection')
            }}</a-button>
            <a-button
              v-permission="['SYSTEM_CONFIG_WECOM_BOT:UPDATE']"
              :disabled="!config.enabled"
              @click="sendUserTest"
              >{{ t('system.wecomBot.testUser') }}</a-button
            >
            <a-switch v-if="config.id" :model-value="config.enabled" :disabled="!canUpdate" @change="toggleBot" />
            <span v-if="config.id">{{
              config.enabled ? t('system.wecomBot.enabled') : t('system.wecomBot.disabled')
            }}</span>
          </a-space>
        </a-form>
      </a-tab-pane>

      <a-tab-pane key="chats" :title="t('system.wecomBot.chatsTab')">
        <a-alert class="mb-4">{{ t('system.wecomBot.groupTip') }}</a-alert>
        <a-table :data="chats" row-key="id" :pagination="false">
          <template #columns>
            <a-table-column :title="t('system.wecomBot.groupName')">
              <template #cell="{ record }"><a-input v-model="record.display_name" :disabled="!canUpdate" /></template>
            </a-table-column>
            <a-table-column title="chatid" :width="150"
              ><template #cell="{ record }">{{ maskId(record.chat_id) }}</template></a-table-column
            >
            <a-table-column :title="t('system.wecomBot.type')" data-index="chat_type" :width="100" />
            <a-table-column :title="t('system.wecomBot.firstSeen')" :width="180"
              ><template #cell="{ record }">{{ formatTime(record.first_seen_at) }}</template></a-table-column
            >
            <a-table-column :title="t('system.wecomBot.lastSeen')" :width="180"
              ><template #cell="{ record }">{{ formatTime(record.last_seen_at) }}</template></a-table-column
            >
            <a-table-column :title="t('system.wecomBot.lastDelivery')" data-index="last_delivery_status" :width="120" />
            <a-table-column :title="t('system.wecomBot.enable')" :width="90"
              ><template #cell="{ record }"
                ><a-switch
                  :model-value="asBool(record.active)"
                  :disabled="!canUpdate"
                  @change="(value) => toggleChat(record, value)" /></template
            ></a-table-column>
            <a-table-column :title="t('common.operation')" :width="180">
              <template #cell="{ record }">
                <a-space
                  ><a-button type="text" :disabled="!canUpdate" @click="saveChatName(record)">{{
                    t('system.wecomBot.saveRemark')
                  }}</a-button
                  ><a-button
                    type="text"
                    :disabled="!canUpdate || !asBool(record.active)"
                    @click="sendGroupTest(record)"
                    >{{ t('system.wecomBot.testSend') }}</a-button
                  ></a-space
                >
              </template>
            </a-table-column>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane
        v-if="canReadRules"
        key="rules"
        :title="t('system.wecomBot.rulesTab')"
        data-resource-code="SYSTEM_NOTIFICATION_RULE_PAGE"
      >
        <div class="mb-4 flex justify-end"
          ><a-button v-permission="['SYSTEM_NOTIFICATION_RULE:CREATE']" type="primary" @click="openRule()">{{
            t('system.wecomBot.newRule')
          }}</a-button></div
        >
        <a-table :data="rules" row-key="id" :pagination="false" :scroll="{ x: 1200 }">
          <template #columns>
            <a-table-column :title="t('system.wecomBot.name')" data-index="name" :width="180" />
            <a-table-column
              :title="t('system.wecomBot.notificationType')"
              data-index="notification_type"
              :width="230"
            />
            <a-table-column :title="t('system.wecomBot.trigger')" data-index="trigger_type" :width="100" />
            <a-table-column :title="t('system.wecomBot.timezone')" data-index="timezone" :width="130" />
            <a-table-column :title="t('system.wecomBot.nextFire')" :width="180"
              ><template #cell="{ record }">{{ formatTime(record.next_fire_time) }}</template></a-table-column
            >
            <a-table-column :title="t('system.wecomBot.enable')" :width="90"
              ><template #cell="{ record }"
                ><a-switch
                  :model-value="asBool(record.enabled)"
                  :disabled="!canUpdateRules"
                  @change="(value) => toggleRule(record, value)" /></template
            ></a-table-column>
            <a-table-column :title="t('common.operation')" fixed="right" :width="240">
              <template #cell="{ record }"
                ><a-space
                  ><a-button type="text" :disabled="!canUpdateRules" @click="openRule(record)">{{
                    t('common.edit')
                  }}</a-button
                  ><a-button type="text" :disabled="!canUpdateRules" @click="copyRule(record)">{{
                    t('common.copy')
                  }}</a-button
                  ><a-button type="text" @click="previewRule(record)">{{ t('system.wecomBot.preview') }}</a-button
                  ><a-button
                    v-if="record.notification_type === 'CUSTOM_CRON'"
                    type="text"
                    :disabled="!canUpdateRules || !asBool(record.enabled)"
                    @click="runRule(record)"
                    >{{ t('system.wecomBot.runOnce') }}</a-button
                  ><a-popconfirm :content="t('system.wecomBot.deleteConfirm')" @ok="removeRule(record)"
                    ><a-button type="text" status="danger" :disabled="!canDeleteRules">{{
                      t('common.delete')
                    }}</a-button></a-popconfirm
                  ></a-space
                ></template
              >
            </a-table-column>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane
        v-if="canReadLogs"
        key="logs"
        :title="t('system.wecomBot.logsTab')"
        data-resource-code="SYSTEM_NOTIFICATION_LOG_PAGE"
      >
        <div class="mb-4 flex flex-wrap gap-2"
          ><a-select
            v-model="logStatus"
            allow-clear
            :placeholder="t('system.wecomBot.status')"
            class="w-[150px]"
            @change="loadLogs"
            ><a-option v-for="item in ['PENDING', 'SENDING', 'SUCCESS', 'RETRY', 'FAILED', 'CANCELLED']" :key="item">{{
              item
            }}</a-option></a-select
          ><a-select
            v-model="logEventType"
            allow-clear
            :placeholder="t('system.wecomBot.notificationType')"
            class="w-[220px]"
            @change="loadLogs"
            ><a-option
              v-for="item in ['BUG_EXPECTED_RESOLUTION_DUE', 'TEST_REPORT_GENERATED', 'CUSTOM_CRON', 'TEST']"
              :key="item"
              >{{ item }}</a-option
            ></a-select
          ><a-select
            v-model="logTargetType"
            allow-clear
            :placeholder="t('system.wecomBot.target')"
            class="w-[130px]"
            @change="loadLogs"
            ><a-option value="USER">USER</a-option><a-option value="CHAT">CHAT</a-option></a-select
          ><a-select
            v-if="canReadRules"
            v-model="logRuleId"
            allow-clear
            :placeholder="t('system.wecomBot.rulesTab')"
            class="w-[180px]"
            @change="loadLogs"
            ><a-option v-for="rule in rules" :key="rule.id" :value="rule.id">{{ rule.name }}</a-option></a-select
          ><a-range-picker
            v-model="logRange"
            show-time
            value-format="timestamp"
            class="w-[350px]"
            @change="loadLogs"
          /><a-button @click="loadLogs">{{ t('common.refresh') }}</a-button></div
        >
        <a-table :data="logs" row-key="id" :pagination="logPagination" @page-change="changeLogPage">
          <template #columns>
            <a-table-column :title="t('system.wecomBot.time')" :width="180"
              ><template #cell="{ record }">{{ formatTime(record.create_time) }}</template></a-table-column
            >
            <a-table-column :title="t('system.wecomBot.type')" data-index="event_type" :width="210" />
            <a-table-column :title="t('system.wecomBot.target')" data-index="target_type" :width="90" />
            <a-table-column
              :title="t('system.wecomBot.messagePreview')"
              data-index="payload_preview"
              ellipsis
              tooltip
            />
            <a-table-column :title="t('system.wecomBot.status')" data-index="status" :width="110" />
            <a-table-column :title="t('system.wecomBot.attempts')" data-index="attempts" :width="100" />
            <a-table-column :title="t('system.wecomBot.error')" ellipsis tooltip
              ><template #cell="{ record }">{{
                [record.error_code, record.error_message].filter(Boolean).join(': ') || '-'
              }}</template></a-table-column
            >
            <a-table-column :title="t('common.operation')" :width="150"
              ><template #cell="{ record }"
                ><a-space
                  ><a-button type="text" @click="openLogDetail(record)">{{ t('system.wecomBot.detail') }}</a-button
                  ><a-button
                    v-if="record.status === 'FAILED' && asBool(record.retryable)"
                    type="text"
                    :disabled="!canRetry"
                    @click="retryMessage(record)"
                    >{{ t('system.wecomBot.retry') }}</a-button
                  ></a-space
                ></template
              ></a-table-column
            >
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>
  </MsCard>

  <a-modal
    v-model:visible="ruleVisible"
    :title="editingRuleId ? t('system.wecomBot.editRule') : t('system.wecomBot.newRule')"
    :ok-loading="savingRule"
    width="760px"
    :mask-closable="false"
    @before-ok="saveRule"
  >
    <a-form :model="ruleForm" layout="vertical">
      <a-form-item :label="t('system.wecomBot.name')" required><a-input v-model="ruleForm.name" /></a-form-item>
      <a-grid :cols="2" :col-gap="16"
        ><a-grid-item
          ><a-form-item :label="t('system.wecomBot.notificationType')" required
            ><a-select v-model="ruleForm.notificationType" @change="syncTrigger"
              ><a-option value="BUG_EXPECTED_RESOLUTION_DUE">{{ t('system.wecomBot.bugDue') }}</a-option
              ><a-option value="TEST_REPORT_GENERATED">{{ t('system.wecomBot.reportGenerated') }}</a-option
              ><a-option value="CUSTOM_CRON">{{ t('system.wecomBot.customCron') }}</a-option></a-select
            ></a-form-item
          ></a-grid-item
        ><a-grid-item
          ><a-form-item :label="t('system.wecomBot.scope')"
            ><a-select v-model="ruleForm.scopeType" @change="changeScope"
              ><a-option value="SYSTEM">{{ t('system.wecomBot.system') }}</a-option
              ><a-option value="PROJECT">{{ t('system.wecomBot.project') }}</a-option></a-select
            ></a-form-item
          ></a-grid-item
        ></a-grid
      >
      <a-form-item v-if="ruleForm.scopeType === 'PROJECT'" :label="t('system.wecomBot.project')" required
        ><a-select v-model="ruleForm.scopeId" allow-search @change="loadRecipientUsers"
          ><a-option v-for="project in appStore.projectList" :key="project.id" :value="project.id">{{
            project.name
          }}</a-option></a-select
        ></a-form-item
      >
      <a-grid v-if="ruleForm.triggerType === 'DEADLINE'" :cols="3" :col-gap="16"
        ><a-grid-item
          ><a-form-item :label="t('system.wecomBot.leadTime')"
            ><a-input-group compact
              ><a-input-number v-model="leadAmount" :min="0" class="w-2/3" /><a-select v-model="leadUnit" class="w-1/3"
                ><a-option value="MINUTE">{{ t('system.wecomBot.minutes') }}</a-option
                ><a-option value="HOUR">{{ t('system.wecomBot.hours') }}</a-option
                ><a-option value="DAY">{{ t('system.wecomBot.days') }}</a-option></a-select
              ></a-input-group
            ></a-form-item
          ></a-grid-item
        ><a-grid-item
          ><a-form-item :label="t('system.wecomBot.repeatInterval')"
            ><a-input-group compact
              ><a-input-number v-model="repeatAmount" :min="0" class="w-2/3" /><a-select
                v-model="repeatUnit"
                class="w-1/3"
                ><a-option value="MINUTE">{{ t('system.wecomBot.minutes') }}</a-option
                ><a-option value="HOUR">{{ t('system.wecomBot.hours') }}</a-option
                ><a-option value="DAY">{{ t('system.wecomBot.days') }}</a-option></a-select
              ></a-input-group
            ></a-form-item
          ></a-grid-item
        ><a-grid-item
          ><a-form-item :label="t('system.wecomBot.maxCount')"
            ><a-input-number v-model="maxCount" :min="1" :max="100" /></a-form-item></a-grid-item
      ></a-grid>
      <a-grid v-if="ruleForm.triggerType === 'CRON'" :cols="2" :col-gap="16"
        ><a-grid-item
          ><a-form-item label="Cron" required
            ><a-input v-model="ruleForm.cron" placeholder="0 0 9 * * ?" />
            <template #extra>{{ t('system.wecomBot.cronTip') }}</template></a-form-item
          ></a-grid-item
        ><a-grid-item
          ><a-form-item :label="t('system.wecomBot.timezone')" required
            ><a-select v-model="ruleForm.timezone"
              ><a-option value="Asia/Shanghai">Asia/Shanghai</a-option><a-option value="UTC">UTC</a-option></a-select
            ></a-form-item
          ></a-grid-item
        ></a-grid
      >
      <a-form-item v-if="ruleForm.triggerType === 'EVENT'" :label="t('system.wecomBot.generationModes')" required
        ><a-select v-model="reportGenerationModes" multiple
          ><a-option value="MANUAL">{{ t('system.wecomBot.manualGeneration') }}</a-option
          ><a-option value="AUTO">{{ t('system.wecomBot.autoGeneration') }}</a-option></a-select
        ></a-form-item
      >
      <a-grid :cols="2" :col-gap="16"
        ><a-grid-item
          ><a-form-item :label="t('system.wecomBot.startAt')"
            ><a-date-picker v-model="ruleForm.startAt" show-time value-format="timestamp" /></a-form-item></a-grid-item
        ><a-grid-item
          ><a-form-item :label="t('system.wecomBot.endAt')"
            ><a-date-picker v-model="ruleForm.endAt" show-time value-format="timestamp" /></a-form-item></a-grid-item
      ></a-grid>
      <a-form-item v-if="ruleForm.triggerType === 'DEADLINE'" :label="t('system.wecomBot.terminalStatuses')"
        ><a-select v-model="terminalStatuses" multiple allow-search
          ><a-option v-for="status in bugTerminalStatuses" :key="status.id" :value="status.id">{{
            status.name
          }}</a-option></a-select
        ></a-form-item
      >
      <a-form-item :label="t('system.wecomBot.enabledGroups')"
        ><a-select v-model="ruleForm.recipientSpec.chatIds" multiple allow-search
          ><a-option v-for="chat in enabledChats" :key="chat.id" :value="chat.chat_id">{{
            chat.display_name || `${t('system.wecomBot.group')} ${chat.id}`
          }}</a-option></a-select
        ></a-form-item
      >
      <a-form-item :label="t('system.wecomBot.users')"
        ><a-select v-model="ruleForm.recipientSpec.userIds" multiple allow-search
          ><a-option v-for="user in recipientUsers" :key="user.id" :value="user.id" :disabled="!asBool(user.mapped)"
            >{{ user.name }}{{ asBool(user.mapped) ? '' : t('system.wecomBot.unmapped') }}</a-option
          ></a-select
        ></a-form-item
      >
      <a-form-item
        v-if="ruleForm.notificationType === 'BUG_EXPECTED_RESOLUTION_DUE'"
        :label="t('system.wecomBot.businessRoles')"
      >
        <a-checkbox-group v-model="ruleForm.recipientSpec.businessRoles">
          <a-checkbox value="BUG_CREATOR">{{ t('system.wecomBot.bugCreator') }}</a-checkbox>
          <a-checkbox value="BUG_HANDLER">{{ t('system.wecomBot.bugHandler') }}</a-checkbox>
        </a-checkbox-group>
      </a-form-item>
      <a-form-item v-if="ruleForm.scopeType === 'PROJECT'" :label="t('system.wecomBot.projectMembers')">
        <a-checkbox v-model="ruleForm.recipientSpec.projectAllMembers">{{
          t('system.wecomBot.allProjectMembers')
        }}</a-checkbox>
      </a-form-item>
      <a-form-item v-if="ruleForm.scopeType === 'PROJECT'" :label="t('system.wecomBot.projectRoles')">
        <a-select v-model="ruleForm.recipientSpec.projectRoleIds" multiple allow-search>
          <a-option v-for="role in projectRoles" :key="role.id" :value="role.id">{{ role.name }}</a-option>
        </a-select>
      </a-form-item>
      <a-form-item :label="t('system.wecomBot.userGroups')">
        <a-select v-model="ruleForm.recipientSpec.userGroupIds" multiple allow-search>
          <a-option v-for="role in userGroups" :key="role.id" :value="role.id">{{ role.name }}</a-option>
        </a-select>
      </a-form-item>
      <a-alert v-if="ruleForm.notificationType === 'TEST_REPORT_GENERATED'" class="mb-4">{{
        t('system.wecomBot.reportRecipientsTip')
      }}</a-alert>
      <a-form-item :label="t('system.wecomBot.deliveryMode')" required
        ><a-select v-model="ruleForm.deliveryMode" :disabled="ruleForm.notificationType === 'TEST_REPORT_GENERATED'"
          ><a-option value="USER">{{ t('system.wecomBot.deliveryUser') }}</a-option
          ><a-option value="CHAT">{{ t('system.wecomBot.deliveryChat') }}</a-option
          ><a-option value="BOTH">{{ t('system.wecomBot.deliveryBoth') }}</a-option></a-select
        ></a-form-item
      >
      <a-form-item :label="t('system.wecomBot.markdownTemplate')" required>
        <div class="w-full"
          ><a-space wrap class="mb-2"
            ><a-button
              v-for="variable in templateVariables"
              :key="variable"
              size="mini"
              @click="insertVariable(variable)"
              >{{ variableLabel(variable) }}</a-button
            ></a-space
          ><a-textarea
            v-model="ruleForm.template"
            :max-length="20480"
            show-word-limit
            :auto-size="{ minRows: 6, maxRows: 12 }"
        /></div>
      </a-form-item>
      <a-alert>{{ t('system.wecomBot.templateTip') }}</a-alert>
    </a-form>
  </a-modal>
  <a-modal v-model:visible="logDetailVisible" :title="t('system.wecomBot.logDetail')" :footer="false" width="680px">
    <a-descriptions v-if="logDetail" :column="1" bordered>
      <a-descriptions-item v-for="(value, key) in logDetail" :key="key" :label="String(key)">{{
        value ?? '-'
      }}</a-descriptions-item>
    </a-descriptions>
  </a-modal>
</template>

<script setup lang="ts">
  import { Message, Modal } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import MsCard from '@/components/pure/ms-card/index.vue';

  import {
    createWecomRule,
    deleteWecomRule,
    getWecomBotConfig,
    getWecomBugTerminalStatuses,
    getWecomChats,
    getWecomLogs,
    getWecomMessage,
    getWecomRecipientRoles,
    getWecomRecipientUsers,
    getWecomRules,
    previewWecomRule,
    renameWecomChat,
    retryWecomMessage,
    runWecomRule,
    saveWecomBotConfig,
    setWecomBotEnabled,
    setWecomChatEnabled,
    setWecomRuleEnabled,
    testWecomBotConnection,
    testWecomGroup,
    testWecomUser,
    updateWecomRule,
    type WecomBotConfig,
    type WecomChat,
    type WecomRuleRequest,
  } from '@/api/modules/setting/wecomBot';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';
  import { hasAnyPermission } from '@/utils/permission';

  const appStore = useAppStore();
  const { t } = useI18n();
  const activeKey = ref('config');
  const loading = ref(false);
  const saving = ref(false);
  const config = ref<WecomBotConfig>({
    name: '',
    botId: '',
    secretConfigured: false,
    enabled: false,
    status: 'DISABLED',
  });
  const configForm = reactive({ name: '', botId: '', secret: '', secretRef: '' });
  const chats = ref<WecomChat[]>([]);
  const rules = ref<Record<string, any>[]>([]);
  const recipientUsers = ref<{ id: string; name: string; mapped: boolean | number }[]>([]);
  const recipientRoles = ref<
    { id: string; name: string; type: 'SYSTEM' | 'ORGANIZATION' | 'PROJECT'; scope_id: string }[]
  >([]);
  const bugTerminalStatuses = ref<{ id: string; name: string; status_code: string }[]>([]);
  const testUserId = ref('');
  const logs = ref<Record<string, any>[]>([]);
  const logDetailVisible = ref(false);
  const logDetail = ref<Record<string, any>>();
  const logStatus = ref<string>();
  const logEventType = ref<string>();
  const logTargetType = ref<string>();
  const logRuleId = ref<string>();
  const logRange = ref<number[]>([]);
  const logPage = ref(1);
  const logTotal = ref(0);
  const ruleVisible = ref(false);
  const editingRuleId = ref('');
  const savingRule = ref(false);
  const leadAmount = ref(60);
  const leadUnit = ref('MINUTE');
  const repeatAmount = ref(0);
  const repeatUnit = ref('MINUTE');
  const maxCount = ref(100);
  const terminalStatuses = ref<string[]>([]);
  const reportGenerationModes = ref<string[]>(['MANUAL', 'AUTO']);
  const DEFAULT_CRON = '0 0 9 * * ?';
  const canUpdate = computed(() => hasAnyPermission(['SYSTEM_CONFIG_WECOM_BOT:UPDATE']));
  const canReadRules = computed(() => hasAnyPermission(['SYSTEM_NOTIFICATION_RULE:READ']));
  const canUpdateRules = computed(() => hasAnyPermission(['SYSTEM_NOTIFICATION_RULE:UPDATE']));
  const canDeleteRules = computed(() => hasAnyPermission(['SYSTEM_NOTIFICATION_RULE:DELETE']));
  const canReadLogs = computed(() => hasAnyPermission(['SYSTEM_NOTIFICATION_LOG:READ']));
  const canRetry = computed(() => hasAnyPermission(['SYSTEM_NOTIFICATION_LOG:RETRY']));
  const logPagination = computed(() => ({
    current: logPage.value,
    pageSize: 20,
    total: logTotal.value,
    showTotal: true,
  }));

  const defaultRule = (): WecomRuleRequest => ({
    name: '',
    scopeType: 'SYSTEM',
    notificationType: 'BUG_EXPECTED_RESOLUTION_DUE',
    triggerType: 'DEADLINE',
    triggerConfig: {},
    cron: DEFAULT_CRON,
    timezone: 'Asia/Shanghai',
    template: t('system.wecomBot.defaultBugTemplate'),
    recipientSpec: {
      chatIds: [],
      userIds: [],
      businessRoles: ['BUG_CREATOR', 'BUG_HANDLER'],
      projectAllMembers: false,
      projectRoleIds: [],
      userGroupIds: [],
    },
    deliveryMode: 'BOTH',
    stopConfig: { statuses: [] },
  });
  const ruleForm = reactive<WecomRuleRequest>(defaultRule());

  const asBool = (value: unknown) => value === true || value === 1;
  const variableLabel = (variable: string) => `\${${variable}}`;
  const enabledChats = computed(() => chats.value.filter((chat) => asBool(chat.active)));
  const projectRoles = computed(() => recipientRoles.value.filter((role) => role.type === 'PROJECT'));
  const userGroups = computed(() => recipientRoles.value.filter((role) => role.type !== 'PROJECT'));
  const templateVariables = computed(() => {
    if (ruleForm.notificationType === 'BUG_EXPECTED_RESOLUTION_DUE') {
      return [
        'bugNum',
        'bugTitle',
        'bugHandlerNames',
        'bugCreatorName',
        'expectedResolveTime',
        'remainingTime',
        'projectName',
        'resourceUrl',
      ];
    }
    if (ruleForm.notificationType === 'TEST_REPORT_GENERATED') {
      return [
        'projectName',
        'testPlanName',
        'reportName',
        'reportGeneratorName',
        'reportSummary',
        'reportUrl',
        'generatedAt',
      ];
    }
    return ['customTitle', 'customContent', 'now', 'ruleName'];
  });
  const maskId = (value: string) =>
    value && value.length > 8 ? `${value.slice(0, 3)}******${value.slice(-3)}` : '******';
  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');
  const statusColor = (status: string) =>
    ({ ONLINE: 'green', CONNECTING: 'blue', AUTH_FAILED: 'red', OFFLINE: 'orange' }[status] || 'gray');
  const parseJson = (value: unknown, fallback: Record<string, any>) => {
    try {
      return typeof value === 'string' ? JSON.parse(value) : value || fallback;
    } catch {
      return fallback;
    }
  };

  async function loadConfig() {
    config.value = await getWecomBotConfig();
    Object.assign(configForm, { name: config.value.name, botId: config.value.botId, secret: '', secretRef: '' });
    recipientUsers.value = canUpdate.value ? await getWecomRecipientUsers() : [];
  }
  async function loadChats() {
    chats.value = await getWecomChats();
  }
  async function loadRules() {
    rules.value = await getWecomRules();
    bugTerminalStatuses.value = await getWecomBugTerminalStatuses();
  }

  function localRuleRow(id: string) {
    const previous = rules.value.find((item) => item.id === id) || {};
    return {
      ...previous,
      id,
      name: ruleForm.name,
      scope_type: ruleForm.scopeType,
      scope_id: ruleForm.scopeId,
      notification_type: ruleForm.notificationType,
      trigger_type: ruleForm.triggerType,
      trigger_config: JSON.stringify(ruleForm.triggerConfig),
      cron: ruleForm.cron,
      timezone: ruleForm.timezone,
      template: ruleForm.template,
      recipient_spec: JSON.stringify(ruleForm.recipientSpec),
      delivery_mode: ruleForm.deliveryMode,
      stop_config: JSON.stringify(ruleForm.stopConfig),
      start_at: ruleForm.startAt,
      end_at: ruleForm.endAt,
      enabled: previous.enabled ?? 0,
      update_time: Date.now(),
    };
  }

  function upsertLocalRule(record: Record<string, any>) {
    const index = rules.value.findIndex((item) => item.id === record.id);
    if (index >= 0) rules.value.splice(index, 1, record);
    else rules.value.unshift(record);
  }

  function ruleMatchesForm(record: Record<string, any>) {
    return (
      record.name === ruleForm.name &&
      record.scope_type === ruleForm.scopeType &&
      (record.scope_id || undefined) === ruleForm.scopeId &&
      record.notification_type === ruleForm.notificationType &&
      record.trigger_type === ruleForm.triggerType &&
      record.timezone === ruleForm.timezone &&
      record.template === ruleForm.template &&
      record.delivery_mode === ruleForm.deliveryMode
    );
  }

  function showRuleValidation(message: string) {
    Modal.warning({ title: t('system.wecomBot.validationFailed'), content: message });
  }
  async function loadLogs() {
    const result = await getWecomLogs({
      page: logPage.value,
      pageSize: 20,
      status: logStatus.value,
      eventType: logEventType.value,
      targetType: logTargetType.value,
      ruleId: logRuleId.value,
      startAt: logRange.value?.[0],
      endAt: logRange.value?.[1],
    });
    logs.value = result.list;
    logTotal.value = result.total;
  }
  async function loadActiveTab() {
    loading.value = true;
    try {
      if (activeKey.value === 'config') await loadConfig();
      else if (activeKey.value === 'chats') await loadChats();
      else if (activeKey.value === 'rules') await Promise.all([loadRules(), loadChats()]);
      else if (canReadRules.value) await Promise.all([loadLogs(), loadRules()]);
      else await loadLogs();
    } finally {
      loading.value = false;
    }
  }
  async function saveConfig() {
    if (!configForm.name || !configForm.botId) return Message.warning(t('system.wecomBot.required'));
    if (configForm.secret && configForm.secretRef) return Message.warning(t('system.wecomBot.secretExclusive'));
    saving.value = true;
    try {
      await saveWecomBotConfig(configForm);
      Message.success(t('common.saveSuccess'));
      await loadConfig();
    } finally {
      saving.value = false;
    }
  }
  async function testConnection() {
    await testWecomBotConnection();
    Message.success(t('system.wecomBot.testSuccess'));
    await loadConfig();
  }
  async function sendUserTest() {
    if (!testUserId.value) return Message.warning(t('system.wecomBot.selectTestRecipient'));
    await testWecomUser(testUserId.value, t('system.wecomBot.testMessage'));
    Message.success(t('system.wecomBot.queued'));
  }
  async function toggleBot(value: string | number | boolean) {
    await setWecomBotEnabled(Boolean(value));
    await loadConfig();
  }
  async function saveChatName(chat: WecomChat) {
    await renameWecomChat(chat.id, chat.display_name || '');
    Message.success(t('common.saveSuccess'));
  }
  async function toggleChat(chat: WecomChat, value: string | number | boolean) {
    await setWecomChatEnabled(chat.id, Boolean(value));
    await loadChats();
  }
  async function sendGroupTest(chat: WecomChat) {
    await testWecomGroup(chat.id, t('system.wecomBot.testMessage'));
    Message.success(t('system.wecomBot.queued'));
  }
  function syncTrigger() {
    const map = {
      BUG_EXPECTED_RESOLUTION_DUE: 'DEADLINE',
      TEST_REPORT_GENERATED: 'EVENT',
      CUSTOM_CRON: 'CRON',
    } as const;
    ruleForm.triggerType = map[ruleForm.notificationType];
    ruleForm.recipientSpec.businessRoles =
      ruleForm.notificationType === 'BUG_EXPECTED_RESOLUTION_DUE' ? ['BUG_CREATOR', 'BUG_HANDLER'] : [];
    if (ruleForm.notificationType === 'TEST_REPORT_GENERATED') {
      ruleForm.deliveryMode = 'CHAT';
      ruleForm.template = t('system.wecomBot.defaultReportTemplate');
    } else if (ruleForm.notificationType === 'CUSTOM_CRON') {
      ruleForm.deliveryMode = 'BOTH';
      ruleForm.cron ||= DEFAULT_CRON;
      ruleForm.template = ['customTitle', 'customContent'].map(variableLabel).join('\n');
    } else {
      ruleForm.deliveryMode = 'BOTH';
      ruleForm.template = defaultRule().template;
    }
  }
  async function loadRecipientUsers() {
    const projectId = ruleForm.scopeType === 'PROJECT' ? ruleForm.scopeId : undefined;
    [recipientUsers.value, recipientRoles.value] = await Promise.all([
      getWecomRecipientUsers(projectId),
      getWecomRecipientRoles(projectId),
    ]);
  }
  async function changeScope() {
    ruleForm.scopeId = undefined;
    ruleForm.recipientSpec.userIds = [];
    ruleForm.recipientSpec.projectRoleIds = [];
    ruleForm.recipientSpec.projectAllMembers = false;
    await loadRecipientUsers();
  }
  function openRule(record?: Record<string, any>) {
    Object.assign(ruleForm, defaultRule());
    editingRuleId.value = record?.id || '';
    leadAmount.value = 60;
    leadUnit.value = 'MINUTE';
    repeatAmount.value = 0;
    repeatUnit.value = 'MINUTE';
    maxCount.value = 100;
    terminalStatuses.value = [];
    reportGenerationModes.value = ['MANUAL', 'AUTO'];
    if (record) {
      const trigger = parseJson(record.trigger_config, {});
      Object.assign(ruleForm, {
        name: record.name,
        scopeType: record.scope_type,
        scopeId: record.scope_id,
        notificationType: record.notification_type,
        triggerType: record.trigger_type,
        triggerConfig: trigger,
        cron: record.cron,
        timezone: record.timezone,
        template: record.template,
        recipientSpec: {
          ...defaultRule().recipientSpec,
          ...parseJson(record.recipient_spec, { chatIds: [], userIds: [] }),
        },
        deliveryMode: record.delivery_mode,
        stopConfig: parseJson(record.stop_config, {}),
        startAt: record.start_at,
        endAt: record.end_at,
      });
      leadAmount.value = trigger.leadTime ?? trigger.beforeMinutes ?? 60;
      leadUnit.value = trigger.leadUnit ?? 'MINUTE';
      repeatAmount.value = trigger.repeatInterval ?? trigger.repeatMinutes ?? 0;
      repeatUnit.value = trigger.repeatUnit ?? 'MINUTE';
      maxCount.value = trigger.maxCount ?? 100;
      terminalStatuses.value = trigger.terminalStatuses ?? [];
      reportGenerationModes.value = trigger.generationModes ?? ['MANUAL', 'AUTO'];
    }
    if (ruleForm.notificationType !== 'BUG_EXPECTED_RESOLUTION_DUE') {
      ruleForm.recipientSpec.businessRoles = [];
    }
    ruleVisible.value = true;
    loadRecipientUsers();
  }
  function copyRule(record: Record<string, any>) {
    openRule(record);
    editingRuleId.value = '';
    ruleForm.name = `${record.name} - ${t('common.copy')}`;
  }
  async function saveRule(done: (closed: boolean) => void) {
    if (!ruleForm.name || !ruleForm.template || (ruleForm.scopeType === 'PROJECT' && !ruleForm.scopeId)) {
      showRuleValidation(t('system.wecomBot.required'));
      done(false);
      return;
    }
    if (ruleForm.triggerType === 'EVENT' && !reportGenerationModes.value.length) {
      showRuleValidation(t('system.wecomBot.required'));
      done(false);
      return;
    }
    if (ruleForm.startAt && ruleForm.endAt && ruleForm.startAt >= ruleForm.endAt) {
      showRuleValidation(t('system.wecomBot.invalidPeriod'));
      done(false);
      return;
    }
    if (ruleForm.triggerType === 'CRON') {
      const cron = ruleForm.cron?.trim() || '';
      const fieldCount = cron ? cron.split(/\s+/).length : 0;
      if (fieldCount < 6 || fieldCount > 7) {
        showRuleValidation(t('system.wecomBot.invalidCron'));
        done(false);
        return;
      }
      ruleForm.cron = cron;
    }
    if (ruleForm.notificationType === 'TEST_REPORT_GENERATED' && !ruleForm.recipientSpec.chatIds.length) {
      showRuleValidation(t('system.wecomBot.reportGroupRequired'));
      done(false);
      return;
    }
    if (ruleForm.notificationType !== 'BUG_EXPECTED_RESOLUTION_DUE') {
      ruleForm.recipientSpec.businessRoles = [];
    }
    if (ruleForm.triggerType === 'DEADLINE') {
      ruleForm.triggerConfig = {
        leadTime: leadAmount.value,
        leadUnit: leadUnit.value,
        repeatInterval: repeatAmount.value,
        repeatUnit: repeatUnit.value,
        maxCount: maxCount.value,
        deadlineBehavior: 'STOP_AT_DEADLINE',
        terminalStatuses: terminalStatuses.value,
      };
      ruleForm.stopConfig = { statuses: terminalStatuses.value };
    } else if (ruleForm.triggerType === 'EVENT') {
      ruleForm.triggerConfig = { generationModes: reportGenerationModes.value };
    } else {
      ruleForm.triggerConfig = {};
    }
    savingRule.value = true;
    const knownRuleIds = new Set(rules.value.map((item) => item.id));
    try {
      let savedId = editingRuleId.value;
      if (savedId) await updateWecomRule(savedId, ruleForm);
      else savedId = await createWecomRule(ruleForm);

      const savedRule = localRuleRow(savedId);
      upsertLocalRule(savedRule);
      try {
        await loadRules();
        if (!rules.value.some((item) => item.id === savedId)) upsertLocalRule(savedRule);
      } catch {
        upsertLocalRule(savedRule);
      }
      Message.success(t('common.saveSuccess'));
      done(true);
    } catch (error) {
      try {
        const latestRules = await getWecomRules();
        rules.value = latestRules;
        const persistedRule = editingRuleId.value
          ? latestRules.find((item) => item.id === editingRuleId.value && ruleMatchesForm(item))
          : latestRules.find((item) => !knownRuleIds.has(item.id) && ruleMatchesForm(item));
        if (persistedRule) {
          Message.success(t('common.saveSuccess'));
          done(true);
          return;
        }
      } catch {
        // Preserve the original save error when the recovery query is unavailable.
      }
      const appError = ensureAppError(error, t('api.apiRequestFailed'));
      Modal.error({
        title: t('system.wecomBot.saveFailed'),
        content: formatAppErrorMessage(appError),
      });
      done(false);
    } finally {
      savingRule.value = false;
    }
  }
  function insertVariable(variable: string) {
    ruleForm.template += variableLabel(variable);
  }
  async function toggleRule(record: Record<string, any>, value: string | number | boolean) {
    await setWecomRuleEnabled(record.id, Boolean(value));
    await loadRules();
  }
  async function previewRule(record: Record<string, any>) {
    const content = await previewWecomRule(record.id);
    Message.info({ content: content || t('system.wecomBot.emptyPreview'), duration: 6000 });
  }
  async function runRule(record: Record<string, any>) {
    const ids = await runWecomRule(record.id);
    if (!ids.length) return Message.warning(t('system.wecomBot.noRecipient'));
    Message.success(t('system.wecomBot.queued'));
  }
  async function openLogDetail(record: Record<string, any>) {
    logDetail.value = await getWecomMessage(record.id);
    logDetailVisible.value = true;
  }
  async function removeRule(record: Record<string, any>) {
    await deleteWecomRule(record.id);
    await loadRules();
  }
  async function retryMessage(record: Record<string, any>) {
    await retryWecomMessage(record.id);
    Message.success(t('system.wecomBot.queued'));
    await loadLogs();
  }
  async function changeLogPage(page: number) {
    logPage.value = page;
    await loadLogs();
  }

  onMounted(async () => {
    if (!appStore.projectList.length) await appStore.initProjectList();
    await loadActiveTab();
  });
</script>
