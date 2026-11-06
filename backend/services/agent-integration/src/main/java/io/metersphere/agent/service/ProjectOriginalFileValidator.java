package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipInputStream;

/** Validates original containers only. Never evaluates workbook or XMind content. */
public final class ProjectOriginalFileValidator {
    private ProjectOriginalFileValidator() { }

    public static void name(String name) {
        if (name == null || name.isBlank() || name.length() > 255
                || !name.equals(name.strip()) || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '/' || c == '\\')
                || !name.toLowerCase(Locale.ROOT).matches(".+\\.(xls|xlsx|xmind)")) {
            throw new ProjectFileException("VALIDATION_ERROR:fileName");
        }
    }

    public static void container(String name, byte[] bytes) {
        name(name);
        if (bytes.length == 0) throw new MSException("UNSUPPORTED_FORMAT");
        String lower = name.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".xls")) {
                try (var fs = new POIFSFileSystem(new ByteArrayInputStream(bytes))) {
                    var root = fs.getRoot();
                    if ((!root.hasEntry("Workbook") && !root.hasEntry("Book"))
                            || root.hasEntry("EncryptedPackage") || root.hasEntry("EncryptionInfo")
                            || root.hasEntry("_VBA_PROJECT_CUR") || root.hasEntry("Macros")) {
                        throw new MSException("UNSUPPORTED_FORMAT");
                    }
                    // Opening HSSF also rejects BIFF encryption; no formulas are evaluated.
                    try (var workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook(fs)) {
                        if (workbook.getNumberOfSheets() == 0) throw new MSException("UNSUPPORTED_FORMAT");
                    }
                }
                return;
            }
            if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K' || bytes[2] != 3 || bytes[3] != 4) {
                throw new MSException("UNSUPPORTED_FORMAT");
            }
            Set<String> names = new HashSet<>();
            boolean workbookValid = false;
            boolean typesValid = false;
            boolean xmindContentValid = false;
            long expanded = 0;
            try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                java.util.zip.ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zip.getNextEntry()) != null) {
                    String path = entry.getName();
                    String pathLower = path.toLowerCase(Locale.ROOT);
                    if (!names.add(path) || names.size() > 10000 || path.startsWith("/") || path.contains("\\")
                            || java.util.Arrays.asList(path.split("/")).contains("..") || pathLower.contains("vbaproject")
                            || pathLower.contains("externallinks/") || pathLower.contains("embeddings/")) {
                        throw new MSException("UNSUPPORTED_FORMAT");
                    }
                    int read;
                    boolean xml = lower.endsWith(".xlsx") && (path.equals("xl/workbook.xml") || path.equals("[Content_Types].xml"));
                    boolean xmind = lower.endsWith(".xmind") && (path.equals("content.json") || path.equals("content.xml"));
                    var selected = xml || xmind ? new java.io.ByteArrayOutputStream() : null;
                    while ((read = zip.read(buffer)) != -1) {
                        expanded += read;
                        if (expanded > 100L * 1024 * 1024) throw new ProjectFileException("SIZE_LIMIT:expandedBytes");
                        if (selected != null) {
                            if (xml && selected.size() + read > 2 * 1024 * 1024) throw new ProjectFileException("SIZE_LIMIT:containerMetadata");
                            selected.write(buffer, 0, read);
                        }
                    }
                    if (xmind) {
                        validateXmindContent(path, selected.toByteArray());
                        xmindContentValid = true;
                    } else if (selected != null) {
                        var factory = javax.xml.parsers.DocumentBuilderFactory.newDefaultInstance();
                        factory.setNamespaceAware(true);
                        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
                        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                        var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(selected.toByteArray())).getDocumentElement();
                        if (path.equals("xl/workbook.xml")) {
                            workbookValid = "workbook".equals(root.getLocalName())
                                    && Set.of("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "http://purl.oclc.org/ooxml/spreadsheetml/main").contains(root.getNamespaceURI());
                        } else {
                            typesValid = "Types".equals(root.getLocalName());
                            var types = root.getElementsByTagNameNS("*", "Override");
                            boolean spreadsheet = false;
                            for (int i = 0; i < types.getLength(); i++) {
                                var type = (org.w3c.dom.Element) types.item(i);
                                String mime = type.getAttribute("ContentType").toLowerCase(Locale.ROOT);
                                if (mime.contains("macroenabled") || mime.contains("vbaproject")) throw new MSException("UNSUPPORTED_FORMAT");
                                if ("/xl/workbook.xml".equals(type.getAttribute("PartName")) && mime.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml")) spreadsheet = true;
                            }
                            typesValid &= spreadsheet;
                        }
                    }
                }
            }
            boolean valid = lower.endsWith(".xlsx")
                    ? workbookValid && typesValid
                    : xmindContentValid;
            if (!valid) throw new MSException("UNSUPPORTED_FORMAT");
        } catch (MSException expected) {
            throw expected;
        } catch (Exception invalid) {
            throw new MSException("UNSUPPORTED_FORMAT");
        }
    }

    private static void validateXmindContent(String path, byte[] bytes) throws Exception {
        if (path.endsWith(".json")) {
            var factory = com.fasterxml.jackson.core.JsonFactory.builder()
                    .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder()
                            .maxNestingDepth(128).maxStringLength(1_048_576).build()).build();
            try (var parser = factory.createParser(bytes)) {
                require(parser.nextToken() == com.fasterxml.jackson.core.JsonToken.START_ARRAY);
                int sheets = 0;
                while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_ARRAY) {
                    require(parser.currentToken() == com.fasterxml.jackson.core.JsonToken.START_OBJECT);
                    boolean id = false, root = false;
                    while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                        require(parser.currentToken() == com.fasterxml.jackson.core.JsonToken.FIELD_NAME);
                        String field = parser.currentName();
                        var value = parser.nextToken();
                        if ("id".equals(field)) {
                            require(value == com.fasterxml.jackson.core.JsonToken.VALUE_STRING && !parser.getText().isBlank());
                            id = true;
                        } else if ("rootTopic".equals(field)) {
                            require(value == com.fasterxml.jackson.core.JsonToken.START_OBJECT);
                            boolean topicId = false;
                            while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                                require(parser.currentToken() == com.fasterxml.jackson.core.JsonToken.FIELD_NAME);
                                String topicField = parser.currentName();
                                var topicValue = parser.nextToken();
                                if ("id".equals(topicField)) {
                                    require(topicValue == com.fasterxml.jackson.core.JsonToken.VALUE_STRING && !parser.getText().isBlank());
                                    topicId = true;
                                } else if ("title".equals(topicField)) require(topicValue == com.fasterxml.jackson.core.JsonToken.VALUE_STRING);
                                parser.skipChildren();
                            }
                            require(topicId);
                            root = true;
                        } else parser.skipChildren();
                    }
                    require(id && root);
                    sheets++;
                }
                require(sheets > 0 && parser.nextToken() == null);
            }
        } else {
            var factory = javax.xml.parsers.SAXParserFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var reader = factory.newSAXParser().getXMLReader();
            reader.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            reader.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var handler = new org.xml.sax.helpers.DefaultHandler() {
                int depth, sheets, topics;
                boolean inSheet;
                public void startElement(String uri, String local, String qName, org.xml.sax.Attributes attributes) throws org.xml.sax.SAXException {
                    depth++;
                    if (depth > 128) throw new org.xml.sax.SAXException("XMind depth limit");
                    if (depth == 1 && (!"xmap-content".equals(local) || !"urn:xmind:xmap:xmlns:content:2.0".equals(uri))) throw new org.xml.sax.SAXException("Invalid XMind root");
                    if (depth == 2 && "sheet".equals(local) && "urn:xmind:xmap:xmlns:content:2.0".equals(uri)) {
                        if (attributes.getValue("id") == null || attributes.getValue("id").isBlank()) throw new org.xml.sax.SAXException("Missing sheet identity");
                        sheets++;
                        inSheet = true;
                        topics = 0;
                    }
                    if (depth == 3 && inSheet && "topic".equals(local) && "urn:xmind:xmap:xmlns:content:2.0".equals(uri)) {
                        if (attributes.getValue("id") == null || attributes.getValue("id").isBlank()) throw new org.xml.sax.SAXException("Missing topic identity");
                        topics++;
                    }
                }
                public void endElement(String uri, String local, String qName) throws org.xml.sax.SAXException {
                    if (depth == 2 && inSheet) {
                        if (topics != 1) throw new org.xml.sax.SAXException("Missing XMind root topic");
                        inSheet = false;
                    }
                    depth--;
                }
                public void endDocument() throws org.xml.sax.SAXException {
                    if (sheets == 0) throw new org.xml.sax.SAXException("Missing XMind sheets");
                }
                public void error(org.xml.sax.SAXParseException error) throws org.xml.sax.SAXException { throw error; }
                public void fatalError(org.xml.sax.SAXParseException error) throws org.xml.sax.SAXException { throw error; }
            };
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.parse(new org.xml.sax.InputSource(new ByteArrayInputStream(bytes)));
        }
    }

    private static void require(boolean valid) {
        if (!valid) throw new MSException("UNSUPPORTED_FORMAT");
    }

    public static void range(long offset, long length, long size, long maximum) {
        if (offset < 0 || length <= 0 || length > maximum || offset > size || length > size - offset) {
            throw new ProjectFileException("VALIDATION_ERROR:range");
        }
    }
}
