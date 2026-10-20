import { RouteLocationNormalized, RouteRecordRaw } from 'vue-router';

import { useUserStore } from '@/store';
import { hasAnyPermission, hasPageVisible, hasRouteVisible, topLevelMenuHasPermission } from '@/utils/permission';

/**
 * 用户权限
 * @returns 调用方法
 */
export default function usePermission() {
  const firstLevelMenu = ['testPlan', 'bugManagement', 'caseManagement', 'apiTest'];
  const userStore = useUserStore();

  return {
    /**
     * 是否为允许访问的路由
     * @param route 路由信息
     * @returns 是否
     */
    accessRouter(route: RouteLocationNormalized | RouteRecordRaw) {
      if (route.name === 'ExecutionQuality') {
        // The parent aggregates its real child grants; legacy project menu grants
        // must not block a system-only quality policy administrator.
        return (
          (hasAnyPermission(['SYSTEM_QUALITY:READ'], ['SYSTEM']) &&
            hasPageVisible('SYSTEM_QUALITY_POLICY_PAGE', ['SYSTEM']) &&
            hasRouteVisible('SystemQualityPolicy', ['SYSTEM'])) ||
          (hasAnyPermission(['AI_EXECUTION:READ']) &&
            hasPageVisible('FUNCTIONAL_CASE_AUTOMATION_EXECUTION_TAB') &&
            hasRouteVisible('executionTasks')) ||
          hasAnyPermission(['AI_CREDENTIAL:READ_METADATA'])
        );
      }
      if (route.meta?.adminOnly && !userStore.isAdmin) {
        return false;
      }
      if (firstLevelMenu.includes(route.name as string)) {
        // 一级菜单: 创建项目时 被勾选的模块
        return topLevelMenuHasPermission(route);
      }
      return (
        route.meta?.requiresAuth === false ||
        !route.meta?.roles ||
        route.meta?.roles?.includes('*') ||
        (hasPageVisible(route.meta?.resourceCode) &&
          hasRouteVisible(route.name) &&
          hasAnyPermission(route.meta?.roles || []))
      );
    },
    // You can add any rules you want
  };
}
