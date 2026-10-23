import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const CaseDeletion: AppRouteRecordRaw = {
  path: '/case-deletion',
  name: 'CaseDeletion',
  component: DEFAULT_LAYOUT,
  meta: { hideInMenu: true, roles: ['FUNCTIONAL_CASE:READ+DELETE'] },
  children: [
    {
      path: 'confirm/:id',
      name: 'CaseDeletionConfirm',
      component: () => import('@/views/execution/case-delete-confirm.vue'),
      meta: { locale: '用例删除确认', hideInMenu: true, roles: ['FUNCTIONAL_CASE:READ+DELETE'] },
    },
  ],
};
export default CaseDeletion;
