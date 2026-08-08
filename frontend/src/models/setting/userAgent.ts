export type UserAgentProvider = 'WORKBUDDY' | 'CODEX' | 'CURSOR';

export interface UserAgentFeatureFlags {
  enabled: boolean;
  workbuddy: boolean;
  codex: boolean;
  cursor: boolean;
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
