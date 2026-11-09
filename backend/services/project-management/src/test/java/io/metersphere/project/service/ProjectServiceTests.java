package io.metersphere.project.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageInterceptor;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import io.metersphere.project.mapper.ExtProjectMapper;
import io.metersphere.project.request.ProjectPageRequest;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.ProjectDTO;
import io.metersphere.system.mapper.UserRoleRelationMapper;
import io.metersphere.system.service.CommonProjectService;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTests {
    @Mock
    private ExtProjectMapper extProjectMapper;
    @Mock
    private UserRoleRelationMapper userRoleRelationMapper;
    @Mock
    private CommonProjectService commonProjectService;

    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService();
        ReflectionTestUtils.setField(service, "extProjectMapper", extProjectMapper);
        ReflectionTestUtils.setField(service, "userRoleRelationMapper", userRoleRelationMapper);
        ReflectionTestUtils.setField(service, "commonProjectService", commonProjectService);
    }

    @Test
    void disabledProjectCannotBeAccessedEvenWithGlobalPermission() {
        when(extProjectMapper.projectIsActive("project-1")).thenReturn(false);

        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            session.when(() -> SessionUtils.hasPermission(null, null,
                    PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ)).thenReturn(true);
            assertFalse(service.canAccessProject("project-1", "user-1"));
        }

        verify(userRoleRelationMapper, never()).countByExample(any());
        verify(extProjectMapper, never()).userHasProjectRelation(any(), any());
    }

    @Test
    void activeProjectCanBeAccessedWithGlobalPermission() {
        when(extProjectMapper.projectIsActive("project-1")).thenReturn(true);
        when(userRoleRelationMapper.countByExample(any())).thenReturn(0L);

        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            session.when(() -> SessionUtils.hasPermission(null, null,
                    PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ)).thenReturn(true);
            assertTrue(service.canAccessProject("project-1", "user-1"));
        }

        verify(extProjectMapper, never()).userHasProjectRelation(any(), any());
    }

    @Test
    void caseAssetProjectPageDelegatesPermissionIntersectionToMapper() {
        ProjectPageRequest request = new ProjectPageRequest();
        ProjectDTO project = new ProjectDTO();
        project.setId("project-1");
        when(userRoleRelationMapper.countByExample(any())).thenReturn(0L);
        when(extProjectMapper.pageCaseAssetProject(request, "user-1", false,
                PermissionConstants.FUNCTIONAL_CASE_READ)).thenReturn(List.of(project));
        when(commonProjectService.buildUserInfo(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ProjectDTO> result = service.pageCaseAssetProject(request, "user-1").getList();

        assertTrue(result.stream().anyMatch(item -> "project-1".equals(item.getId())));
        verify(extProjectMapper).pageCaseAssetProject(eq(request), eq("user-1"), eq(false),
                eq(PermissionConstants.FUNCTIONAL_CASE_READ));
    }

    @Test
    void projectPaginationStartsAfterPermissionSqlAndKeepsTheProjectTotal() {
        ProjectPageRequest request = new ProjectPageRequest();
        request.setCurrent(2);
        request.setPageSize(20);
        when(userRoleRelationMapper.countByExample(any())).thenAnswer(invocation -> {
            assertNull(PageHelper.getLocalPage(), "permission SQL must not consume the project page");
            return 1L;
        });
        when(extProjectMapper.pageUserProject(request, "user-1", true)).thenAnswer(invocation -> {
            var page = PageHelper.getLocalPage();
            assertNotNull(page);
            assertEquals(2, page.getPageNum());
            assertEquals(20, page.getPageSize());
            page.setTotal(57);
            PageHelper.clearPage(); // MyBatis' interceptor clears this after the target SQL.
            return List.of();
        });
        when(commonProjectService.buildUserInfo(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.pageUserProject(request, "user-1");

        assertEquals(57, result.getTotal());
        assertEquals(2, result.getCurrent());
        assertEquals(20, result.getPageSize());
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void caseAssetPaginationPreservesPermissionFilteringAndCleansUpOnFailure() {
        ProjectPageRequest request = new ProjectPageRequest();
        request.setCurrent(1);
        request.setPageSize(50);
        when(userRoleRelationMapper.countByExample(any())).thenAnswer(invocation -> {
            assertNull(PageHelper.getLocalPage());
            return 0L;
        });
        when(extProjectMapper.pageCaseAssetProject(request, "user-1", false,
                PermissionConstants.FUNCTIONAL_CASE_READ)).thenAnswer(invocation -> {
            assertEquals(50, PageHelper.getLocalPage().getPageSize());
            throw new IllegalStateException("target SQL failed");
        });

        assertThrows(IllegalStateException.class, () -> service.pageCaseAssetProject(request, "user-1"));
        assertNull(PageHelper.getLocalPage(), "a failed target query must not leak pagination into later SQL");
    }

    interface PaginationSql {
        @Select("SELECT COUNT(*) FROM (VALUES(1)) roles")
        long adminCount();

        @Select("SELECT CAST(X AS VARCHAR) id FROM SYSTEM_RANGE(1,57) WHERE #{empty}=FALSE ORDER BY X")
        List<ProjectDTO> projects(@Param("empty") boolean empty);
    }

    @Test
    void realPageInterceptorPagesTheTargetSqlForAdminMemberAndEmptyResults() {
        Configuration configuration = new Configuration(new Environment("msp25", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:msp25", "sa", "")));
        PageInterceptor interceptor = new PageInterceptor();
        Properties properties = new Properties();
        properties.setProperty("helperDialect", "h2");
        interceptor.setProperties(properties);
        configuration.addInterceptor(interceptor);
        configuration.addMapper(PaginationSql.class);
        try (var sqlSession = new SqlSessionFactoryBuilder().build(configuration).openSession();
             MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            PaginationSql sql = sqlSession.getMapper(PaginationSql.class);
            // Reproduce the old controller/service ordering with the real interceptor.
            try (Page<Object> premature = PageHelper.startPage(1, 20)) {
                sql.adminCount();
                assertEquals(1, premature.getTotal());
                assertEquals(57, sql.projects(false).size());
            }
            when(userRoleRelationMapper.countByExample(any())).thenAnswer(invocation -> sql.adminCount());
            when(commonProjectService.buildUserInfo(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(extProjectMapper.pageUserProject(any(), eq("user-1"), eq(true)))
                    .thenAnswer(invocation -> sql.projects(false));
            for (int pageSize : new int[]{20, 50}) {
                for (int current : new int[]{1, 2, 3}) {
                    ProjectPageRequest request = new ProjectPageRequest();
                    request.setCurrent(current);
                    request.setPageSize(pageSize);
                    var result = service.pageUserProject(request, "user-1");
                    assertEquals(57, result.getTotal());
                    assertEquals(Math.max(0, Math.min(pageSize, 57 - (current - 1) * pageSize)), result.getList().size());
                    if (!result.getList().isEmpty()) {
                        assertEquals(String.valueOf((current - 1) * pageSize + 1), result.getList().getFirst().getId());
                    }
                    assertNull(PageHelper.getLocalPage());
                }
            }
            // Ordinary membership still reaches the permission-filtered mapper, including true empty results.
            when(userRoleRelationMapper.countByExample(any())).thenAnswer(invocation -> sql.adminCount() - 1);
            when(extProjectMapper.pageCaseAssetProject(any(), eq("user-1"), eq(false),
                    eq(PermissionConstants.FUNCTIONAL_CASE_READ))).thenAnswer(invocation -> sql.projects(true));
            ProjectPageRequest request = new ProjectPageRequest();
            request.setCurrent(1);
            request.setPageSize(20);
            var empty = service.pageCaseAssetProject(request, "user-1");
            assertEquals(0, empty.getTotal());
            assertTrue(empty.getList().isEmpty());
            assertNull(PageHelper.getLocalPage());
        }
    }
}
