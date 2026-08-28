export type ActionType =
  | "NAVIGATE" | "CLICK" | "FILL" | "SELECT" | "CHECK"
  | "UPLOAD" | "KEYBOARD" | "WAIT" | "SCROLL";

export type LocatorStrategy =
  | "TEST_ID" | "ROLE" | "LABEL" | "PLACEHOLDER"
  | "TEXT" | "CSS";

export interface WebLocator {
  strategy: LocatorStrategy;
  testId?: string;
  role?: string;
  name?: string;
  label?: string;
  placeholder?: string;
  text?: string;
  selector?: string;
}

export interface WebAction {
  contractVersion: "v1";
  id: string;
  type: ActionType;
  target?: WebLocator;
  value?: string;
  valueRef?: string;
  fileRef?: string;
  idempotencyKey: string;
  timeoutMs: number;
  retryable: boolean;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
}

export type AssertionType =
  | "TEXT" | "VISIBLE" | "ENABLED" | "CHECKED"
  | "ATTRIBUTE" | "COUNT" | "URL" | "TITLE";

export interface WebAssertion {
  contractVersion: "v1";
  type: AssertionType;
  target?: WebLocator;
  operator?: "EQUALS" | "CONTAINS" | "MATCHES" | "NOT_EQUALS";
  expected?: string;
  attribute?: string;
  timeoutMs: number;
}

export interface ExecutionStep {
  id: string;
  executionCaseId: string;
  caseId: string;
  pos: number;
  instruction?: string;
  expected?: string;
  actionJson?: string;
  assertionJson?: string;
  riskLevel?: string;
  retryable?: boolean;
  status?: string;
  attempt?: number;
}

export interface ExecutionCase {
  id: string;
  caseId: string;
  caseName?: string;
  status?: string;
  steps: ExecutionStep[];
}

export interface ExecutionTask {
  id: string;
  projectId: string;
  targetUrl?: string;
  loginMode?: string;
  policySnapshot?: string;
  credentialReferenceId?: string;
  executionParameterSnapshot?: string;
  cases: ExecutionCase[];
}

export interface RuntimeCredential { username: string; value: string; secretVersion?: string; expiresAt?: number; }
export interface TestDataLease { id: string; datasetId: string; dataKey: string; leaseToken: string; expiresAt: number; }

export interface LeaseAssignment {
  leaseId: string;
  leaseToken: string;
  expireTime: number;
  nextEventSequence: number;
  task: ExecutionTask;
}

export interface RunnerEvent {
  contractVersion: "v1";
  eventId: string;
  caseId?: string;
  stepId?: string;
  attempt: number;
  sequence: number;
  eventTime: number;
  level: "DEBUG" | "INFO" | "WARN" | "ERROR";
  eventType: string;
  message: string;
  artifactIds?: string[];
  sanitizedMetadata?: string;
}

export interface RunnerControl {
  taskStatus: string;
  command: "NONE" | "CANCEL" | "PAUSE" | "WAIT_LOGIN" | "CONTINUE";
  serverTime: number;
}

export interface ArtifactResponse {
  artifactId: string;
  purpose: string;
  sha256: string;
  sizeBytes: number;
  downloadPath: string;
}

export interface RunnerConfig {
  apiUrl: string;
  runnerId: string;
  runnerToken: string;
  allowedOrigins: ReadonlySet<string>;
  headless: boolean;
  pollIntervalMs: number;
  values: Readonly<Record<string, string>>;
  uploadRoot?: string;
  sensitiveSelectors: readonly string[];
}
