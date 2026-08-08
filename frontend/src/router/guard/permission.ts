import { getTestReportProject } from '@/api/modules/case-management/testReport';
import usePermission from '@/hooks/usePermission';
import useAppStore from '@/store/modules/app';

import { CaseManagementRouteEnum, ShareEnum } from '@/enums/routeEnum';

import { featureRouteMap, NO_RESOURCE_ROUTE_NAME, WHITE_LIST } from '../constants';
import NProgress from 'nprogress'; // progress bar
import type { Router } from 'vue-router';

export default function setupPermissionGuard(router: Router) {
  router.beforeEach(async (to, from, next) => {
    const appStore = useAppStore();
    if (to.name === CaseManagementRouteEnum.CASE_MANAGEMENT_TEST_REPORT_DETAIL && to.query.id) {
      try {
        const { projectId, hasProjectPermission } = await getTestReportProject(to.query.id as string);
        if (!hasProjectPermission) {
          next({
            name: ShareEnum.SHARE_FUNCTIONAL_TEST_REPORT,
            query: { id: to.query.id },
            replace: true,
          });
          return;
        }
        if (projectId) {
          appStore.setCurrentProjectId(projectId);
          if (to.query.pId !== projectId) {
            next({
              name: to.name as string,
              params: to.params,
              query: {
                ...to.query,
                pId: projectId,
              },
              hash: to.hash,
              replace: true,
            });
            return;
          }
        }
      } catch (error) {
        // eslint-disable-next-line no-console
        console.log(error);
      }
    }
    const Permission = usePermission();
    const permissionsAllow = Permission.accessRouter(to);
    // 模块未启用：仅在菜单配置已加载时拦截（空配置表示尚未拉取，避免误跳无权限页）
    const moduleId = Object.keys(featureRouteMap).find((key) => (to.name as string)?.includes(key));
    if (
      appStore.currentMenuConfig.length > 0 &&
      moduleId &&
      featureRouteMap[moduleId] &&
      !appStore.currentMenuConfig.includes(featureRouteMap[moduleId])
    ) {
      next({
        name: NO_RESOURCE_ROUTE_NAME,
      });
      NProgress.done();
      return;
    }
    const exist = WHITE_LIST.find((el) => el.name === to.name);
    if (exist || permissionsAllow) {
      next();
    } else {
      next({
        name: NO_RESOURCE_ROUTE_NAME,
      });
    }
    NProgress.done();
  });
}
