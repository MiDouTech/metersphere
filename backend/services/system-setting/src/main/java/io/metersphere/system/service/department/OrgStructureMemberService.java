package io.metersphere.system.service.department;

import io.metersphere.system.dto.department.OrgStructureMemberDetailDTO;
import io.metersphere.system.dto.department.OrgStructureMemberItemDTO;
import io.metersphere.system.dto.department.OrgStructureMemberPageRequest;
import io.metersphere.system.mapper.ExtOrgStructureMemberMapper;
import io.metersphere.system.utils.ServiceUtils;
import jakarta.annotation.Resource;
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
        // 使用 ServiceUtils 设置资源名，前端展示「成员不存在」而非裸 "not found"/「资源不存在」
        return hideContactFields(ServiceUtils.checkResourceExist(detail, "成员"));
    }

    /**
     * 全局隐藏手机/邮箱；企微 UserID 返回明文（同步关联标识）。
     */
    public OrgStructureMemberDetailDTO hideContactFields(OrgStructureMemberDetailDTO dto) {
        dto.setPhone(null);
        dto.setEmail(null);
        return dto;
    }
}
