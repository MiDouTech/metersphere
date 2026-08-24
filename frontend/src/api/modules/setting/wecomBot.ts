import MSR from '@/api/http/index';

interface ApiEnvelope<T> {
  code: number;
  data: T;
}

function unwrapApiData<T>(response: T | ApiEnvelope<T>): T {
  if (response && typeof response === 'object' && 'code' in response && 'data' in response) {
    return (response as ApiEnvelope<T>).data;
  }
  return response as T;
}

export interface WecomBotConfig {
  id?: string;
  name: string;
  botId: string;
  secretConfigured: boolean;
  enabled: boolean;
  status: string;
  lastConnectedAt?: number;
  lastHeartbeatAt?: number;
  lastErrorCode?: string;
  lastErrorMessage?: string;
}

export interface WecomChat {
  id: string;
  chat_id: string;
  chat_type: string;
  display_name: string;
  active: boolean | number;
  first_seen_at: number;
  last_seen_at: number;
  last_delivery_status?: string;
}

export interface WecomNotificationSchedule {
  id?: string;
  cycleType: 'DAILY' | 'WEEKLY';
  weekdays: number[];
  executionTime: string;
  timezone: string;
  enabled: boolean;
  nextFireTime?: number;
  lastFireTime?: number;
}

export interface WecomScheduleExecution {
  id: string;
  schedule_id: string;
  trigger_mode: 'SCHEDULE' | 'MANUAL';
  planned_fire_time: number;
  actual_start_time?: number;
  actual_finish_time?: number;
  status: 'RUNNING' | 'SUCCESS' | 'RETRY' | 'FAILED' | 'SKIPPED';
  attempts: number;
  max_attempts: number;
  target_count?: number;
  next_retry_at?: number;
  error_code?: string;
  error_message?: string;
}

export interface WecomRuleRequest {
  name: string;
  scopeType: 'SYSTEM' | 'PROJECT';
  scopeId?: string;
  notificationType: 'BUG_EXPECTED_RESOLUTION_DUE' | 'TEST_REPORT_GENERATED' | 'CUSTOM_CRON';
  triggerType: 'DEADLINE' | 'EVENT' | 'CRON';
  triggerConfig: Record<string, unknown>;
  cron?: string;
  timezone: string;
  template: string;
  recipientSpec: {
    chatIds: string[];
    userIds: string[];
    businessRoles?: string[];
    projectAllMembers?: boolean;
    positionIds?: string[];
    roleIds?: string[];
  };
  deliveryMode: 'USER' | 'CHAT' | 'BOTH';
  stopConfig: Record<string, unknown>;
  startAt?: number;
  endAt?: number;
  schedules: WecomNotificationSchedule[];
}

export interface WecomTemplateVariable {
  key: string;
  name: string;
  description: string;
  example: string;
}

const base = '/wecom-bot';

export const getWecomBotConfig = async () =>
  unwrapApiData(await MSR.get<WecomBotConfig | ApiEnvelope<WecomBotConfig>>({ url: `${base}/config` }));
export const saveWecomBotConfig = (data: { name: string; botId: string; secret?: string; secretRef?: string }) =>
  MSR.post<WecomBotConfig>({ url: `${base}/config`, data });
export const testWecomBotConnection = () => MSR.post({ url: `${base}/config/test-connection` });
export const setWecomBotEnabled = (enabled: boolean) =>
  MSR.post({ url: `${base}/config/${enabled ? 'enable' : 'disable'}` });
export const getWecomBotStatus = async () => unwrapApiData(await MSR.get({ url: `${base}/status` }));
export const getWecomChats = async () =>
  unwrapApiData(await MSR.get<WecomChat[] | ApiEnvelope<WecomChat[]>>({ url: `${base}/chats` }));
export const getWecomRecipientUsers = async (projectId?: string) => {
  type RecipientUser = { id: string; name: string; mapped: boolean | number };
  return unwrapApiData(
    await MSR.get<RecipientUser[] | ApiEnvelope<RecipientUser[]>>({
      url: `${base}/recipient-options/users`,
      params: { projectId },
    })
  );
};
export const getWecomRecipientRoles = async (projectId?: string) => {
  type RecipientRole = { id: string; name: string; type: 'SYSTEM' | 'ORGANIZATION' | 'PROJECT'; scope_id: string };
  return unwrapApiData(
    await MSR.get<RecipientRole[] | ApiEnvelope<RecipientRole[]>>({
      url: `${base}/recipient-options/roles`,
      params: { projectId },
    })
  );
};
export const getWecomRecipientPositions = async (projectId?: string) => {
  type RecipientPosition = { id: string; name: string; memberCount: number };
  return unwrapApiData(
    await MSR.get<RecipientPosition[] | ApiEnvelope<RecipientPosition[]>>({
      url: `${base}/recipient-options/positions`,
      params: { projectId },
    })
  );
};
export const previewWecomRecipients = async (data: WecomRuleRequest) =>
  unwrapApiData(
    await MSR.post<
      | { users: Record<string, any>[]; warnings: string[] }
      | ApiEnvelope<{ users: Record<string, any>[]; warnings: string[] }>
    >({ url: `${base}/recipient-options/preview`, data })
  );
