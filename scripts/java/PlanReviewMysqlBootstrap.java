import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Creates credentials only after verifying the new server owns the requested workspace data directory. */
public class PlanReviewMysqlBootstrap {
    public static void main(String[] args) throws Exception {
        try{bootstrap(args);}catch(Exception error){System.err.println("ISOLATED_MYSQL_BOOTSTRAP_FAILED: "+error.getClass().getSimpleName());System.exit(1);}
    }
    private static void bootstrap(String[] args) throws Exception {
        int port=Integer.parseInt(args[0]);
        if(port<1024 || port==3306 || port==33060)throw new IllegalArgumentException("A separate test port is required");
        Path expected=Path.of(args[1]).toRealPath(),file=Path.of(args[2]).toAbsolutePath();
        String url="jdbc:mysql://127.0.0.1:"+port+"/?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&connectTimeout=10000&socketTimeout=10000";
        try(Connection connection=DriverManager.getConnection(url,"root","");Statement sql=connection.createStatement()){
            String dataDir,uuid;
            try(var row=sql.executeQuery("SELECT @@datadir,@@server_uuid")){row.next();dataDir=row.getString(1);uuid=row.getString(2);}
            if(!expected.equals(Path.of(dataDir).toRealPath()))throw new IllegalStateException("Server data directory mismatch; no credentials created");
            if(args.length>3 && "--shutdown".equals(args[3])){sql.execute("SHUTDOWN");System.out.println("ISOLATED_MYSQL_STOPPED");return;}
            byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);String password=HexFormat.of().formatHex(bytes);
            String user="msp12_"+UUID.randomUUID().toString().substring(0,8);
            for(String host:List.of("localhost","127.0.0.1")){
                sql.execute("CREATE USER '"+user+"'@'"+host+"' IDENTIFIED BY '"+password+"'");
                sql.execute("GRANT ALL PRIVILEGES ON `msp12_pr_%`.* TO '"+user+"'@'"+host+"'");
            }
            var properties=new Properties();properties.setProperty("isolated","true");properties.setProperty("url",url);properties.setProperty("user",user);properties.setProperty("password",password);properties.setProperty("serverUuid",uuid);
            try(var writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){properties.store(writer,"Temporary isolated development test connection");}
            var acl=Files.getFileAttributeView(file,java.nio.file.attribute.AclFileAttributeView.class);
            if(acl!=null){var owner=Files.getOwner(file);acl.setAcl(List.of(java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(owner).setPermissions(EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class)).build()));}
            System.out.println("ISOLATED_MYSQL_READY");
        }
    }
}
