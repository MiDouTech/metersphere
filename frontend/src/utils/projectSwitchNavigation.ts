import { readonly, ref } from 'vue';

import type { RouteLocationNormalized, RouteLocationRaw, Router } from 'vue-router';

const pending = ref(false);
export const projectSwitchPending = readonly(pending);
let permittedFullPath: string | undefined;

/** Consume one exact transaction navigation before route effects, cancellation and context changes. */
export function consumeProjectSwitchNavigation(to: Pick<RouteLocationNormalized, 'fullPath'>) {
  if (!pending.value) return true;
  if (to.fullPath !== permittedFullPath) return false;
  permittedFullPath = undefined;
  return true;
}

export function beginProjectSwitchNavigation() {
  if (pending.value) return undefined;
  pending.value = true;
  return {
    async replace(router: Router, destination: RouteLocationRaw) {
      // Only authorize this transaction's exact navigation, including organization/project query.
      permittedFullPath = router.resolve(destination).fullPath;
      try {
        return await router.replace(destination);
      } finally {
        permittedFullPath = undefined;
      }
    },
    complete() {
      permittedFullPath = undefined;
      pending.value = false;
    },
  };
}
