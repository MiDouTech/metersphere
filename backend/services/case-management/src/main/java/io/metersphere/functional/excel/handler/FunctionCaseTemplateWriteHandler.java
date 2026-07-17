package io.metersphere.functional.excel.handler;

import com.alibaba.excel.util.BooleanUtils;
import com.alibaba.excel.write.handler.RowWriteHandler;
import com.alibaba.excel.write.handler.context.RowWriteHandlerContext;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import io.metersphere.functional.excel.constants.FunctionalCaseImportFiled;
import io.metersphere.functional.excel.domain.FunctionalCaseExcelData;
import io.metersphere.sdk.constants.CustomFieldType;
import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.dto.sdk.TemplateCustomFieldDTO;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author wx
 */
public class FunctionCaseTemplateWriteHandler implements RowWriteHandler {

    Map<String, List<String>> customFieldOptionsMap;

    private Sheet sheet;
    private Drawing<?> drawingPatriarch;

    private Map<String, TemplateCustomFieldDTO> customField;
    private Map<String, Integer> fieldMap = new HashMap<>();

    public FunctionCaseTemplateWriteHandler(List<List<String>> headList, Map<String, List<String>> customFieldOptionsMap, Map<String, TemplateCustomFieldDTO> customFieldMap) {
        initIndex(headList);
        this.customFieldOptionsMap = customFieldOptionsMap;
        this.customField = customFieldMap;
    }

    private void initIndex(List<List<String>> headList) {
        int index = 0;
        for (List<String> list : headList) {
            for (String head : list) {
                this.fieldMap.put(head, index);
                index++;
            }
        }
    }

    @Override
    public void afterRowDispose(RowWriteHandlerContext context) {
        if (BooleanUtils.isTrue(context.getHead())) {
            sheet = context.getWriteSheetHolder().getSheet();
            drawingPatriarch = sheet.createDrawingPatriarch();

            for (Map.Entry<String, Integer> entry : fieldMap.entrySet()) {
                String head = entry.getKey();
                Integer index = entry.getValue();
                // 默认系统字段
                if (FunctionalCaseImportFiled.ID.containsHead(head)) {
                    setComment(index, Translator.get("excel.template.id"));
                    continue;
                }
                if (FunctionalCaseImportFiled.NAME.containsHead(head)) {
                    setComment(index, Translator.get("excel.template.name"));
                    continue;
                }
                if (FunctionalCaseImportFiled.MODULE.containsHead(head)) {
                    setComment(index, Translator.get("excel.template.module"));
                    continue;
                }
                if (FunctionalCaseImportFiled.PREREQUISITE.containsHead(head)) {
                    setComment(index, Translator.get("excel.template.prerequisite"));
                    continue;
                }
                if (FunctionalCaseImportFiled.TEXT_DESCRIPTION.containsHead(head)) {
                    setComment(index, Translator.get("excel.template.text_description"));
                    continue;
                }
                if (FunctionalCaseImportFiled.EXPECTED_RESULT.containsHead(head)) {
                    setComment(index, Translator.get("excel.template.expected_result"));
                    continue;
                }
                if (FunctionalCaseImportFiled.DESCRIPTION.containsHead(head)) {
                    setComment(index, Translator.get("excel.template.description"));
                    continue;
                }
                if (FunctionalCaseImportFiled.TAGS.containsHead(head)) {
                    setComment(index, Translator.get("excel.template.tag"));
                    continue;
                }
                if (FunctionalCaseImportFiled.CASE_EDIT_TYPE.containsHead(head)) {
                    setComment(index, Translator.get("excel.template.case_edit_type"));
                    continue;
                }

                // 用例等级（内置自定义字段）
                if (isPriorityHead(head)) {
                    List<String> options = customFieldOptionsMap.get(head);
                    if (CollectionUtils.isNotEmpty(options)) {
                        setComment(index, Translator.get("excel.template.priority")
                                .concat("：").concat(Translator.get("options")).concat(JSON.toJSONString(options)));
                    } else {
                        setComment(index, Translator.get("excel.template.priority"));
                    }
                    continue;
                }

                // 其他自定义字段：导入模板全部非必填
                if (customField.containsKey(head)) {
                    TemplateCustomFieldDTO templateCustomFieldDTO = customField.get(head);
                    List<String> strings = customFieldOptionsMap.get(head);
                    if (StringUtils.equalsAnyIgnoreCase(templateCustomFieldDTO.getType(), CustomFieldType.MULTIPLE_MEMBER.name(), CustomFieldType.MEMBER.name())) {
                        setComment(index, Translator.get("excel.template.not_required").concat(",").concat(Translator.get("excel.template.member")));
                    } else if (CollectionUtils.isNotEmpty(strings)) {
                        setComment(index, Translator.get("excel.template.not_required").concat("：").concat(Translator.get("options")).concat(JSON.toJSONString(strings)));
                    } else {
                        setComment(index, Translator.get("excel.template.not_required"));
                    }
                    continue;
                }

                // 兜底：保证每一列都有表头批注
                setComment(index, Translator.get("excel.template.not_required"));
            }
        }

    }

    private boolean isPriorityHead(String head) {
        if (StringUtils.equalsAny(head, FunctionalCaseExcelData.FUNCTIONAL_PRIORITY_KEY, Translator.get("custom_field.functional_priority"))) {
            return true;
        }
        TemplateCustomFieldDTO dto = customField.get(head);
        if (dto == null) {
            return false;
        }
        return StringUtils.equalsAny(dto.getInternalFieldKey(), FunctionalCaseExcelData.FUNCTIONAL_PRIORITY_KEY)
                || StringUtils.equalsAny(dto.getFieldKey(), FunctionalCaseExcelData.FUNCTIONAL_PRIORITY_KEY)
                || StringUtils.equalsAny(dto.getFieldName(), FunctionalCaseExcelData.FUNCTIONAL_PRIORITY_KEY, Translator.get("custom_field.functional_priority"));
    }

    private void setComment(Integer index, String text) {
        if (index == null || sheet == null || sheet.getRow(0) == null) {
            return;
        }
        Row headRow = sheet.getRow(0);
        Cell cell = headRow.getCell(index);
        if (cell == null) {
            cell = headRow.createCell(index);
        }
        Comment comment = drawingPatriarch.createCellComment(new XSSFClientAnchor(0, 0, 0, 0, index, 0, index + 1, 1));
        comment.setString(new XSSFRichTextString(text));
        cell.setCellComment(comment);
    }

    public static HorizontalCellStyleStrategy getHorizontalWrapStrategy() {
        WriteCellStyle contentWriteCellStyle = new WriteCellStyle();
        // 设置自动换行
        contentWriteCellStyle.setWrapped(true);
        return new HorizontalCellStyleStrategy(null, contentWriteCellStyle);
    }
}
