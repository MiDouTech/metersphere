import { useStorage } from '@vueuse/core';

import useUserStore from '@/store/modules/user';

/**
 * 功能用例「自动下一条」本机偏好（B1）
 * 键：ms.functionalCase.autoNext.{userId}，默认 false
 */
export default function useFeatureCaseAutoNext() {
  const userStore = useUserStore();
  const key = `ms.functionalCase.autoNext.${userStore.id || 'anonymous'}`;
  const autoNext = useStorage(key, false);
  return { autoNext };
}
