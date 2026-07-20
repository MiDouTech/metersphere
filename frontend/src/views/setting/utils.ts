import { Message } from '@arco-design/web-vue';

import MsButton from '@/components/pure/ms-button/index.vue';

import { getProjectInfo, switchProject } from '@/api/modules/project-management/project';
import { switchUserOrg } from '@/api/modules/system';
import { useI18n } from '@/hooks/useI18n';
import router from '@/router';
import { useUserStore } from '@/store';
import useAppStore from '@/store/modules/app';
import { getFirstRouteNameByPermission } from '@/utils/permission';

import type { LoginRes } from '@/models/user';

const { t } = useI18n();
const userStore = useUserStore();
const appStore = useAppStore();

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
  try {
    appStore.showLoading();
    // 切换组织
    if (organizationId && appStore.currentOrgId !== organizationId) {
      await switchUserOrg(organizationId, userStore.id || '');
      await userStore.isLogin(true);
    }
    // 切换项目：立即应用返回的会话用户，避免仅依赖二次 is-login 时权限未刷新
    const switchedUser = (await switchProject({
      projectId,
      userId: userStore.id || '',
    })) as LoginRes;
    if (switchedUser) {
      userStore.setInfo(switchedUser);
      if (switchedUser.lastOrganizationId) {
        appStore.setCurrentOrgId(switchedUser.lastOrganizationId);
      } else if (organizationId) {
        appStore.setCurrentOrgId(organizationId);
      }
    }
    appStore.setCurrentProjectId(projectId);
    await userStore.checkIsLogin(true);
    // 防止 autoSwitch / 异步时序把项目 ID 冲掉
    appStore.setCurrentProjectId(projectId);
    try {
      const project = await getProjectInfo(projectId);
      if (project) {
        appStore.setCurrentMenuConfig(project.moduleIds || []);
      }
    } catch (e) {
      // eslint-disable-next-line no-console
      console.log(e);
    }
    // 跳转到项目页面
    router.replace({
      name: getFirstRouteNameByPermission(router.getRoutes()),
      query: {
        orgId: appStore.currentOrgId,
        pId: projectId,
      },
    });
  } catch (error) {
    // eslint-disable-next-line no-console
    console.log(error);
  } finally {
    appStore.hideLoading();
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
