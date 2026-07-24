package io.metersphere.functional.excel.domain;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.metadata.data.WriteCellData;
import io.metersphere.functional.excel.constants.FunctionalCaseImportFiled;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.dto.sdk.TemplateCustomFieldDTO;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * @author wx
 */
@Getter
@Setter
public class FunctionalCaseExcelData {
    public static final String FUNCTIONAL_PRIORITY_KEY = "functional_priority";

    @ExcelIgnore
    private String num;
    @ExcelIgnore
    private String name;
    @ExcelIgnore
    private String module;
    @ExcelIgnore
    private String tags;
    @ExcelIgnore
    private String prerequisite;
    @ExcelIgnore
    private String description;
    @ExcelIgnore
    private String executeUser;
    /**
     * 导入校验通过后解析出的用户 ID（非 Excel 列）
     */
    @ExcelIgnore
    private String executeUserId;
    @ExcelIgnore
    private String textDescription;
    @ExcelIgnore
    private String expectedResult;
    @ExcelIgnore
    private String caseEditType;
    @ExcelIgnore
    private String steps;
    @ExcelIgnore
    Map<String, Object> customData = new LinkedHashMap<>();
    @ExcelIgnore
    Map<String, String> otherFields;

    @ExcelIgnore
    private WriteCellData<String> hyperLinkName;

    /**
     * 合并文本描述
     */
    @ExcelIgnore
    List<String> MergeTextDescription;
    /**
     * 合并步骤结果
     */
    @ExcelIgnore
    List<String> mergeExpectedResult;


    public List<List<String>> getHead(List<TemplateCustomFieldDTO> customFields) {
        return new ArrayList<>();
    }

    /**
     * 下载导入模板表头（全部非必填）：
     * 用例ID|用例名称|所属模块|前置条件|步骤描述|预期结果|用例等级|执行人|备注
     * 标签、编辑模式及其他自定义字段仅兼容导入，不出现在下载模板中。
     */
    public List<List<String>> getHead(List<TemplateCustomFieldDTO> customFields, Locale lang) {
        List<List<String>> heads = new ArrayList<>();
        FunctionalCaseImportFiled[] templateFields = {
                FunctionalCaseImportFiled.ID,
                FunctionalCaseImportFiled.NAME,
                FunctionalCaseImportFiled.MODULE,
                FunctionalCaseImportFiled.PREREQUISITE,
                FunctionalCaseImportFiled.TEXT_DESCRIPTION,
                FunctionalCaseImportFiled.EXPECTED_RESULT
        };
        for (FunctionalCaseImportFiled field : templateFields) {
            heads.add(Arrays.asList(field.getFiledLangMap().get(lang)));
        }

        heads.add(Arrays.asList(resolvePriorityFieldName(customFields)));
        heads.add(Arrays.asList(FunctionalCaseImportFiled.EXECUTE_USER.getFiledLangMap().get(lang)));
        heads.add(Arrays.asList(FunctionalCaseImportFiled.DESCRIPTION.getFiledLangMap().get(lang)));
        return heads;
    }

    /**
     * 解析用例等级列名：优先用项目默认模板中的内置字段展示名。
     */
    public static String resolvePriorityFieldName(List<TemplateCustomFieldDTO> customFields) {
        TemplateCustomFieldDTO priority = findPriorityField(customFields);
        if (priority != null && StringUtils.isNotBlank(priority.getFieldName())) {
            return priority.getFieldName();
        }
        return Translator.get("custom_field.functional_priority");
    }

    public static TemplateCustomFieldDTO findPriorityField(List<TemplateCustomFieldDTO> customFields) {
        if (CollectionUtils.isEmpty(customFields)) {
            return null;
        }
        String i18nName = Translator.get("custom_field.functional_priority");
        for (TemplateCustomFieldDTO dto : customFields) {
            if (StringUtils.equalsAny(dto.getInternalFieldKey(), FUNCTIONAL_PRIORITY_KEY)
                    || StringUtils.equalsAny(dto.getFieldKey(), FUNCTIONAL_PRIORITY_KEY)
                    || StringUtils.equalsAny(dto.getFieldName(), FUNCTIONAL_PRIORITY_KEY, i18nName)) {
                return dto;
            }
        }
        return null;
    }
}
