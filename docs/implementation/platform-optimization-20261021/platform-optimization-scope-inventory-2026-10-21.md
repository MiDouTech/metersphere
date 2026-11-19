# 平台优化源码范围盘点（2026-10-21）

目标系统：msp.ebcone.cn。扫描当前工作区，不代表线上版本完全一致。此清单为静态候选清单，命中位置仍需逐页确认权限、运行行为及验收证据；不将字符串命中等同于功能入口。

## 列表及刷新候选

| 文件 | 首次命中行 |
|---|---|
| `frontend/src/components/business/ms-associate-case/apiCaseTable.vue` | 2 |
| `frontend/src/components/business/ms-associate-case/apiTable.vue` | 2 |
| `frontend/src/components/business/ms-associate-case/caseTable.vue` | 2 |
| `frontend/src/components/business/ms-associate-case/scenarioCaseTable.vue` | 2 |
| `frontend/src/components/business/ms-bug-operation/bugCountPopover.vue` | 6 |
| `frontend/src/components/business/ms-case-associate/index.vue` | 116 |
| `frontend/src/components/business/ms-common-script/formApiImportDrawer.vue` | 79 |
| `frontend/src/components/business/ms-common-script/insertCommonScript.vue` | 33 |
| `frontend/src/components/business/ms-link-file/linkFileTable.vue` | 31 |
| `frontend/src/components/business/ms-user-group-comp/userTable.vue` | 3 |
| `frontend/src/components/pure/ms-form-table/index.vue` | 3 |
| `frontend/src/views/api-test/components/quoteSqlSourceDrawer.vue` | 22 |
| `frontend/src/views/api-test/management/components/importTaskDrawer.vue` | 14 |
| `frontend/src/views/api-test/management/components/management/api/apiTable.vue` | 22 |
| `frontend/src/views/api-test/management/components/management/api/preview/history.vue` | 9 |
| `frontend/src/views/api-test/management/components/management/api/preview/quote.vue` | 12 |
| `frontend/src/views/api-test/management/components/management/api/shareListDrawer.vue` | 30 |
| `frontend/src/views/api-test/management/components/management/case/caseTable.vue` | 31 |
| `frontend/src/views/api-test/management/components/management/case/tabContent/tabCaseChangeHistory.vue` | 9 |
| `frontend/src/views/api-test/management/components/management/case/tabContent/tabCaseDependency.vue` | 16 |
| `frontend/src/views/api-test/management/components/management/case/tabContent/tabCaseExecuteHistory.vue` | 14 |
| `frontend/src/views/api-test/management/components/management/mock/mockTable.vue` | 23 |
| `frontend/src/views/api-test/management/components/recycle/api/apiTable.vue` | 21 |
| `frontend/src/views/api-test/management/components/recycle/case/caseTable.vue` | 21 |
| `frontend/src/views/api-test/report/component/reportList.vue` | 22 |
| `frontend/src/views/api-test/scenario/components/changeHistory.vue` | 4 |
| `frontend/src/views/api-test/scenario/components/common/importApiDrawer/table.vue` | 15 |
| `frontend/src/views/api-test/scenario/components/common/quoteCsvDrawer.vue` | 11 |
| `frontend/src/views/api-test/scenario/components/executeHistory.vue` | 3 |
| `frontend/src/views/api-test/scenario/components/scenarioTable.vue` | 14 |
| `frontend/src/views/api-test/scenario/recycle/recycleTable.vue` | 23 |
| `frontend/src/views/bug-management/components/bugCaseTab.vue` | 16 |
| `frontend/src/views/bug-management/components/bugHistoryTab.vue` | 2 |
| `frontend/src/views/bug-management/index.vue` | 36 |
| `frontend/src/views/bug-management/recycle.vue` | 15 |
| `frontend/src/views/case-management/caseManagementFeature/components/addStep.vue` | 2 |
| `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue` | 134 |
| `frontend/src/views/case-management/caseManagementFeature/components/recycleCaseTable.vue` | 72 |
| `frontend/src/views/case-management/caseManagementFeature/components/tabContent/tabBug/bugList.vue` | 2 |
| `frontend/src/views/case-management/caseManagementFeature/components/tabContent/tabBug/tabDefect.vue` | 63 |
| `frontend/src/views/case-management/caseManagementFeature/components/tabContent/tabCase/tabCaseTable.vue` | 24 |
| `frontend/src/views/case-management/caseManagementFeature/components/tabContent/tabCaseReview.vue` | 15 |
| `frontend/src/views/case-management/caseManagementFeature/components/tabContent/tabChangeHistory.vue` | 11 |
| `frontend/src/views/case-management/caseManagementFeature/components/tabContent/tabDemand/associatedDemandTable.vue` | 2 |
| `frontend/src/views/case-management/caseManagementFeature/components/tabContent/tabDemand/thirdDemandDrawer.vue` | 31 |
| `frontend/src/views/case-management/caseManagementFeature/components/tabContent/tabDependency/preAndPostCaseDrawer.vue` | 87 |
| `frontend/src/views/case-management/caseManagementFeature/components/tabContent/tabDependency/tabDependency.vue` | 38 |
| `frontend/src/views/case-management/caseManagementFeature/components/tabContent/tabTestPlan.vue` | 15 |
| `frontend/src/views/case-management/caseReview/components/detail/caseTable.vue` | 47 |
| `frontend/src/views/case-management/caseReview/components/index/reviewTable.vue` | 28 |
| `frontend/src/views/case-management/components/linkDefectDrawer.vue` | 31 |
| `frontend/src/views/case-management/testReport/index.vue` | 18 |
| `frontend/src/views/project-management/commonScript/components/scriptDetailDrawer.vue` | 50 |
| `frontend/src/views/project-management/commonScript/index.vue` | 17 |
| `frontend/src/views/project-management/environmental/components/envParams/DatabaseTab.vue` | 17 |
| `frontend/src/views/project-management/environmental/components/envParams/HttpTab.vue` | 55 |
| `frontend/src/views/project-management/environmental/components/envParams/popUp/domain.vue` | 12 |
| `frontend/src/views/project-management/fileManagement/components/fileDetailDrawer.vue` | 170 |
| `frontend/src/views/project-management/fileManagement/components/rightBox.vue` | 43 |
| `frontend/src/views/project-management/messageManagement/components/messageList.vue` | 35 |
| `frontend/src/views/project-management/projectAndPermission/member/components/memberTable.vue` | 25 |
| `frontend/src/views/project-management/projectAndPermission/menuManagement/components/falseAlermRule.vue` | 22 |
| `frontend/src/views/project-management/projectAndPermission/menuManagement/menuManagement.vue` | 5 |
| `frontend/src/views/project-management/projectAndPermission/projectVersion/index.vue` | 63 |
| `frontend/src/views/project-management/projectAndPermission/userGroup/projectUserGroup.vue` | 16 |
| `frontend/src/views/project-management/projectAndPermission/userGroup/userDrawer.vue` | 27 |
| `frontend/src/views/project-management/template/components/templateManagement.vue` | 34 |
| `frontend/src/views/setting/organization/member/index.vue` | 43 |
| `frontend/src/views/setting/organization/project/components/userDrawer.vue` | 42 |
| `frontend/src/views/setting/organization/project/orgProject.vue` | 20 |
| `frontend/src/views/setting/organization/template/components/fieldSetting.vue` | 41 |
| `frontend/src/views/setting/organization/template/components/templateManagement.vue` | 38 |
| `frontend/src/views/setting/system/agentIntegration/index.vue` | 67 |
| `frontend/src/views/setting/system/config/components/authConfig.vue` | 18 |
| `frontend/src/views/setting/system/log/components/logCards.vue` | 109 |
| `frontend/src/views/setting/system/organizationAndProject/components/addUserModal.vue` | 23 |
| `frontend/src/views/setting/system/organizationAndProject/components/projectDrawer.vue` | 24 |
| `frontend/src/views/setting/system/organizationAndProject/components/systemOrganization.vue` | 2 |
| `frontend/src/views/setting/system/organizationAndProject/components/systemProject.vue` | 2 |
| `frontend/src/views/setting/system/organizationAndProject/components/userDrawer.vue` | 43 |
| `frontend/src/views/setting/system/orgStructure/components/MemberTable.vue` | 37 |
| `frontend/src/views/setting/system/orgStructure/components/SyncPanel.vue` | 38 |
| `frontend/src/views/setting/system/pluginManager/components/pluginTable.vue` | 27 |
| `frontend/src/views/setting/system/resourcePool/components/capacityDrawer.vue` | 60 |
| `frontend/src/views/setting/system/resourcePool/index.vue` | 21 |
| `frontend/src/views/setting/system/user/index.vue` | 35 |
| `frontend/src/views/taskCenter/component/batchTaskReportDrawer.vue` | 3 |
| `frontend/src/views/taskCenter/component/caseTaskDetailTable.vue` | 33 |
| `frontend/src/views/taskCenter/component/caseTaskTable.vue` | 16 |
| `frontend/src/views/taskCenter/component/systemTaskTable.vue` | 16 |
| `frontend/src/views/taskCenter/component/testPlanExecuteResultDrawer.vue` | 72 |
| `frontend/src/views/test-plan/report/component/reportList.vue` | 22 |
| `frontend/src/views/test-plan/report/detail/component/system-card/apiAndScenarioTable.vue` | 3 |
| `frontend/src/views/test-plan/report/detail/component/system-card/bugTable.vue` | 16 |
| `frontend/src/views/test-plan/report/detail/component/system-card/featureCaseTable.vue` | 3 |
| `frontend/src/views/test-plan/report/detail/component/system-card/reportDetailTable.vue` | 36 |
| `frontend/src/views/test-plan/report/detail/component/system-card/testSetTable.vue` | 2 |
| `frontend/src/views/test-plan/testPlan/components/planTable.vue` | 30 |
| `frontend/src/views/test-plan/testPlan/detail/apiCase/components/caseTable.vue` | 14 |
| `frontend/src/views/test-plan/testPlan/detail/apiScenario/components/scenarioTable.vue` | 14 |
| `frontend/src/views/test-plan/testPlan/detail/bugManagement/caseCountPopover.vue` | 8 |
| `frontend/src/views/test-plan/testPlan/detail/bugManagement/index.vue` | 21 |
| `frontend/src/views/test-plan/testPlan/detail/executeHistory/index.vue` | 3 |
| `frontend/src/views/test-plan/testPlan/detail/executeResultDrawer.vue` | 68 |
| `frontend/src/views/test-plan/testPlan/detail/featureCase/components/caseTable.vue` | 45 |
| `frontend/src/views/workbench/components/apiCaseTable.vue` | 8 |
| `frontend/src/views/workbench/components/bugTable.vue` | 12 |
| `frontend/src/views/workbench/components/caseReviewTable.vue` | 12 |
| `frontend/src/views/workbench/components/scenarioCaseTable.vue` | 8 |
| `frontend/src/views/workbench/components/testCaseTable.vue` | 8 |
| `frontend/src/views/workbench/components/testPlanTable.vue` | 30 |
| `frontend/src/views/workbench/homePage/components/apiChangeList.vue` | 25 |
| `frontend/src/views/workbench/homePage/components/waitReviewList.vue` | 25 |

