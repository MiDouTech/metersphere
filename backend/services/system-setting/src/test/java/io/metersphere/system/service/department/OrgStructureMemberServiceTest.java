package io.metersphere.system.service.department;

import io.metersphere.system.dto.department.OrgStructureMemberDetailDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OrgStructureMemberServiceTest {

    @Test
    void maskSensitive_fields() {
        OrgStructureMemberDetailDTO dto = new OrgStructureMemberDetailDTO();
        dto.setPhone("13800138001");
        dto.setEmail("orguser1@metersphere.io");
        dto.setWecomUserid("wx_user_001");

        OrgStructureMemberService service = new OrgStructureMemberService();
        service.maskSensitive(dto);

        Assertions.assertEquals("138****8001", dto.getPhone());
        Assertions.assertEquals("o***1@metersphere.io", dto.getEmail());
        Assertions.assertEquals("wx****01", dto.getWecomUserid());
    }

    @Test
    void maskSensitive_shortValues() {
        Assertions.assertEquals("123", OrgStructureMemberService.maskPhone("123"));
        Assertions.assertEquals("*@x.com", OrgStructureMemberService.maskEmail("a@x.com"));
        Assertions.assertEquals("ab", OrgStructureMemberService.maskWecomUserid("ab"));
    }
}
