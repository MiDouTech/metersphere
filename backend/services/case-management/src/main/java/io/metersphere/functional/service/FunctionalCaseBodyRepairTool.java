package io.metersphere.functional.service;

import io.metersphere.sdk.util.FunctionalCaseBlobCodec;
import io.metersphere.sdk.util.JSON;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Offline database-administrator tool. Never registered as a public HTTP operation. */
public final class FunctionalCaseBodyRepairTool {
    private static final Set<String> FIELDS = Set.of("steps", "text_description", "expected_result", "prerequisite", "description");
    public record Request(String caseId, String projectId, String field, String actorId, String expectedHash,
                          long expectedRevision, byte[] replacement, String sourceReference, Path backupDirectory, boolean apply, String expectedLogicalHash) { }
    private FunctionalCaseBodyRepairTool() { }

    public static Map<String,Object> repair(Connection connection, Request request) throws Exception {
        return execute(connection, request, false);
    }

    public static Map<String,Object> rollback(Connection connection, Request request, Path backupFile) throws Exception {
        if(Files.isSymbolicLink(backupFile) || !Files.isRegularFile(backupFile,LinkOption.NOFOLLOW_LINKS)
                || Files.size(backupFile)>17L*1024*1024
                || Files.getPosixFilePermissions(backupFile).stream().anyMatch(p->p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_")))
            throw new IllegalStateException("PRIVATE_BACKUP_REQUIRED");
        String json=Files.readString(backupFile);
        var parser=new com.fasterxml.jackson.databind.ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        Map<?,?> backup=parser.readValue(json,Map.class);
        if(!Objects.equals(backup.get("caseId"),request.caseId()) || !Objects.equals(backup.get("projectId"),request.projectId())
                || !Objects.equals(backup.get("field"),request.field()) || !(backup.get("beforeWasNull") instanceof Boolean)
                || !(backup.get("beforeRawHex") instanceof String)
                || !request.expectedHash().equalsIgnoreCase(String.valueOf(backup.get("afterHash"))))
            throw new IllegalStateException("BACKUP_TARGET_MISMATCH");
        byte[] original=(Boolean)backup.get("beforeWasNull")?null:HexFormat.of().parseHex((String)backup.get("beforeRawHex"));
        if(!sha(original).equals(backup.get("beforeHash")))throw new IllegalStateException("BACKUP_HASH_MISMATCH");
        var restored=new Request(request.caseId(),request.projectId(),request.field(),request.actorId(),request.expectedHash(),
                request.expectedRevision(),original,"rollback:"+sha(json.getBytes(StandardCharsets.UTF_8)),request.backupDirectory(),request.apply(),null);
        return execute(connection, restored, true);
    }

    private static Map<String,Object> execute(Connection connection, Request request, boolean rawRollback) throws Exception {
        if (!connection.getAutoCommit()) throw new IllegalArgumentException("REQUIRES_DEDICATED_CONNECTION");
        if (request==null || request.field()==null || request.caseId()==null || !FIELDS.contains(request.field()) || !request.caseId().matches("[A-Za-z0-9_-]{1,50}")
                || request.projectId()==null || request.actorId()==null || request.expectedHash()==null
                || !request.expectedHash().matches("[a-fA-F0-9]{64}") || request.sourceReference()==null
                || request.sourceReference().isBlank() || request.sourceReference().length()>1000)
            throw new IllegalArgumentException("INVALID_REPAIR_REQUEST");
        connection.setAutoCommit(false);
        try {
            try(var actor=connection.prepareStatement("SELECT id FROM `user` WHERE BINARY id=BINARY ? AND enable=1")) {
                actor.setString(1,request.actorId());
                try(var rows=actor.executeQuery()) {if(!rows.next())throw new IllegalStateException("ACTOR_NOT_ACTIVE");}
            }
            byte[] before;String mode;long revision;
            try(var read=connection.prepareStatement("SELECT c.case_edit_type,c.update_time,OCTET_LENGTH(b."+request.field()+")"+
                    " FROM functional_case c JOIN functional_case_blob b ON b.id=c.id WHERE BINARY c.id=BINARY ? AND BINARY c.project_id=BINARY ? AND c.deleted=0 AND c.latest=1 FOR UPDATE")) {
                read.setString(1,request.caseId());read.setString(2,request.projectId());
                try(var row=read.executeQuery()) {
                    if(!row.next())throw new IllegalStateException("TARGET_NOT_CURRENT_OR_PROJECT_MISMATCH");
                    mode=row.getString(1);revision=row.getLong(2);
                    if(row.wasNull())throw new IllegalStateException("REVISION_MISSING");
                    if(row.getLong(3)>8L*1024*1024+65536)throw new IllegalArgumentException("ORIGINAL_BODY_TOO_LARGE_FOR_TOOL");
                }
            }
            try(var read=connection.prepareStatement("SELECT "+request.field()+" FROM functional_case_blob WHERE id=?")) {
                read.setString(1,request.caseId());
                try(var row=read.executeQuery()) {if(!row.next())throw new IllegalStateException("BLOB_MISSING");before=row.getBytes(1);}
            }
            byte[] logical=request.replacement();
            if(!rawRollback) {
                if(logical==null || logical.length==0 || logical.length>8*1024*1024)throw new IllegalArgumentException("REPLACEMENT_REQUIRED_OR_TOO_LARGE");
                if((request.apply() || request.expectedLogicalHash()!=null) && !sha(logical).equalsIgnoreCase(request.expectedLogicalHash()))
                    throw new IllegalStateException("REPLACEMENT_CHANGED_OR_NOT_CONFIRMED");
                String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(logical)).toString();
                if("steps".equals(request.field())) {
                    FunctionalCaseService.validateWriteSteps(mode,text);
                    if(text.isBlank() || "[]".equals(text.replaceAll("\\s", "")))throw new IllegalArgumentException("EMPTY_RECOVERY_REQUIRES_MANUAL_REVIEW");
                }
            }
            byte[] after=rawRollback?logical:(byte[])FunctionalCaseBlobCodec.pack(logical);
            String beforeHash=sha(before),afterHash=sha(after);
            var report=new LinkedHashMap<String,Object>();
            report.put("caseId",request.caseId());report.put("projectId",request.projectId());report.put("field",request.field());
            report.put("beforeHash",beforeHash);report.put("afterHash",afterHash);report.put("revision",revision);
            report.put(rawRollback?"restoreRawHash":"logicalHash",sha(logical));report.put("mode",rawRollback?"ROLLBACK":"REPAIR");
            if(Arrays.equals(before,after)) {connection.rollback();report.put("state","ALREADY_MATCHES");return report;}
            if(!beforeHash.equalsIgnoreCase(request.expectedHash()) || revision!=request.expectedRevision())
                throw new IllegalStateException("REPAIR_CONFLICT");
            if(!request.apply()) {connection.rollback();report.put("state","DRY_RUN");return report;}
            long now=System.currentTimeMillis(),nextRevision=Math.max(now,Math.addExact(revision,1));
            var backup=new LinkedHashMap<String,Object>(report);
            backup.put("state","PREPARED");backup.put("beforeWasNull",before==null);
            backup.put("beforeRawHex",before==null?"":HexFormat.of().formatHex(before));
            backup.put("actorId",request.actorId());backup.put("sourceReference",request.sourceReference());
            Path file=saveBackup(request.backupDirectory(),request.caseId()+"-"+request.field()+"-"+beforeHash+".json",backup);
            try(var write=connection.prepareStatement("UPDATE functional_case_blob SET "+request.field()+"=? WHERE id=?")) {
                write.setBytes(1,after);write.setString(2,request.caseId());
                if(write.executeUpdate()!=1)throw new IllegalStateException("BLOB_UPDATE_CONFLICT");
            }
            try(var write=connection.prepareStatement("UPDATE functional_case SET update_time=?,update_user=? WHERE id=? AND update_time=?")) {
                write.setLong(1,nextRevision);write.setString(2,request.actorId());write.setString(3,request.caseId());write.setLong(4,revision);
                if(write.executeUpdate()!=1)throw new IllegalStateException("REVISION_UPDATE_CONFLICT");
            }
            String identity=request.caseId()+":"+request.field()+":"+beforeHash+":"+afterHash+":"+revision;
            String eventId=UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
            try(var event=connection.prepareStatement("INSERT INTO functional_import_effect_outbox(id,effect_type,project_id,user_id,payload,state,next_retry_at,create_time,update_time) VALUES (?,'CASE_SYNC',?,?,?,'PENDING',?,?,?)")) {
                event.setString(1,eventId);event.setString(2,request.projectId());event.setString(3,request.actorId());
                event.setString(4,JSON.toJSONString(Map.of("caseId",request.caseId())));
                event.setLong(5,now);event.setLong(6,now);event.setLong(7,now);event.executeUpdate();
            }
            connection.commit();
            report.put("state","APPLIED");report.put("nextRevision",nextRevision);report.put("backup",file.toString());report.put("eventId",eventId);
            return report;
        } catch(Exception failure) {
            try {connection.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}
            throw failure;
        } finally {connection.setAutoCommit(true);}
    }

    private static Path saveBackup(Path directory,String name,Map<String,Object> backup) throws Exception {
        if(directory==null)throw new IllegalArgumentException("BACKUP_DIRECTORY_REQUIRED");
        Path root=directory.toAbsolutePath().normalize();
        if(!Files.exists(root)) {
            Files.createDirectory(root,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            forceDirectory(root.getParent());
        }
        if(Files.isSymbolicLink(root))throw new IllegalStateException("BACKUP_DIRECTORY_SYMLINK");
        var permissions=Files.getPosixFilePermissions(root);
        if(permissions.stream().anyMatch(p->p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_")))
            throw new IllegalStateException("BACKUP_DIRECTORY_MUST_BE_PRIVATE");
        Path file=root.resolve(name);
        String json=JSON.toJSONString(backup);
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)) {
            if(Files.isSymbolicLink(file) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)
                    || Files.getPosixFilePermissions(file).stream().anyMatch(p->p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_"))
                    || !Files.readString(file).equals(json))throw new IllegalStateException("BACKUP_ALREADY_EXISTS_REVIEW_REQUIRED");
            return file;
        }
        Files.createFile(file,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try(var output=FileChannel.open(file,StandardOpenOption.WRITE)) {
            var bytes=ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
            while(bytes.hasRemaining())output.write(bytes);output.force(true);
        }
        forceDirectory(root);
        return file;
    }

    private static void forceDirectory(Path path) throws Exception {
        try(var directory=FileChannel.open(path,StandardOpenOption.READ)) {directory.force(true);}
    }

    private static String sha(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value==null?new byte[0]:value));
    }

    public static void main(String[] arguments) {
        try {
            Map<String,String> options=new HashMap<>();
            for(String argument:arguments) {
                if(!argument.startsWith("--") || !argument.contains("="))throw new IllegalArgumentException();
                String[] pair=argument.substring(2).split("=",2);
                if(options.put(pair[0],pair[1])!=null)throw new IllegalArgumentException();
            }
            Set<String> allowed=Set.of("case-id","project-id","field","actor-id","expected-hash","expected-revision","replacement-file","replacement-hash","rollback-backup","source-reference","backup-directory","apply");
            if(!allowed.containsAll(options.keySet()))throw new IllegalArgumentException();
            boolean rollback=options.containsKey("rollback-backup");
            if(rollback==options.containsKey("replacement-file"))throw new IllegalArgumentException();
            if(!Set.of("true","false").contains(options.getOrDefault("apply","false")))throw new IllegalArgumentException();
            Path source=Path.of(options.get(rollback?"rollback-backup":"replacement-file"));
            if(Files.size(source)>(rollback?17L:8L)*1024*1024)throw new IllegalArgumentException();
            String url=Objects.requireNonNull(System.getenv("MS_REPAIR_JDBC_URL"));
            if(!url.startsWith("jdbc:mysql:"))throw new IllegalArgumentException();
            var request=new Request(options.get("case-id"),options.get("project-id"),options.get("field"),options.get("actor-id"),
                    options.get("expected-hash"),Long.parseLong(options.get("expected-revision")),rollback?null:Files.readAllBytes(source),
                    options.get("source-reference"),options.containsKey("backup-directory")?Path.of(options.get("backup-directory")):null,
                    "true".equals(options.getOrDefault("apply","false")),options.get("replacement-hash"));
            try(var connection=DriverManager.getConnection(url,Objects.requireNonNull(System.getenv("MS_REPAIR_DB_USER")),Objects.requireNonNull(System.getenv("MS_REPAIR_DB_PASSWORD")))) {
                System.out.println(JSON.toJSONString(rollback?rollback(connection,request,source):repair(connection,request)));
            }
        } catch(Exception failure) {
            String code=failure.getMessage();
            if(code==null || !code.matches("[A-Z_]{1,80}"))code="REPAIR_NOT_CONFIRMED";
            System.err.println(code+"：修复结果未确认；请检查参数、备份及数据库状态，并使用相同参数核对。");
            System.exit(1);
        }
    }
}