候选文件数：113。

## 批量操作候选

| 文件 | 首次命中行 |
|---|---|
| `frontend/src/components/pure/ms-table/base-table.vue` | 258 |
| `frontend/src/views/api-test/management/components/management/api/apiTable.vue` | 25 |
| `frontend/src/views/api-test/management/components/management/case/caseTable.vue` | 33 |
| `frontend/src/views/api-test/management/components/management/mock/mockTable.vue` | 25 |
| `frontend/src/views/api-test/management/components/recycle/api/apiTable.vue` | 24 |
| `frontend/src/views/api-test/management/components/recycle/case/caseTable.vue` | 23 |
| `frontend/src/views/api-test/report/component/reportList.vue` | 29 |
| `frontend/src/views/api-test/scenario/components/scenarioTable.vue` | 17 |
| `frontend/src/views/api-test/scenario/recycle/recycleTable.vue` | 25 |
| `frontend/src/views/bug-management/index.vue` | 42 |
| `frontend/src/views/bug-management/recycle.vue` | 20 |
| `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue` | 142 |
| `frontend/src/views/case-management/caseManagementFeature/components/recycleCaseTable.vue` | 78 |
| `frontend/src/views/case-management/caseReview/components/detail/caseTable.vue` | 49 |
| `frontend/src/views/case-management/caseReview/components/index/reviewTable.vue` | 30 |
| `frontend/src/views/project-management/fileManagement/components/rightBox.vue` | 50 |
| `frontend/src/views/project-management/projectAndPermission/member/components/memberTable.vue` | 31 |
| `frontend/src/views/project-management/projectAndPermission/menuManagement/components/falseAlermRule.vue` | 26 |
| `frontend/src/views/setting/organization/member/index.vue` | 48 |
| `frontend/src/views/setting/system/user/index.vue` | 39 |
| `frontend/src/views/taskCenter/component/caseTaskDetailTable.vue` | 38 |
| `frontend/src/views/taskCenter/component/caseTaskTable.vue` | 21 |
| `frontend/src/views/taskCenter/component/systemTaskTable.vue` | 22 |
| `frontend/src/views/test-plan/report/component/reportList.vue` | 29 |
| `frontend/src/views/test-plan/testPlan/components/planTable.vue` | 41 |
| `frontend/src/views/test-plan/testPlan/detail/apiCase/components/caseTable.vue` | 18 |
| `frontend/src/views/test-plan/testPlan/detail/apiScenario/components/scenarioTable.vue` | 18 |
| `frontend/src/views/test-plan/testPlan/detail/featureCase/components/caseTable.vue` | 48 |

