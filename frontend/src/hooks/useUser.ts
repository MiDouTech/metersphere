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
      const userStore = useUserStore();
      await userStore.logout(silenceToast);
      const appStore = useAppStore();
      const currentRoute = router.currentRoute.value;
      // 清空顶部菜单
      appStore.setTopMenus([]);
      if (!silenceToast) {
        Message.success({ content: t('message.logoutSuccess'), duration: HTTP_MESSAGE_DURATION });
      }
      router.push({
        name: logoutTo && typeof logoutTo === 'string' ? logoutTo : 'login',
        query: noRedirect
          ? {}
          : {
              ...router.currentRoute.value.query,
              redirect: currentRoute.name as string,
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
    return window.location.hash.indexOf('login') > -1;
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
