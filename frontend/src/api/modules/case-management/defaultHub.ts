import MSR from '@/api/http/index';

export function getDefaultHubProjectId() {
  return MSR.get<string>({ url: '/default-hub/default-project-id' });
}

export function triggerDefaultHubSync(data?: { projectId?: string }) {
  return MSR.post<{ jobId: string; status: string; progress: number }>({
    url: '/default-hub/sync',
    data: data || {},
  });
}

export function getDefaultHubSyncJob(jobId: string) {
  return MSR.get<{
    jobId: string;
    status: string;
    progress: number;
    successCount?: number;
    failCount?: number;
    errorMessage?: string;
  }>({ url: `/default-hub/sync/${jobId}` });
}
