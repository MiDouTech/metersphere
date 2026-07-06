package io.metersphere.system.controller;

import io.metersphere.sdk.constants.UserRoleType;
import io.metersphere.system.base.BaseTest;
import io.metersphere.system.dto.department.DepartmentTreeNode;
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

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DepartmentControllerTests extends BaseTest {

    private static final String DEPARTMENT_TREE = "/department/tree?organizationId=sys_default_organization_3";
    private static final String DEPARTMENT_TREE_OTHER_ORG = "/department/tree?organizationId=sys_default_organization_7";

    @Test
    @Order(1)
    @Sql(scripts = {"/dml/init_sys_organization.sql", "/dml/init_org_structure_test_data.sql"},
            config = @SqlConfig(encoding = "utf-8", transactionMode = SqlConfig.TransactionMode.ISOLATED))
    public void treeSuccess() throws Exception {
        MvcResult mvcResult = requestGetWithOkAndReturn(DEPARTMENT_TREE);
        List<DepartmentTreeNode> tree = getResultDataArray(mvcResult, DepartmentTreeNode.class);
        Assertions.assertEquals(1, tree.size());
        Assertions.assertEquals("dept_test_root", tree.getFirst().getId());
        Assertions.assertEquals(2, tree.getFirst().getChildren().size());
        Assertions.assertNull(tree.getFirst().getDirectUserCount());
    }

    @Test
    @Order(2)
    @Sql(scripts = {"/dml/init_sys_organization.sql", "/dml/init_org_structure_test_data.sql"},
            config = @SqlConfig(encoding = "utf-8", transactionMode = SqlConfig.TransactionMode.ISOLATED))
    public void treeCrossOrganizationForbidden() throws Exception {
        requestGetWithNoAdmin(DEPARTMENT_TREE_OTHER_ORG, UserRoleType.ORGANIZATION.name())
                .andExpect(status().isForbidden());
    }
}
