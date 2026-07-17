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
  import { DEFAULT_ROUTE_NAME } from '@/router/constants';
  import { useUserStore } from '@/store';

  import type { LoginRes } from '@/models/user';

  const { t } = useI18n();
  const route = useRoute();
  const router = useRouter();
  const userStore = useUserStore();

  const loading = ref(true);
  const errorMessage = ref('');

  function goLogin() {
    router.replace({ name: 'login' });
  }

  /** 读完后去掉 URL 中的 token，避免停留在地址栏 */
  function stripSensitiveQuery() {
    const q = { ...route.query } as Record<string, string | string[] | undefined>;
    delete q.token;
    delete q.app_code;
    router.replace({ path: route.path, query: q }).catch(() => undefined);
  }

  onMounted(async () => {
    const token = String(route.query.token || '');
    const state = String(route.query.state || '');
    if (!token || !state) {
      loading.value = false;
      errorMessage.value = t('login.miduo.callback.missingParams');
      return;
    }
    stripSensitiveQuery();
    try {
      const res = (await postMiduoSsoCallback({ token, state })) as LoginRes;
      if (!res?.sessionId) {
        throw new Error(t('login.miduo.callback.error'));
      }
      userStore.qrCodeLogin(res);
      Message.success(t('login.form.login.success'));
      const redirect = (route.query.redirect as string) || DEFAULT_ROUTE_NAME;
      await router.replace({ name: redirect });
    } catch (e) {
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
