package io.metersphere.system.service.department;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import io.metersphere.system.dto.department.OrgStructureMemberDetailDTO;
import io.metersphere.system.dto.department.OrgStructureMemberItemDTO;
import io.metersphere.system.dto.department.OrgStructureMemberPageRequest;
import io.metersphere.system.mapper.ExtOrgStructureMemberMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrgStructureMemberService {

    @Resource
    private ExtOrgStructureMemberMapper extOrgStructureMemberMapper;
    @Resource
    private OrgStructureAccessService orgStructureAccessService;

    public List<OrgStructureMemberItemDTO> page(OrgStructureMemberPageRequest request) {
        orgStructureAccessService.validateReadable(request.getOrganizationId());
        return extOrgStructureMemberMapper.pageMembers(request);
    }

    public OrgStructureMemberDetailDTO detail(String userId, String organizationId) {
        orgStructureAccessService.validateReadable(organizationId);
        OrgStructureMemberDetailDTO detail = extOrgStructureMemberMapper.getMemberDetail(userId, organizationId);
        if (detail == null) {
            throw new MSException(MsHttpResultCode.NOT_FOUND);
        }
        return maskSensitive(detail);
    }

    public OrgStructureMemberDetailDTO maskSensitive(OrgStructureMemberDetailDTO dto) {
        dto.setPhone(maskPhone(dto.getPhone()));
        dto.setEmail(maskEmail(dto.getEmail()));
        dto.setWecomUserid(maskWecomUserid(dto.getWecomUserid()));
        return dto;
    }

    public static String maskPhone(String phone) {
        if (StringUtils.isBlank(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String maskEmail(String email) {
        if (StringUtils.isBlank(email) || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@", 2);
        String username = parts[0];
        if (username.length() <= 2) {
            return "*@" + parts[1];
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1) + "@" + parts[1];
    }

    public static String maskWecomUserid(String wecomUserid) {
        if (StringUtils.isBlank(wecomUserid) || wecomUserid.length() <= 4) {
            return wecomUserid;
        }
        return wecomUserid.substring(0, 2) + "****" + wecomUserid.substring(wecomUserid.length() - 2);
    }
}
