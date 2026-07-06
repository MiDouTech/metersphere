package io.metersphere.system.controller;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.base.BaseTest;
import io.metersphere.system.dto.department.DepartmentTreeNode;
import io.metersphere.system.dto.department.OrgStructureMemberDetailDTO;
import io.metersphere.system.dto.department.OrgStructureMemberItemDTO;
import io.metersphere.system.utils.Pager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OrgStructureMemberControllerTests extends BaseTest {

    private static final String ADMIN_TREE = "/org-structure/departments/tree?organizationId=sys_default_organization_3";
    private static final String MEMBER_PAGE = "/org-structure/members/page?organizationId=sys_default_organization_3&current=1&pageSize=10";
    private static final String MEMBER_DETAIL = "/org-structure/members/org_structure_user_1?organizationId=sys_default_organization_3";

    @Test
    @Order(1)
    @Sql(scripts = {"/dml/init_sys_organization.sql", "/dml/init_org_structure_test_data.sql"},
            config = @SqlConfig(encoding = "utf-8", transactionMode = SqlConfig.TransactionMode.ISOLATED))
    public void adminTreeWithStats() throws Exception {
        requestGetPermissionTest(PermissionConstants.ORGANIZATION_MEMBER_READ, ADMIN_TREE);
        MvcResult mvcResult = requestGetWithOkAndReturn(ADMIN_TREE);
        List<DepartmentTreeNode> tree = getResultDataArray(mvcResult, DepartmentTreeNode.class);
        Assertions.assertEquals(1, tree.size());
        DepartmentTreeNode root = tree.getFirst();
        Assertions.assertEquals(4L, root.getTotalUserCount());
        Assertions.assertTrue(root.getChildren().stream().anyMatch(item -> "dept_test_disabled".equals(item.getId())));
    }

    @Test
    @Order(2)
    @Sql(scripts = {"/dml/init_sys_organization.sql", "/dml/init_org_structure_test_data.sql"},
            config = @SqlConfig(encoding = "utf-8", transactionMode = SqlConfig.TransactionMode.ISOLATED))
    public void memberPage() throws Exception {
        requestGetPermissionTest(PermissionConstants.ORGANIZATION_MEMBER_READ, MEMBER_PAGE);
        MvcResult mvcResult = requestGetWithOkAndReturn(MEMBER_PAGE);
        Pager pager = getResultData(mvcResult, Pager.class);
        List<OrgStructureMemberItemDTO> list = JSON.parseArray(JSON.toJSONString(pager.getList()), OrgStructureMemberItemDTO.class);
        Assertions.assertTrue(list.size() >= 4);
    }

    @Test
    @Order(3)
    @Sql(scripts = {"/dml/init_sys_organization.sql", "/dml/init_org_structure_test_data.sql"},
            config = @SqlConfig(encoding = "utf-8", transactionMode = SqlConfig.TransactionMode.ISOLATED))
    public void memberDetailMasked() throws Exception {
        requestGetPermissionTest(PermissionConstants.ORGANIZATION_MEMBER_READ, MEMBER_DETAIL);
        MvcResult mvcResult = requestGetWithOkAndReturn(MEMBER_DETAIL);
        OrgStructureMemberDetailDTO detail = getResultData(mvcResult, OrgStructureMemberDetailDTO.class);
        Assertions.assertEquals("138****8001", detail.getPhone());
        Assertions.assertEquals("o***1@metersphere.io", detail.getEmail());
        Assertions.assertEquals("wx****01", detail.getWecomUserid());
    }
}
