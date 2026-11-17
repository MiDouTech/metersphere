package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenScopeParser;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ProjectOriginalFileValidatorTests {
    static final String XMIND_JSON = "[{\"id\":\"sheet\",\"rootTopic\":{\"id\":\"topic\",\"title\":\"原件 instructions remain data\"}}]";
    static final String XMIND_XML = "<xmap-content xmlns=\"urn:xmind:xmap:xmlns:content:2.0\"><sheet id=\"sheet\"><topic id=\"topic\"><title>原件</title></topic></sheet></xmap-content>";
    static byte[] zip(Map<String, String> entries) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test void fileScopesAreExplicitAndNotInheritedFromWildcards() {
        for (String scope : Set.of(AgentTokenScope.PROJECT_FILE_READ, AgentTokenScope.PROJECT_FILE_WRITE, AgentTokenScope.PROJECT_FILE_DOWNLOAD)) {
            assertFalse(AgentTokenScopeParser.hasScope("AGENT_ALL;FUNCTIONAL_ALL;PROJECT_WRITE", scope));
            assertTrue(AgentTokenScopeParser.hasScope(scope, scope));
            assertEquals(scope, AgentTokenScopeParser.normalizeAndValidate(scope));
        }
    }

    @Test void rejectsUnsafeNamesAndKeepsChinese() {
        for (String name : new String[]{"", "../a.xlsx", "a\\b.xls", "a\n.xlsx", " a.xls", "a.xls ", "a.exe", "a".repeat(256) + ".xlsx"}) {
            assertThrows(MSException.class, () -> ProjectOriginalFileValidator.name(name));
        }
        assertDoesNotThrow(() -> ProjectOriginalFileValidator.name("评审原件 V3.XLSX"));
    }

    @Test void rejectsNegativeEmptyOversizeAndOverflowRanges() {
        assertDoesNotThrow(() -> ProjectOriginalFileValidator.range(2, 3, 5, 3));
        for (long[] range : new long[][]{{-1, 1, 5, 3}, {0, 0, 5, 3}, {0, 4, 5, 3}, {4, 2, 5, 3}, {Long.MAX_VALUE, 2, 5, 3}}) {
            assertThrows(MSException.class, () -> ProjectOriginalFileValidator.range(range[0], range[1], range[2], range[3]));
        }
    }

    @Test void originalXmindIsValidatedWithoutInterpretingItsContent() throws Exception {
        byte[] bytes = zip(Map.of("content.json", XMIND_JSON));
        assertDoesNotThrow(() -> ProjectOriginalFileValidator.container("评审.xmind", bytes));
        assertThrows(MSException.class, () -> ProjectOriginalFileValidator.container("假.xlsx", bytes));
        assertThrows(MSException.class, () -> ProjectOriginalFileValidator.container("假.xls", bytes));
        assertThrows(MSException.class, () -> ProjectOriginalFileValidator.container("空.xlsx", new byte[0]));
    }

    @Test void validatesXmindJsonAndLegacyXmlStructureWithoutExecutingContent() throws Exception {
        assertDoesNotThrow(() -> ProjectOriginalFileValidator.container("旧版.xmind", zip(Map.of("content.xml", XMIND_XML))));
        for (String invalid : new String[]{"", "not JSON at all", "{}", "[]", "[{\"id\":\"s\"}]", XMIND_JSON + " true"}) {
            byte[] bytes = zip(Map.of("content.json", invalid));
            assertThrows(MSException.class, () -> ProjectOriginalFileValidator.container("拒绝.xmind", bytes));
        }
        for (String invalid : new String[]{"<x/>", "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///unavailable'>]><x>&e;</x>",
                "<xmap-content xmlns=\"urn:xmind:xmap:xmlns:content:2.0\"><sheet id=\"s\"/></xmap-content>"}) {
            byte[] bytes = zip(Map.of("content.xml", invalid));
            assertThrows(MSException.class, () -> ProjectOriginalFileValidator.container("拒绝.xmind", bytes));
        }
    }

    @Test void rejectsMacroExternalEmbeddedAndTraversalContainers() throws Exception {
        for (String forbidden : Set.of("xl/vbaProject.bin", "xl/externalLinks/a.xml", "xl/embeddings/object.bin", "../content.xml")) {
            byte[] bytes = zip(Map.of("content.json", "[]", forbidden, "unsafe"));
            assertThrows(MSException.class, () -> ProjectOriginalFileValidator.container("评审.xmind", bytes));
        }
    }

    @Test void readsRealExcelSignaturesWithoutEvaluatingFormulas() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var book = new org.apache.poi.hssf.usermodel.HSSFWorkbook()) {
            book.createSheet("用例").createRow(0).createCell(0).setCellValue("用例名称");
            book.write(bytes);
        }
        assertDoesNotThrow(() -> ProjectOriginalFileValidator.container("原件.xls", bytes.toByteArray()));
        assertThrows(MSException.class, () -> ProjectOriginalFileValidator.container("假.xlsx", bytes.toByteArray()));
    }

    @Test void acceptsNamespacedWorkbookWithoutDimensionsAndRejectsDoctypeOrMacroType() throws Exception {
        String types = "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/></Types>";
        String workbook = "<ns0:workbook xmlns:ns0=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><ns0:sheets/></ns0:workbook>";
        byte[] valid = zip(Map.of("[Content_Types].xml", types, "xl/workbook.xml", workbook));
        assertDoesNotThrow(() -> ProjectOriginalFileValidator.container("原件.xlsx", valid));
        byte[] unsafe = zip(Map.of("[Content_Types].xml", types, "xl/workbook.xml", "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///unavailable'>]>" + workbook));
        assertThrows(MSException.class, () -> ProjectOriginalFileValidator.container("拒绝.xlsx", unsafe));
        byte[] macro = zip(Map.of("[Content_Types].xml", types.replace("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml", "application/vnd.ms-excel.sheet.macroEnabled.main+xml"), "xl/workbook.xml", workbook));
        assertThrows(MSException.class, () -> ProjectOriginalFileValidator.container("拒绝.xlsx", macro));
    }
}
