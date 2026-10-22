import { computed, ref, watch } from 'vue';

import useLocalForage from '@/hooks/useLocalForage';
import { useAppStore, useUserStore } from '@/store';

/** Pagination for configuration APIs returning their complete authorized list. */
export default function useClientTablePaging(name: string, count: () => number, scope?: () => unknown) {
  const app = useAppStore();
  const user = useUserStore();
  const { getItem, setItem } = useLocalForage();
  const current = ref(1);
  const pageSize = ref(10);
  const sizes = [10, 20, 50, 100];
  let preferenceGeneration = 0;
  const key = computed(() => `ms:list-page-size:${user.id}:${name}`);
  watch(
    key,
    async () => {
      const generation = ++preferenceGeneration;
      pageSize.value = 10;
      current.value = 1;
      const saved = await getItem<number>(key.value, true);
      if (generation === preferenceGeneration && saved && sizes.includes(saved)) pageSize.value = saved;
    },
    { immediate: true }
  );
  watch(
    () => [app.currentProjectId, scope?.()],
    () => {
      current.value = 1;
    }
  );
  watch(
    () => [count(), pageSize.value],
    () => {
      current.value = Math.min(current.value, Math.max(1, Math.ceil(count() / pageSize.value)));
    }
  );
  return computed(() => ({
    pagination: {
      current: current.value,
      pageSize: pageSize.value,
      total: count(),
      pageSizeOptions: sizes,
      showTotal: true,
      showPageSize: true,
    },
    onPageChange: (page: number) => {
      current.value = page;
    },
    onPageSizeChange: (size: number) => {
      if (!sizes.includes(size)) return;
      ++preferenceGeneration;
      pageSize.value = size;
      current.value = 1;
      setItem(key.value, size, true);
    },
  }));
}
