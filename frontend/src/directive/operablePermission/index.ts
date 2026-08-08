import { DirectiveBinding } from 'vue';
import { Message } from '@arco-design/web-vue';

import { hasButtonOperable } from '@/utils/permission';

type OperablePermissionValue =
  | string
  | {
      code?: string;
      permissions?: string[];
      typeList?: string[];
    };

const HANDLER_KEY = '__msUiPermissionClickHandler__';
const DISABLED_BY_PERMISSION_KEY = '__msUiPermissionDisabled__';

function normalize(binding: DirectiveBinding<OperablePermissionValue>) {
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

function removePermissionHandler(el: HTMLElement) {
  const handler = (el as any)[HANDLER_KEY];
  if (handler) {
    el.removeEventListener('click', handler, true);
    delete (el as any)[HANDLER_KEY];
  }
}

function setDisabled(el: HTMLElement, disabled: boolean) {
  const target = el as HTMLButtonElement & Record<string, any>;
  if (disabled) {
    target.disabled = true;
    target.setAttribute('aria-disabled', 'true');
    target.setAttribute('title', '无操作权限');
    target.classList.add('is-disabled');
    target[DISABLED_BY_PERMISSION_KEY] = true;
    return;
  }
  if (target[DISABLED_BY_PERMISSION_KEY]) {
    target.disabled = false;
    target.removeAttribute('aria-disabled');
    target.removeAttribute('title');
    target.classList.remove('is-disabled');
    delete target[DISABLED_BY_PERMISSION_KEY];
  }
}

function checkOperablePermission(el: HTMLElement, binding: DirectiveBinding<OperablePermissionValue>) {
  const { code, permissions, typeList } = normalize(binding);
  const operable = hasButtonOperable(code, permissions, typeList);
  removePermissionHandler(el);
  setDisabled(el, !operable);
  if (!operable) {
    const handler = (event: Event) => {
      event.preventDefault();
      event.stopImmediatePropagation();
      Message.warning('无操作权限');
    };
    (el as any)[HANDLER_KEY] = handler;
    el.addEventListener('click', handler, true);
  }
}

export default {
  mounted(el: HTMLElement, binding: DirectiveBinding<OperablePermissionValue>) {
    checkOperablePermission(el, binding);
  },
  updated(el: HTMLElement, binding: DirectiveBinding<OperablePermissionValue>) {
    checkOperablePermission(el, binding);
  },
  unmounted(el: HTMLElement) {
    removePermissionHandler(el);
  },
};