候选文件数：28。

## 模型功能候选

| 文件 | 首次命中行 |
|---|---|
| `frontend/src/api/modules/case-management/featureCase.ts` | 739 |
| `frontend/src/components/business/ms-ai-drawer/components/conversation.vue` | 282 |
| `frontend/src/components/business/ms-ai-drawer/components/conversationList.vue` | 17 |
| `frontend/src/components/pure/navbar/index.vue` | 70 |
| `frontend/src/config/pathMap.ts` | 38 |
| `frontend/src/router/routes/modules/caseManagement.ts` | 20 |
| `frontend/src/router/routes/modules/testAsset.ts` | 11 |
| `frontend/src/views/agent/capability.vue` | 33 |
| `frontend/src/views/agent/components/AiGovernancePanel.vue` | 10 |
| `frontend/src/views/api-test/management/components/management/case/caseTable.vue` | 16 |
| `frontend/src/views/case-management/caseGenerate/index.vue` | 36 |
| `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue` | 67 |
| `frontend/src/views/case-management/caseManagementFeature/locale/en-US.ts` | 374 |
| `frontend/src/views/case-management/caseManagementFeature/locale/zh-CN.ts` | 364 |
| `frontend/src/views/test-asset/components/TestAssetTabs.vue` | 19 |
| `frontend/src/views/test-asset/documents.vue` | 13 |

候选文件数：16。

## 路由模块

- `frontend/src/router/routes/base.ts`
- `frontend/src/router/routes/execution-settings.ts`
- `frontend/src/router/routes/index.ts`
- `frontend/src/router/routes/modules/agent.ts`
- `frontend/src/router/routes/modules/apiTest.ts`
- `frontend/src/router/routes/modules/bugManagement.ts`
- `frontend/src/router/routes/modules/caseManagement.ts`
- `frontend/src/router/routes/modules/execution.ts`
- `frontend/src/router/routes/modules/fullPage.ts`
- `frontend/src/router/routes/modules/projectManagement.ts`
- `frontend/src/router/routes/modules/setting.ts`
- `frontend/src/router/routes/modules/share.ts`
- `frontend/src/router/routes/modules/testAsset.ts`
- `frontend/src/router/routes/modules/testPlan.ts`
- `frontend/src/router/routes/modules/workbench.ts`
- `frontend/src/router/routes/types.ts`
