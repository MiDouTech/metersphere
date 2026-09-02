package io.metersphere.system.mapper;

import io.metersphere.system.base.BaseTest;
import io.metersphere.system.dto.user.UserExcludeOptionDTO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ExtUserRoleRelationMapperTests extends BaseTest {

    @Resource
    private ExtUserRoleRelationMapper mapper;

    @Test
    void roleMemberOptionsMapNumericFlagToBoolean() {
        List<UserExcludeOptionDTO> options = mapper.selectRoleMemberOptions("admin", "system", null);

        assertFalse(options.isEmpty());
    }
}
