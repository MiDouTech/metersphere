<template>
  <nav v-if="links.length" aria-label="更多测试资产" class="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
    <RouterLink v-for="link in links" :key="link.path" :to="link.path" class="text-[var(--color-text-2)]">
      {{ link.title }}
    </RouterLink>
  </nav>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { useRouter } from 'vue-router';

  import { hasAnyPermission, hasPageVisible, hasRouteVisible } from '@/utils/permission';

  const router = useRouter();
  const links = computed(() =>
    [
      { key: 'environments', title: '测试环境' },
      { key: 'versions', title: '资产版本' },
      { key: 'relations', title: '关联追溯' },
      { key: 'common-steps', title: '公共步骤' },
      { key: 'apis', title: '接口资产' },
      { key: 'evidence', title: '执行证据' },
      { key: 'bugs', title: '缺陷资产' },
    ].flatMap(({ key, title }) => {
      const path = `/test-assets/${key}`;
      const { meta, name } = router.resolve(path);
      return hasAnyPermission(meta.roles as string[]) &&
        hasPageVisible(meta.resourceCode as string | undefined) &&
        hasRouteVisible(name)
        ? [{ path, title }]
        : [];
    })
  );
</script>
