package io.metersphere.functional.service;
import io.metersphere.functional.excel.util.CaseAssetExcelStepMetadata;
import io.metersphere.functional.excel.util.CaseStepSplitUtils;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Msp38StepMetadataTests {
 @Test void hiddenMetadataRetainsExactStepsAndNeverOverridesEditedVisibleCell() throws Exception {
  var parts=List.of("first\n[2]literal", " ", "last\n");
  try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
   var sheet=workbook.createSheet();sheet.createRow(0).createCell(0).setCellValue("步骤描述");var cell=sheet.createRow(1).createCell(0);cell.setCellValue(CaseStepSplitUtils.encodeExportSteps(parts));
   CaseAssetExcelStepMetadata.hideTransportCells(workbook);
   assertFalse(cell.getStringCellValue().contains("MSP-STEPS"));assertTrue(cell.getStringCellValue().contains("[2]literal"));
   var bytes=new ByteArrayOutputStream();workbook.write(bytes);
   var metadata=CaseAssetExcelStepMetadata.read(new ByteArrayInputStream(bytes.toByteArray()),0);
   var raw=new HashMap<Integer,String>();raw.put(0,cell.getStringCellValue().trim());
   CaseAssetExcelStepMetadata.restore(raw,1,metadata);assertEquals(parts,CaseStepSplitUtils.splitCell(raw.get(0)));
   raw.put(0,"edited visible content");CaseAssetExcelStepMetadata.restore(raw,1,metadata);assertEquals("edited visible content",raw.get(0));
  }
 }
 @Test void realWorkbookWhitespaceEditInvalidatesMetadataIncludingXls() throws Exception {
  for(org.apache.poi.ss.usermodel.Workbook workbook:List.of(new org.apache.poi.xssf.usermodel.XSSFWorkbook(),new org.apache.poi.hssf.usermodel.HSSFWorkbook())) {
   try(workbook) {
    var sheet=workbook.createSheet();sheet.createRow(0).createCell(0).setCellValue("步骤描述");var cell=sheet.createRow(1).createCell(0);
    var parts=List.of("line\n[2]literal");cell.setCellValue(CaseStepSplitUtils.encodeExportSteps(parts));CaseAssetExcelStepMetadata.hideTransportCells(workbook);
    var bytes=new ByteArrayOutputStream();workbook.write(bytes);
    var metadata=CaseAssetExcelStepMetadata.read(new ByteArrayInputStream(bytes.toByteArray()),0);assertFalse(metadata.isEmpty());
    var row=new HashMap<Integer,String>();row.put(0,cell.getStringCellValue().trim());CaseAssetExcelStepMetadata.restore(row,1,metadata);assertEquals(parts,CaseStepSplitUtils.splitCell(row.get(0)));
    cell.setCellValue(cell.getStringCellValue()+" ");bytes.reset();workbook.write(bytes);
    assertTrue(CaseAssetExcelStepMetadata.read(new ByteArrayInputStream(bytes.toByteArray()),0).isEmpty());
   }
  }
 }
 @Test void nearExcelCellLimitRetainsVisibleAndMetadataContent() throws Exception {
  try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
   var sheet=workbook.createSheet();sheet.createRow(0).createCell(0).setCellValue("步骤描述");var cell=sheet.createRow(1).createCell(0);
   var parts=List.of("x".repeat(32670)+"\n[2]literal");cell.setCellValue(CaseStepSplitUtils.encodeExportSteps(parts));CaseAssetExcelStepMetadata.hideTransportCells(workbook);
   assertTrue(cell.getStringCellValue().length()<=32767);var bytes=new ByteArrayOutputStream();workbook.write(bytes);
   var metadata=CaseAssetExcelStepMetadata.read(new ByteArrayInputStream(bytes.toByteArray()),0);var row=new HashMap<Integer,String>();row.put(0,cell.getStringCellValue().trim());CaseAssetExcelStepMetadata.restore(row,1,metadata);assertEquals(parts,CaseStepSplitUtils.splitCell(row.get(0)));
  }
 }
 @Test void forgedOrOverflowingMetadataCannotReplaceVisibleContent() throws Exception {
  try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
   var sheet=workbook.createSheet();sheet.createRow(0).createCell(0).setCellValue("步骤描述");var cell=sheet.createRow(1).createCell(0);
   cell.setCellValue(CaseStepSplitUtils.encodeExportSteps(List.of("visible\nbody")));CaseAssetExcelStepMetadata.hideTransportCells(workbook);
   String original=cell.getCellComment().getString().getString();String prefix=original.substring(0,original.indexOf("MSP-STEPS/1\n"));
   for(String payload:List.of(CaseStepSplitUtils.encodeExportSteps(List.of("different hidden content")),"MSP-STEPS/1\n[1:9223372036854775807]x\nMSP-STEPS-END")) {
    cell.getCellComment().setString(workbook.getCreationHelper().createRichTextString(prefix+payload));var bytes=new ByteArrayOutputStream();workbook.write(bytes);
    var error=assertThrows(io.metersphere.sdk.exception.MSException.class,()->CaseAssetExcelStepMetadata.read(new ByteArrayInputStream(bytes.toByteArray()),0));
    assertFalse(error.getMessage().contains("different hidden content"));
   }
  }
 }
}
