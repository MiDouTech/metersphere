import MSR from '@/api/http/index';

import type { AiProjectGovernance } from '@/models/setting/aiGovernance';

const url = '/ai/governance';

export const getAiProjectGovernance = (projectId: string) =>
  MSR.get<AiProjectGovernance>({ url, params: { projectId } });

export const saveAiProjectGovernance = (data: AiProjectGovernance) => MSR.post<AiProjectGovernance>({ url, data });
