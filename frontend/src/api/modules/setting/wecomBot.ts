import MSR from '@/api/http/index';

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
    projectRoleIds?: string[];
    userGroupIds?: string[];
  };
  deliveryMode: 'USER' | 'CHAT' | 'BOTH';
  stopConfig: Record<string, unknown>;
  startAt?: number;
  endAt?: number;
}

const base = '/wecom-bot';

export const getWecomBotConfig = () => MSR.get<WecomBotConfig>({ url: `${base}/config` });
export const saveWecomBotConfig = (data: { name: string; botId: string; secret?: string; secretRef?: string }) =>
  MSR.post<WecomBotConfig>({ url: `${base}/config`, data });
export const testWecomBotConnection = () => MSR.post({ url: `${base}/config/test-connection` });
export const setWecomBotEnabled = (enabled: boolean) =>
  MSR.post({ url: `${base}/config/${enabled ? 'enable' : 'disable'}` });
export const getWecomBotStatus = () => MSR.get({ url: `${base}/status` });
export const getWecomChats = () => MSR.get<WecomChat[]>({ url: `${base}/chats` });
export const getWecomRecipientUsers = (projectId?: string) =>
  MSR.get<{ id: string; name: string; mapped: boolean | number }[]>({
    url: `${base}/recipient-options/users`,
    params: { projectId },
  });
export const getWecomRecipientRoles = (projectId?: string) =>
  MSR.get<{ id: string; name: string; type: 'SYSTEM' | 'ORGANIZATION' | 'PROJECT'; scope_id: string }[]>({
    url: `${base}/recipient-options/roles`,
    params: { projectId },
  });
export const getWecomBugTerminalStatuses = () =>
  MSR.get<{ id: string; name: string; status_code: string }[]>({
    url: `${base}/recipient-options/bug-terminal-statuses`,
  });
export const renameWecomChat = (id: string, name: string) =>
  MSR.post({ url: `${base}/chats/${id}/rename`, data: { name } });
export const setWecomChatEnabled = (id: string, enabled: boolean) =>
  MSR.post({ url: `${base}/chats/${id}/${enabled ? 'enable' : 'disable'}` });
export const testWecomGroup = (chatId: string, content: string) =>
  MSR.post({ url: `${base}/messages/test-group`, data: { chatId, content } });
export const testWecomUser = (userId: string, content: string) =>
  MSR.post({ url: `${base}/messages/test-user`, data: { userId, content } });
export const getWecomRules = () => MSR.get<Record<string, any>[]>({ url: `${base}/notification-rules` });
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
export const getWecomLogs = (params: {
  page: number;
  pageSize: number;
  status?: string;
  eventType?: string;
  targetType?: string;
  ruleId?: string;
  startAt?: number;
  endAt?: number;
}) => MSR.get<{ list: Record<string, any>[]; total: number }>({ url: `${base}/messages/logs`, params });
export const retryWecomMessage = (id: string) => MSR.post({ url: `${base}/messages/${id}/retry` });
export const getWecomMessage = (id: string) => MSR.get<Record<string, any>>({ url: `${base}/messages/${id}` });
