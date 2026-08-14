import MSR from '@/api/http/index';
import Url from '@/api/requrls/setting/userAgent';

import type {
  AgentBridgeDevice,
  AgentBridgeInstallInfo,
  AgentBridgePackage,
  AgentBridgePairingStatus,
  UserAgentConnection,
  UserAgentConnectionImpact,
  UserAgentFeatureFlags,
  UserAgentProvider,
} from '@/models/setting/userAgent';

export const getUserAgentFeatures = () => MSR.get<UserAgentFeatureFlags>({ url: Url.features });
export const listUserAgentConnections = () => MSR.get<UserAgentConnection[]>({ url: Url.connections });
export const listAgentBridgeDevices = () => MSR.get<AgentBridgeDevice[]>({ url: Url.devices });
export const getAgentBridgeInstallInfo = () => MSR.get<AgentBridgeInstallInfo>({ url: Url.installInfo });
export const listAgentBridgePackages = () => MSR.get<AgentBridgePackage[]>({ url: Url.packages });
export const uploadAgentBridgePackage = (request: Record<string, unknown>, file: File) =>
  MSR.uploadFile<AgentBridgePackage>({ url: Url.packages }, { request, fileList: [file] });
export const activateAgentBridgePackage = (id: string) =>
  MSR.post<AgentBridgePackage>({ url: `${Url.packages}/${id}/activate` });
export const deactivateAgentBridgePackage = (id: string) =>
  MSR.post<AgentBridgePackage>({ url: `${Url.packages}/${id}/deactivate` });
export const deleteAgentBridgePackage = (id: string) => MSR.delete({ url: `${Url.packages}/${id}` });
export const downloadManagedAgentBridge = () =>
  MSR.get<BlobPart>(
    { url: Url.download, params: { osType: 'WINDOWS', architecture: 'X64' }, responseType: 'blob' },
    { isTransformResponse: false }
  );
export const downloadAgentBridgePackageById = (id: string) =>
  MSR.get<BlobPart>({ url: `${Url.packages}/${id}/download`, responseType: 'blob' }, { isTransformResponse: false });

export const createAgentBridgePairing = (data: { provider: UserAgentProvider; expectedDeviceName?: string }) =>
  MSR.post<{ pairingId: string; pairingCode: string; expiresAt: number }>({ url: Url.pairing, data });
export const getAgentBridgePairingStatus = (id: string) =>
  MSR.get<AgentBridgePairingStatus>({ url: `${Url.pairing}/${id}` });

export const createUserAgentConnection = (data: {
  provider: UserAgentProvider;
  deviceId: string;
  displayName?: string;
}) => MSR.post<UserAgentConnection>({ url: Url.connections, data });

export const revokeUserAgentConnection = (id: string) => MSR.post({ url: `${Url.connections}/${id}/revoke` });
export const getUserAgentConnection = (id: string) => MSR.get<UserAgentConnection>({ url: `${Url.connections}/${id}` });
export const getUserAgentConnectionImpact = (id: string) =>
  MSR.get<UserAgentConnectionImpact>({ url: `${Url.connections}/${id}/impact` });
export const deleteUserAgentConnection = (id: string) => MSR.delete({ url: `${Url.connections}/${id}` });

export const authorizeUserAgentConnection = (id: string) => MSR.post({ url: `${Url.connections}/${id}/authorize` });

export const revokeAgentBridgeDevice = (id: string) => MSR.post({ url: `${Url.devices}/${id}/revoke` });
