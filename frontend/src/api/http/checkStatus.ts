import { h } from 'vue';
import { Message } from '@arco-design/web-vue';

import { useI18n } from '@/hooks/useI18n';
import useUser from '@/hooks/useUser';
import router from '@/router';
import { NO_RESOURCE_ROUTE_NAME } from '@/router/constants';
import useLicenseStore from '@/store/modules/setting/license';
import { HTTP_MESSAGE_DURATION, showHttpErrorMessage, tryBeginSessionExpiredHandling } from '@/utils/httpMessage';

import type { ErrorMessageMode } from '#/axios';

export default function checkStatus(
  status: number,
  msg: string,
  code?: number,
  errorMessageMode?: ErrorMessageMode,
  context?: Record<string, unknown>
): void {
  const { t } = useI18n();
  const licenseStore = useLicenseStore();
  const { logout, isLoginPage, isWhiteListPage } = useUser();
  let errMessage = '';
  switch (status) {
    case 400:
      errMessage = `${msg}`;
      break;
    case 401: {
      // 登录页只提示错误，不登出；业务页并发 401 只处理一次
      if (isLoginPage() || isWhiteListPage()) {
        errMessage = msg || t('api.errMsg401');
        break;
      }
      if (!tryBeginSessionExpiredHandling()) {
        return;
      }
      errMessage = msg || t('api.errMsg401');
      // 静默登出：避免「认证失败」后再弹「登出成功」
      logout(undefined, false, true);
      break;
    }
    case 403:
      if (router.currentRoute.value.name !== NO_RESOURCE_ROUTE_NAME) {
        router.push({
          name: NO_RESOURCE_ROUTE_NAME,
          query: {
            redirect: router.currentRoute.value.fullPath,
            permission: typeof context?.permissionCode === 'string' ? context.permissionCode : undefined,
          },
        });
      }
      break;
    // 404请求不存在
    case 404:
      errMessage = msg || t('api.errMsg404');
      break;
    case 405:
      errMessage = msg || t('api.errMsg405');
      break;
    case 408:
      errMessage = msg || t('api.errMsg408');
      break;
    case 500:
      if (code === 101511) {
        // 开源版创建用户超数量
        Message.error({
          content: () =>
            h(
              'div',
              {
                style: {
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px',
                },
              },
              [
                h('span', t('user.openSourceCreateUsersLimit')),
                h(
                  'a',
                  {
                    href: 'https://jinshuju.net/f/CzzAOe',
                    target: '_blank',
                    style: {
                      color: 'rgb(var(--primary-5))',
                    },
                  },
                  t('user.businessTry')
                ),
              ]
            ),
          duration: 0,
          closable: true,
        });
        return;
      }
      if (code === 101512) {
        // 企业版创建用户超数量
        Message.error({
          content: () =>
            h(
              'div',
              {
                style: {
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px',
                },
              },
              [
                h(
                  'span',
                  (licenseStore.licenseInfo?.license.count || 5) <= 5
                    ? t('user.businessCreateUsersLimitThirty')
                    : t('user.businessCreateUsersLimitMax', { count: licenseStore.licenseInfo?.license.count })
                ),
                h(
                  'a',
                  {
                    href: 'https://jinshuju.net/f/CzzAOe',
                    target: '_blank',
                    style: {
                      color: 'rgb(var(--primary-5))',
                    },
                  },
                  t('user.businessScaling')
                ),
              ]
            ),
          duration: 0,
          closable: true,
        });
        return;
      }
      errMessage = msg || t('api.errMsg500');
      break;
    case 501:
      errMessage = msg || t('api.errMsg501');
      break;
    case 502:
      errMessage = msg || t('api.errMsg502');
      break;
    case 503:
      errMessage = msg || t('api.errMsg503');
      break;
    case 504:
      errMessage = msg || t('api.errMsg504');
      break;
    case 505:
      errMessage = msg || t('api.errMsg505');
      break;
    default:
  }

  if (errMessage) {
    if (errorMessageMode === 'modal') {
      showHttpErrorMessage(errMessage, { mode: 'modal', title: t('api.errorTip') });
    } else if ((errorMessageMode || 'message') === 'message') {
      showHttpErrorMessage(errMessage, { duration: HTTP_MESSAGE_DURATION });
    }
  }
}
