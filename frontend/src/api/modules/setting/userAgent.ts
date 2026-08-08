import MSR from '@/api/http/index';
import Url from '@/api/requrls/setting/userAgent';

import type {
  AgentBridgeDevice,
  UserAgentConnection,
  UserAgentFeatureFlags,
  UserAgentProvider,
} from '@/models/setting/userAgent';

export const getUserAgentFeatures = () => MSR.get<UserAgentFeatureFlags>({ url: Url.features });
export const listUserAgentConnections = () => MSR.get<UserAgentConnection[]>({ url: Url.connections });
export const listAgentBridgeDevices = () => MSR.get<AgentBridgeDevice[]>({ url: Url.devices });

export const createAgentBridgePairing = (data: { provider: UserAgentProvider; expectedDeviceName?: string }) =>
  MSR.post<{ pairingId: string; pairingCode: string; expiresAt: number }>({ url: Url.pairing, data });

export const createUserAgentConnection = (data: {
  provider: UserAgentProvider;
  deviceId: string;
  displayName?: string;
}) => MSR.post<UserAgentConnection>({ url: Url.connections, data });

export const revokeUserAgentConnection = (id: string) => MSR.post({ url: `${Url.connections}/${id}/revoke` });

export const authorizeUserAgentConnection = (id: string) => MSR.post({ url: `${Url.connections}/${id}/authorize` });

export const revokeAgentBridgeDevice = (id: string) => MSR.post({ url: `${Url.devices}/${id}/revoke` });
