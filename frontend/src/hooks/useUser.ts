import { Message } from '@arco-design/web-vue';

import { useI18n } from '@/hooks/useI18n';
import router from '@/router';
import { WHITE_LIST } from '@/router/constants';
import { useAppStore, useUserStore } from '@/store';
import { endSessionExpiredHandling, HTTP_MESSAGE_DURATION } from '@/utils/httpMessage';

export default function useUser() {
  const { t } = useI18n();

  /**
   * @param logoutTo 跳转路由名
   * @param noRedirect 不带 redirect
   * @param silenceToast 静默：不弹「登出成功」（用于 401 被动踢下线）
   */
  const logout = async (logoutTo?: string, noRedirect?: boolean, silenceToast = false) => {
    try {
      if (!silenceToast) sessionStorage.setItem('msp_explicit_logout', '1');
      const userStore = useUserStore();
      let serverLogoutSucceeded = true;
      try {
        await userStore.logout(silenceToast);
      } catch {
        serverLogoutSucceeded = false;
        // logout() always clears local credentials, including when the expired server session rejects it.
      }
      const appStore = useAppStore();
      const currentRoute = router.currentRoute.value;
      // 清空顶部菜单
      appStore.setTopMenus([]);
      if (!silenceToast) {
        if (serverLogoutSucceeded) {
          Message.success({ content: t('message.logoutSuccess'), duration: HTTP_MESSAGE_DURATION });
        } else {
          Message.warning({
            content: '本机登录信息已清除，服务端退出未确认，请稍后重试。',
            duration: HTTP_MESSAGE_DURATION,
          });
        }
      }
      await router.replace({
        name: logoutTo && typeof logoutTo === 'string' ? logoutTo : 'login',
        query: noRedirect
          ? {}
          : {
              ...router.currentRoute.value.query,
              redirect: currentRoute.name as string,
              returnTo: currentRoute.fullPath,
            },
      });
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      endSessionExpiredHandling();
    }
  };

  const isLoginPage = () => {
    return ['login', 'loginAdmin'].includes(String(router.currentRoute.value.name));
  };

  const isWhiteListPage = () => {
    const currentRoute = router.currentRoute.value;
    return WHITE_LIST.some((e) => e.path.includes(currentRoute.path));
  };

  return {
    logout,
    isLoginPage,
    isWhiteListPage,
  };
}
