import { isNavigationFailure } from 'vue-router';
import { Message } from '@arco-design/web-vue';

import MsButton from '@/components/pure/ms-button/index.vue';

import { getProjectInfo, switchProject } from '@/api/modules/project-management/project';
import { switchUserOrg } from '@/api/modules/system';
import { useI18n } from '@/hooks/useI18n';
import router from '@/router';
import { useUserStore } from '@/store';
import useAppStore from '@/store/modules/app';
import { getProjectEntryRouteName } from '@/utils/permission';
import { beginProjectSwitchNavigation } from '@/utils/projectSwitchNavigation';

import type { LoginRes } from '@/models/user';

export { projectSwitchPending } from '@/utils/projectSwitchNavigation';

const { t } = useI18n();
const userStore = useUserStore();
const appStore = useAppStore();

const PROJECT_SWITCH_ROUTE = 'projectSwitchPending';

// 进入组织
export async function enterOrganization(organizationId: string) {
  try {
    appStore.showLoading();
    if (appStore.currentOrgId !== organizationId) {
      await switchUserOrg(organizationId, userStore.id || '');
      await userStore.isLogin();
      await userStore.checkIsLogin(true);
    }
  } catch (error) {
    console.log(error);
  } finally {
    appStore.hideLoading();
  }
}

export async function enterProject(projectId: string, organizationId?: string) {
  const navigation = beginProjectSwitchNavigation();
  if (!navigation) return false;
  const previous = {
    route: router.currentRoute.value.fullPath,
    projectId: appStore.currentProjectId,
    organizationId: appStore.currentOrgId,
    modules: [...appStore.currentMenuConfig],
  };
  let leftPage = false;
  let sessionMayHaveChanged = false;
  let requiresReauthentication = false;
  try {
    appStore.showLoading();
    // Run the real leave guards BEFORE changing session, permissions or project.
    // This route has no business components or target-project query parameters.
    const leaveFailure = await navigation.replace(router, { name: PROJECT_SWITCH_ROUTE });
    if (isNavigationFailure(leaveFailure) || router.currentRoute.value.name !== PROJECT_SWITCH_ROUTE) return false;
    leftPage = true;
    // 切换组织
    if (organizationId && appStore.currentOrgId !== organizationId) {
      sessionMayHaveChanged = true;
      await switchUserOrg(organizationId, userStore.id || '');
    }
    // 切换项目：立即应用返回的会话用户，避免仅依赖二次 is-login 时权限未刷新
    sessionMayHaveChanged = true;
    const switchedUser = (await switchProject({
      projectId,
      userId: userStore.id || '',
    })) as LoginRes;
    if (
      !switchedUser ||
      switchedUser.lastProjectId !== projectId ||
      (organizationId && switchedUser.lastOrganizationId !== organizationId)
    ) {
      throw new Error('Project switch did not return the target session');
    }
    userStore.setInfo(switchedUser);
    appStore.setCurrentOrgId(switchedUser.lastOrganizationId || organizationId || previous.organizationId);
    appStore.setCurrentProjectId(projectId);
    // Unknown configuration must never be treated as an enabled module.
    const project = await getProjectInfo(projectId);
    if (!project || !Array.isArray(project.moduleIds)) {
      throw new Error('Target project configuration is unavailable');
    }
    appStore.setCurrentMenuConfig(project.moduleIds);
    // 跳转到项目页面
    const destination = getProjectEntryRouteName(router.getRoutes());
    const configuredRedirect = router.getRoutes().find((item) => item.name === destination)?.redirect;
    const expectedRoute = router.resolve(
      typeof configuredRedirect === 'string' || (configuredRedirect && typeof configuredRedirect === 'object')
        ? configuredRedirect
        : { name: destination }
    );
    const navigationFailure = await navigation.replace(router, {
      path: expectedRoute.path,
      query: {
        orgId: appStore.currentOrgId,
        pId: projectId,
      },
    });
    if (
      isNavigationFailure(navigationFailure) ||
      router.currentRoute.value.path !== expectedRoute.path ||
      router.currentRoute.value.query.pId !== projectId ||
      router.currentRoute.value.query.orgId !== appStore.currentOrgId
    ) {
      throw new Error('Project navigation did not complete');
    }
    return true;
  } catch (error) {
    // eslint-disable-next-line no-console
    console.log(error);
    try {
      if (sessionMayHaveChanged) {
        // A rejected/timed-out response does not prove the server session stayed unchanged.
        if (!previous.projectId || previous.projectId === 'no_such_project') {
          throw new Error('No previous project session can be restored');
        }
        if (previous.organizationId) await switchUserOrg(previous.organizationId, userStore.id || '');
        const restoredUser = (await switchProject({
          projectId: previous.projectId,
          userId: userStore.id || '',
        })) as LoginRes;
        if (
          !restoredUser ||
          restoredUser.lastProjectId !== previous.projectId ||
          (previous.organizationId && restoredUser.lastOrganizationId !== previous.organizationId)
        ) {
          throw new Error('Previous project session was not restored');
        }
        userStore.setInfo(restoredUser);
      }
      appStore.setCurrentOrgId(previous.organizationId);
      appStore.setCurrentProjectId(previous.projectId);
      appStore.setCurrentMenuConfig(previous.modules);
      if (leftPage) {
        const restoreFailure = await navigation.replace(router, previous.route);
        if (isNavigationFailure(restoreFailure) || router.currentRoute.value.fullPath !== previous.route) {
          throw new Error('Previous route was not restored');
        }
      }
      Message.error('进入项目失败，已保留原项目，请重试');
    } catch (restoreError) {
      // Do not let any business page operate with an uncertain server/client session.
      requiresReauthentication = true;
      userStore.logoutCallBack();
      appStore.setCurrentProjectId('');
      appStore.setCurrentOrgId('');
      appStore.setCurrentMenuConfig([]);
      Message.error('项目切换及恢复失败，请重新登录');
      window.location.replace(`${window.location.origin}${window.location.pathname}#/login`);
    }
    return false;
  } finally {
    appStore.hideLoading();
    // Keep the barrier until the new document is loaded if recovery was not verified.
    if (!requiresReauthentication) navigation.complete();
  }
}

export function showUpdateOrCreateMessage(isEdit: boolean, id: string, organizationId?: string) {
  if (isEdit) {
    Message.success(t('system.project.updateProjectSuccess'));
  } else {
    // 创建人已写入项目管理员；始终提供「进入项目」，不依赖当前上下文的 PROJECT_BASE_INFO
    Message.success({
      content: () =>
        h('div', { class: 'flex items-center gap-[12px]' }, [
          h('div', t('system.project.createProjectSuccess')),
          h(
            MsButton,
            {
              type: 'text',
              onClick() {
                enterProject(id, organizationId);
              },
            },
            { default: () => t('system.project.enterProject') }
          ),
        ]),
    });
  }
}