export const getWecomTemplateVariables = async (notificationType: WecomRuleRequest['notificationType']) =>
  unwrapApiData(
    await MSR.get<WecomTemplateVariable[] | ApiEnvelope<WecomTemplateVariable[]>>({
      url: `${base}/template-variables/${notificationType}`,
    })
  );
export const getWecomBugTerminalStatuses = async () => {
  type BugTerminalStatus = { id: string; name: string; status_code: string };
  return unwrapApiData(
    await MSR.get<BugTerminalStatus[] | ApiEnvelope<BugTerminalStatus[]>>({
      url: `${base}/recipient-options/bug-terminal-statuses`,
    })
  );
};
export const renameWecomChat = (id: string, name: string) =>
  MSR.post({ url: `${base}/chats/${id}/rename`, data: { name } });
export const setWecomChatEnabled = (id: string, enabled: boolean) =>
  MSR.post({ url: `${base}/chats/${id}/${enabled ? 'enable' : 'disable'}` });
export const testWecomGroup = (chatId: string, content: string) =>
  MSR.post({ url: `${base}/messages/test-group`, data: { chatId, content } });
export const testWecomUser = (userId: string, content: string) =>
  MSR.post({ url: `${base}/messages/test-user`, data: { userId, content } });
export const getWecomRules = async () =>
  unwrapApiData(
    await MSR.get<Record<string, any>[] | ApiEnvelope<Record<string, any>[]>>({ url: `${base}/notification-rules` })
  );
export const createWecomRule = (data: WecomRuleRequest) =>
  MSR.post<string>({ url: `${base}/notification-rules`, data }, { errorMessageMode: 'none' });
export const updateWecomRule = (id: string, data: WecomRuleRequest) =>
  MSR.put({ url: `${base}/notification-rules/${id}`, data }, { errorMessageMode: 'none' });
export const deleteWecomRule = (id: string) => MSR.delete({ url: `${base}/notification-rules/${id}` });
export const setWecomRuleEnabled = (id: string, enabled: boolean) =>
  MSR.post({ url: `${base}/notification-rules/${id}/${enabled ? 'enable' : 'disable'}` });
export const previewWecomRule = (id: string, variables: Record<string, unknown> = {}) =>
  MSR.post<string>({ url: `${base}/notification-rules/${id}/preview`, data: { variables } });
export const runWecomRule = (id: string) => MSR.post<string[]>({ url: `${base}/notification-rules/${id}/run-once` });
export const runWecomSchedule = (id: string) =>
  MSR.post<string[]>({ url: `${base}/notification-schedules/${id}/run-once` });
export const getWecomSchedules = async (ruleId: string) =>
  unwrapApiData(
    await MSR.get<Record<string, any>[] | ApiEnvelope<Record<string, any>[]>>({
      url: `${base}/notification-rules/${ruleId}/schedules`,
    })
  );
export const createWecomSchedule = (ruleId: string, data: WecomNotificationSchedule) =>
  MSR.post<string>({ url: `${base}/notification-rules/${ruleId}/schedules`, data });
export const updateWecomSchedule = (id: string, data: WecomNotificationSchedule) =>
  MSR.put({ url: `${base}/notification-schedules/${id}`, data });
export const deleteWecomSchedule = (id: string) => MSR.delete({ url: `${base}/notification-schedules/${id}` });
export const setWecomScheduleEnabled = (id: string, enabled: boolean) =>
  MSR.post({ url: `${base}/notification-schedules/${id}/${enabled ? 'enable' : 'disable'}` });
export const getWecomScheduleExecutions = async (ruleId: string) =>
  unwrapApiData(
    await MSR.get<WecomScheduleExecution[] | ApiEnvelope<WecomScheduleExecution[]>>({
      url: `${base}/notification-rules/${ruleId}/schedule-executions`,
    })
  );
export const getWecomLogs = async (params: {
  page: number;
  pageSize: number;
  status?: string;
  eventType?: string;
  targetType?: string;
  ruleId?: string;
  startAt?: number;
  endAt?: number;
}) => {
  type LogPage = { list: Record<string, any>[]; total: number };
  return unwrapApiData(await MSR.get<LogPage | ApiEnvelope<LogPage>>({ url: `${base}/messages/logs`, params }));
};
export const retryWecomMessage = (id: string) => MSR.post({ url: `${base}/messages/${id}/retry` });
export const getWecomMessage = async (id: string) =>
  unwrapApiData(
    await MSR.get<Record<string, any> | ApiEnvelope<Record<string, any>>>({ url: `${base}/messages/${id}` })
  );
