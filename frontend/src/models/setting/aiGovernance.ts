export interface AiProjectGovernance {
  projectId: string;
  allowedModelIds: string[];
  allowedResourceTypes: Array<'MODEL_API' | 'USER_AGENT'>;
  allowedAgentProviders: Array<'CODEX' | 'CURSOR' | 'WORKBUDDY'>;
  allowPersonalAgent: boolean;
  allowLocalAgentTools: boolean;
  maxAgentConcurrentTasks: number;
  maxAgentExecutionMinutes: number;
  dailyAgentExecutionLimit: number;
  fallbackModelId?: string;
  maxConcurrentTasks: number;
  monthlyTokenQuota: number;
  projectFileQuota: number;
  sessionFileLimit: number;
  singleFileLimit: number;
  usedTokens: number;
  usedFileBytes: number;
  activeTasks: number;
}
