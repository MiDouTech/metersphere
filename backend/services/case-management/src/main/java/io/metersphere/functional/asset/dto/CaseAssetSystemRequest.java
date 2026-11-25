package io.metersphere.functional.asset.dto;
import lombok.Data;
import java.util.List;
@Data
public class CaseAssetSystemRequest {
    private String name;
    private String parentId;
    private List<String> ids;
    private String systemId;
    private String moduleId;
}
