export type UserAgentProvider = 'WORKBUDDY' | 'CODEX' | 'CURSOR';

export interface UserAgentFeatureFlags {
  enabled: boolean;
  workbuddy: boolean;
  codex: boolean;
  cursor: boolean;
  providers?: Array<{
    provider: UserAgentProvider;
    configured: boolean;
    implemented: boolean;
    verified: boolean;
    available: boolean;
    reason: string;
  }>;
}

export interface UserAgentConnection {
  id: string;
  provider: UserAgentProvider;
  connectionMode: string;
  displayName: string;
  maskedAccount?: string;
  status: string;
  capabilities?: string;
  deviceId?: string;
  deviceName?: string;
  deviceStatus?: string;
  bridgeVersion?: string;
  expiresAt?: number;
  lastHealthTime?: number;
}

export interface AgentBridgeDevice {
  id: string;
  deviceName: string;
  status: string;
  bridgeVersion?: string;
  protocolVersion?: string;
  osType?: string;
  lastHeartbeatTime?: number;
}

export interface AgentBridgeInstallInfo {
  productName: string;
  windowsDownloadUrl: string;
  managedDownloadAvailable: boolean;
  managedDownloadPath: string;
  publishedVersion?: string;
  sha256?: string;
  sizeBytes?: number;
  minimumVersion: string;
  protocolScheme: string;
}

export interface UserAgentConnectionImpact {
  conversationCount: number;
  executionCount: number;
  activeExecutionCount: number;
}

export interface AgentBridgePackage {
  id: string;
  version: string;
  osType: 'WINDOWS' | 'MACOS' | 'LINUX';
  architecture: 'X64' | 'ARM64';
  fileName: string;
  sha256: string;
  sizeBytes: number;
  status: 'ACTIVE' | 'INACTIVE';
  description?: string;
  downloadCount: number;
  createUser: string;
  createTime: number;
  updateUser?: string;
  updateTime: number;
}

export interface AgentBridgePairingStatus {
  pairingId: string;
  provider: UserAgentProvider;
  status: 'PENDING' | 'CONSUMED' | 'EXPIRED';
  expiresAt: number;
  deviceId?: string;
  consumedAt?: number;
}
