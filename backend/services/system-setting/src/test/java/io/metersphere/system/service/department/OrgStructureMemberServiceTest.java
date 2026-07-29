package io.metersphere.system.service.department;

import io.metersphere.system.dto.department.OrgStructureMemberDetailDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OrgStructureMemberServiceTest {

    @Test
    void hideContactFields_clearsPhoneEmail_keepsWecomUserid() {
        OrgStructureMemberDetailDTO dto = new OrgStructureMemberDetailDTO();
        dto.setPhone("13800138001");
        dto.setEmail("orguser1@metersphere.io");
        dto.setWecomUserid("wx_user_001");

        OrgStructureMemberService service = new OrgStructureMemberService();
        service.hideContactFields(dto);

        Assertions.assertNull(dto.getPhone());
        Assertions.assertNull(dto.getEmail());
        Assertions.assertEquals("wx_user_001", dto.getWecomUserid());
    }
}
