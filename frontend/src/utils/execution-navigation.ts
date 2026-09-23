import type { LocationQuery, LocationQueryRaw } from 'vue-router';

// Preserve business context only; never propagate credentials or arbitrary return URLs.
const queryKeys = [
  'pId',
  'projectId',
  'executionTaskId',
  'creating',
  'caseIds',
  'testPlanId',
  'planId',
  'assetType',
  'assetId',
  'assetName',
  'assetVersionId',
  'keyword',
  'status',
  'verdict',
  'executorChannel',
  'current',
  'pageSize',
  'view',
  'operationalStatus',
  'businessVerdict',
  'executorType',
  'executorId',
  'startTime',
  'endTime',
  'source',
];
export function safeExecutionQuery(query: LocationQuery): LocationQueryRaw {
  return Object.fromEntries(queryKeys.filter((key) => query[key] !== undefined).map((key) => [key, query[key]]));
}
export function executionLocation(query: LocationQuery) {
  const safe = safeExecutionQuery(query);
  if (!safe.pId && typeof query.projectId === 'string') safe.pId = query.projectId;
  const taskId = query.executionTaskId || query.taskId || query.id;
  if (typeof taskId === 'string' && /^[a-zA-Z0-9_-]+$/.test(taskId)) {
    delete safe.executionTaskId;
    return { name: 'AgentExecutionDetail', params: { id: taskId }, query: safe };
  }
  delete safe.executionTaskId;
  return { path: '/execution/tasks', query: safe };
}
