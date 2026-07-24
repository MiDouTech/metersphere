import MSR from '@/api/http/index';
import {
  MiduoSsoBridgeUrl,
  MiduoSsoCallbackUrl,
  MiduoSsoLogoutUrl,
  MiduoSsoStateUrl,
  MiduoSsoStatusUrl,
} from '@/api/requrls/sso/miduo';

/**
 * 米多星球 SSO（浏览器只中转 exchange token；sessionToken 不下发）。
 */

export interface MiduoSsoStatus {
  enabled: boolean;
  ready: boolean;
  localLoginEnabled: boolean;
  /** /#/login/admin 是否开启，默认 false */
  adminLoginEnabled?: boolean;
  reason?: string;
  message?: string;
}

export interface MiduoSsoCallbackBody {
  token: string;
  /** 登录桥场景必填；工作台快捷入口回跳可为空 */
  state?: string;
}

export function getMiduoSsoStatus() {
  return MSR.get<MiduoSsoStatus>({ url: MiduoSsoStatusUrl }, { ignoreCancelToken: true });
}

export function getMiduoSsoState() {
  return MSR.get<{ state: string }>({ url: MiduoSsoStateUrl });
}

export function postMiduoSsoCallback(data: MiduoSsoCallbackBody) {
  // 落地页会改 URL；勿被路由守卫 removeAllPending 取消
  return MSR.post({ url: MiduoSsoCallbackUrl, data }, { ignoreCancelToken: true });
}

export function postMiduoSsoLogout() {
  return MSR.post({ url: MiduoSsoLogoutUrl });
}

export function getMiduoBridgeUrl() {
  return MSR.get<{ url: string; state?: string }>({ url: MiduoSsoBridgeUrl });
}
