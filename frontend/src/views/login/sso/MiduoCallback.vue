<template>
  <div class="miduo-callback">
    <a-spin :loading="loading" :tip="t('login.miduo.callback.loading')">
      <div v-if="errorMessage" class="miduo-callback__error">
        <div class="miduo-callback__title">{{ t('login.miduo.callback.error') }}</div>
        <div class="miduo-callback__msg">{{ errorMessage }}</div>
        <a-button type="primary" class="mt-4" @click="goLogin">{{ t('login.form.login') }}</a-button>
      </div>
    </a-spin>
  </div>
</template>

<script lang="ts" setup>
  /**
   * 米多 SSO 落地页（QUERY）：只中转 exchange token，立即 POST 后端。
   * 禁止把 token / sessionToken 写入 localStorage。
   */
  import { onMounted, ref } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';

  import { postMiduoSsoCallback } from '@/api/modules/sso/miduo';
  import { useI18n } from '@/hooks/useI18n';
  import { useUserStore } from '@/store';
  import { getFirstRouteNameByPermission, routerNameHasPermission } from '@/utils/permission';

  import type { LoginRes } from '@/models/user';

  const { t } = useI18n();
  const route = useRoute();
  const router = useRouter();
  const userStore = useUserStore();

  const loading = ref(true);
  const errorMessage = ref('');

  function getLoginTarget() {
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '';
    const routes = router.getRoutes();
    return redirect && routerNameHasPermission(redirect, routes) ? redirect : getFirstRouteNameByPermission(routes);
  }

  async function goLogin() {
    loading.value = true;
    errorMessage.value = '';
    try {
      const loggedIn = await userStore.isLogin();
      await router.replace({ name: loggedIn ? getLoginTarget() : 'login' });
    } catch (e) {
      errorMessage.value = (e as Error)?.message || t('login.miduo.callback.error');
    } finally {
      loading.value = false;
    }
  }

  /**
   * 用 history.replaceState 清掉地址栏 token，避免触发 Vue Router beforeEach →
   * removeAllPending，把进行中的 callback 请求取消成 canceled。
   */
  function stripSensitiveQueryQuietly() {
    const url = new URL(window.location.href);
    url.searchParams.delete('token');
    url.searchParams.delete('app_code');
    // Hash 路由：#/sso/miduo/callback?token=...
    const hash = url.hash || '';
    const hashQ = hash.indexOf('?');
    if (hashQ >= 0) {
      const pathPart = hash.slice(0, hashQ);
      const params = new URLSearchParams(hash.slice(hashQ + 1));
      params.delete('token');
      params.delete('app_code');
      const rest = params.toString();
      url.hash = rest ? `${pathPart}?${rest}` : pathPart;
    }
    window.history.replaceState(window.history.state, '', url.toString());
  }

  onMounted(async () => {
    // Hash 路由下，米多白名单回跳常为 /?token=...（search），需兼容；hash query 优先
    const search = new URLSearchParams(window.location.search);
    const token = String(route.query.token || search.get('token') || '');
    const state = String(route.query.state || search.get('state') || '');
    if (!token) {
      loading.value = false;
      errorMessage.value = t('login.miduo.callback.missingParams');
      return;
    }
    try {
      // ① 先完成 callback，再清 URL（避免路由取消请求）
      const res = (await postMiduoSsoCallback({ token, state: state || undefined })) as LoginRes;
      // ② 不走 router.replace，用 history API 静默去敏
      stripSensitiveQueryQuietly();
      if (!res?.sessionId) {
        throw new Error(t('login.miduo.callback.error'));
      }
      userStore.qrCodeLogin(res);
      // 登录成功后清掉重认证跳转标记，避免上次失败残留导致再次被踢
      sessionStorage.removeItem('miduo_reauth_redirecting');
      Message.success(t('login.form.login.success'));
      await router.replace({ name: getLoginTarget() });
    } catch (e) {
      stripSensitiveQueryQuietly();
      errorMessage.value = (e as Error)?.message || t('login.miduo.callback.error');
    } finally {
      loading.value = false;
    }
  });
</script>

<style scoped lang="less">
  .miduo-callback {
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 100vh;
    background: var(--color-bg-1);
  }
  .miduo-callback__error {
    padding: 24px;
    text-align: center;
  }
  .miduo-callback__title {
    margin-bottom: 8px;
    font-size: 18px;
    font-weight: 600;
  }
  .miduo-callback__msg {
    max-width: 420px;
    color: var(--color-text-3);
  }
</style>
