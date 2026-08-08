import { DirectiveBinding } from 'vue';

import { hasButtonVisible } from '@/utils/permission';

function normalize(binding: DirectiveBinding): { code?: string; permissions?: string[]; typeList?: string[] } {
  const { value } = binding;
  if (typeof value === 'string') {
    return { code: value };
  }
  return {
    code: value?.code,
    permissions: value?.permissions,
    typeList: value?.typeList,
  };
}

function checkVisiblePermission(el: HTMLElement, binding: DirectiveBinding) {
  const { code, permissions, typeList } = normalize(binding);
  if (!hasButtonVisible(code, permissions, typeList) && el.parentNode) {
    el.parentNode.removeChild(el);
  }
}

export default {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    checkVisiblePermission(el, binding);
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    checkVisiblePermission(el, binding);
  },
};
