package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.bug.mapper.BugContentMapper;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only against this task's explicitly isolated, loopback-only MySQL data directory. */
@EnabledIfSystemProperty(named="bug.image.mysql",matches="true")
class AgentBugImageFinalCheckMySqlTests {
    final String schema="msp14_guard_"+UUID.randomUUID().toString().replace("-", "");
    JdbcTemplate admin,jdbc;
    DriverManagerDataSource dataSource;
    DataSourceTransactionManager manager;
    SqlSessionTemplate sessions;
    AgentBugImageFinalCheck guard;
    BugContentMapper content;
    String url;

    @BeforeEach void setup() throws Exception {
        admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13327/?useSSL=false&allowPublicKeyRetrieval=true","root",""));
        String actual=admin.queryForObject("SELECT @@datadir",String.class).replace('\\','/').replaceAll("/+$", "");
        String expected=Path.of(Objects.requireNonNull(System.getProperty("bug.image.mysql.datadir"),"Explicit isolated datadir required"))
                .toAbsolutePath().toString().replace('\\','/');
        assertTrue(expected.endsWith("/msp14-isolated-mysql"),"Refuse any datadir not explicitly named for these isolated tests");
        assertEquals(expected.toLowerCase(Locale.ROOT),actual.toLowerCase(Locale.ROOT),"Refuse any database outside this task's isolated server");
        admin.execute("CREATE DATABASE "+schema);
        url="jdbc:mysql://127.0.0.1:13327/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true";
        dataSource=new DriverManagerDataSource(url,"root","");jdbc=new JdbcTemplate(dataSource);manager=new DataSourceTransactionManager(dataSource);
        for(String ddl:List.of(
                "agent_token(id VARCHAR(32) PRIMARY KEY,enable BOOLEAN)",
                "`user`(id VARCHAR(32) PRIMARY KEY,enable BOOLEAN)",
                "user_role_relation(id VARCHAR(32) PRIMARY KEY,user_id VARCHAR(32),role_id VARCHAR(32),INDEX(user_id))",
                "user_role(id VARCHAR(32) PRIMARY KEY,enabled BOOLEAN)",
                "user_role_permission(role_id VARCHAR(32),permission_id VARCHAR(64),INDEX(role_id))",
                "project(id VARCHAR(32) PRIMARY KEY,deleted BOOLEAN)",
                "bug(id VARCHAR(32) PRIMARY KEY,deleted BOOLEAN)",
                "bug_content(bug_id VARCHAR(32) PRIMARY KEY,description LONGTEXT)",
                "bug_local_attachment(id VARCHAR(32) PRIMARY KEY,bug_id VARCHAR(32),INDEX(bug_id))",
                "file_association(id VARCHAR(32) PRIMARY KEY,source_id VARCHAR(32),source_type VARCHAR(32),deleted BOOLEAN,INDEX(source_id))",
                "file_metadata(id VARCHAR(32) PRIMARY KEY,module_id VARCHAR(32))",
                "file_metadata_repository(file_metadata_id VARCHAR(32) PRIMARY KEY)",
                "file_module_repository(file_module_id VARCHAR(32) PRIMARY KEY)"))jdbc.execute("CREATE TABLE "+ddl+" ENGINE=InnoDB");
        for(String sql:List.of("INSERT INTO agent_token VALUES('t',1)","INSERT INTO `user` VALUES('u',1)",
                "INSERT INTO user_role_relation VALUES('rel','u','role')","INSERT INTO user_role VALUES('role',1)",
                "INSERT INTO user_role_permission VALUES('role','PROJECT_BUG:READ')","INSERT INTO project VALUES('p',0)",
                "INSERT INTO bug VALUES('b',0)","INSERT INTO bug_content VALUES('b','original')",
                "INSERT INTO bug_local_attachment VALUES('local','b')","INSERT INTO file_association VALUES('a','b','BUG',0)",
                "INSERT INTO file_metadata VALUES('f','m')","INSERT INTO file_metadata_repository VALUES('f')",
                "INSERT INTO file_module_repository VALUES('m')"))jdbc.execute(sql);
        var factory=new SqlSessionFactoryBean();factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("io/metersphere/bug/mapper/BugContentMapper.xml"));
        sessions=new SqlSessionTemplate(factory.getObject());content=sessions.getMapper(BugContentMapper.class);
        guard=new AgentBugImageFinalCheck(jdbc,sessions,manager);
        var token=new AgentToken();token.setId("t");token.setUserId("u");AgentTokenContext.set(token);
    }

    @AfterEach void cleanup() {
        AgentTokenContext.clear();
        if(admin!=null && url!=null && schema.matches("msp14_guard_[a-f0-9]{32}"))admin.execute("DROP DATABASE "+schema);
    }

    @ParameterizedTest
    @ValueSource(strings={
        "UPDATE bug_content SET description='removed' WHERE bug_id='b'",
        "DELETE FROM bug_local_attachment WHERE id='local'",
        "UPDATE file_association SET deleted=1 WHERE id='a'",
        "UPDATE bug SET deleted=1 WHERE id='b'",
        "UPDATE agent_token SET enable=0 WHERE id='t'",
        "UPDATE `user` SET enable=0 WHERE id='u'",
        "UPDATE user_role SET enabled=0 WHERE id='role'",
        "DELETE FROM user_role_relation WHERE id='rel'",
        "DELETE FROM user_role_permission WHERE role_id='role'",
        "DELETE FROM file_metadata WHERE id='f'",
        "DELETE FROM file_metadata_repository WHERE file_metadata_id='f'",
        "DELETE FROM file_module_repository WHERE file_module_id='m'"
    })
    void actualRevocationsCannotCommitUntilAfterTheStorageDecision(String change) throws Exception {
        var writerId=new AtomicLong();var decision=new AtomicLong();var committed=new AtomicLong();
        var entered=new CountDownLatch(1);List<Future<?>> future=new ArrayList<>();
        try(var workers=Executors.newSingleThreadExecutor()){
            guard.run("p","b","f",()->{
                future.add(workers.submit(()->{
                    try(var connection=dataSource.getConnection();var statement=connection.createStatement()){
                        try(var result=statement.executeQuery("SELECT CONNECTION_ID()")){result.next();writerId.set(result.getLong(1));}
                        statement.execute("SET innodb_lock_wait_timeout=5");entered.countDown();statement.executeUpdate(change);committed.set(System.nanoTime());
                    } catch(Exception failure){throw new RuntimeException(failure);}
                }));
                try {
                    assertTrue(entered.await(3,TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class,()->future.getFirst().get(200,TimeUnit.MILLISECONDS));
                    var observer=new JdbcTemplate(new DriverManagerDataSource(url,"root",""));
                    assertTrue(observer.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.threads t ON t.THREAD_ID=w.REQUESTING_THREAD_ID WHERE t.PROCESSLIST_ID=?",Integer.class,writerId.get())>0,"Observe a real InnoDB lock wait, not just an unscheduled writer");
                    assertEquals("original",content.selectByPrimaryKey("b").getDescription());
                    decision.set(System.nanoTime()); // Represents the final storage revision read while all predicates are pinned.
                } catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new RuntimeException(interrupted);}
            });
            future.getFirst().get(5,TimeUnit.SECONDS);
            assertTrue(committed.get()>decision.get());
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"empty-permissions", "nonempty-permissions", "empty-relations", "new-role-relation", "empty-attachments", "new-association"})
    void scannedPredicatesRejectPhantomInsertUntilAfterDecision(String scenario) throws Exception {
        String insert;
        switch(scenario) {
            case "empty-permissions" -> { jdbc.update("DELETE FROM user_role_permission"); insert="INSERT INTO user_role_permission VALUES('role','PROJECT_BUG:READ')"; }
            case "nonempty-permissions" -> { jdbc.update("UPDATE user_role_permission SET permission_id='OTHER'"); insert="INSERT INTO user_role_permission VALUES('role','PROJECT_BUG:READ')"; }
            case "empty-relations" -> { jdbc.update("DELETE FROM user_role_relation"); insert="INSERT INTO user_role_relation VALUES('new','u','role')"; }
            case "new-role-relation" -> { jdbc.update("INSERT INTO user_role VALUES('new-role',1)"); insert="INSERT INTO user_role_relation VALUES('new','u','new-role')"; }
            case "empty-attachments" -> { jdbc.update("DELETE FROM bug_local_attachment"); insert="INSERT INTO bug_local_attachment VALUES('new','b')"; }
            default -> insert="INSERT INTO file_association VALUES('new','b','BUG',0)";
        }
        actualRevocationsCannotCommitUntilAfterTheStorageDecision(insert);
    }

    @ParameterizedTest
    @ValueSource(strings={"empty", "nonempty"})
    void reviewR2ServiceCannotAdoptInsertedThenRevokedGrant(String initial) throws Exception {
        var external=new JdbcTemplate(new DriverManagerDataSource(url,"root",""));
        // Per-connection init via URL: this is a different autocommit connection from the guard.
        var attacker=new JdbcTemplate(new DriverManagerDataSource(url+"&sessionVariables=innodb_lock_wait_timeout=1","root",""));
        var f=new AgentBugImageServiceTests();f.setup();String ref=f.ref();
        org.mockito.Mockito.clearInvocations(f.store);
        org.mockito.Mockito.doAnswer(call->{
            int count=jdbc.queryForObject("SELECT COUNT(*) FROM user_role_permission WHERE role_id='role' AND permission_id='PROJECT_BUG:READ'",Integer.class);
            if(count==0)throw AgentBusinessAccess.denied();return null;
        }).when(f.permissions).require("u","p");
        org.mockito.Mockito.when(f.store.read(org.mockito.ArgumentMatchers.any())).thenAnswer(call->{
            external.update("DELETE FROM user_role_permission");
            if(initial.equals("nonempty")) external.update("INSERT INTO user_role_permission VALUES('role','OTHER')");
            return f.png;
        });
        org.mockito.Mockito.doAnswer(call->{guard.run("p","b","f",()->{
            assertEquals("SERIALIZABLE",jdbc.queryForObject("SELECT @@transaction_isolation",String.class));
            assertThrows(org.springframework.dao.CannotAcquireLockException.class,()->attacker.update("INSERT INTO user_role_permission VALUES('role','PROJECT_BUG:READ')"));
            assertEquals(0,external.queryForObject("SELECT COUNT(*) FROM user_role_permission WHERE permission_id='PROJECT_BUG:READ'",Integer.class));
            ((Runnable)call.getArgument(3)).run();
        });return null;}).when(f.finalCheck).run(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(Runnable.class));
        assertEquals("AGENT_RESOURCE_NOT_ACCESSIBLE",assertThrows(io.metersphere.sdk.exception.MSException.class,()->f.service.read("p","b",ref)).getMessage());
        // Only the initial revision occurs: denied final authorization cannot reach a storage decision.
        org.mockito.Mockito.verify(f.store,org.mockito.Mockito.times(1)).revision(org.mockito.ArgumentMatchers.any());
        assertEquals(0,external.queryForObject("SELECT COUNT(*) FROM user_role_permission WHERE permission_id='PROJECT_BUG:READ'",Integer.class));
    }

    @Test void finalPlainSelectAlsoLocksDependenciesNotInExplicitPrelockList() throws Exception {
        jdbc.execute("CREATE TABLE inherited_grant(id VARCHAR(32) PRIMARY KEY, enabled BOOLEAN) ENGINE=InnoDB");
        jdbc.update("INSERT INTO inherited_grant VALUES('system',1)");
        var attacker=new JdbcTemplate(new DriverManagerDataSource(url+"&sessionVariables=innodb_lock_wait_timeout=1","root",""));
        guard.run("p","b","f",()->{
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inherited_grant WHERE enabled=1",Integer.class));
            assertThrows(org.springframework.dao.CannotAcquireLockException.class,()->attacker.update("DELETE FROM inherited_grant WHERE id='system'"));
            assertThrows(org.springframework.dao.CannotAcquireLockException.class,()->attacker.update("INSERT INTO inherited_grant VALUES('new',1)"));
        });
        assertEquals(1,attacker.update("DELETE FROM inherited_grant WHERE id='system'"));
    }

    @ParameterizedTest
    @ValueSource(strings={"PROJECT", "SYSTEM", "PROJECT_ADMIN"})
    void actualProductionRoleMappersAndInheritedPermissionStayProtected(String kind) throws Exception {
        jdbc.execute("ALTER TABLE user_role ADD name VARCHAR(255), ADD description TEXT, ADD internal BOOLEAN, ADD type VARCHAR(32), ADD create_time BIGINT, ADD update_time BIGINT, ADD create_user VARCHAR(32), ADD scope_id VARCHAR(32)");
        jdbc.execute("ALTER TABLE user_role_relation ADD source_id VARCHAR(32), ADD organization_id VARCHAR(32), ADD create_time BIGINT, ADD create_user VARCHAR(32)");
        jdbc.execute("ALTER TABLE user_role_permission ADD id VARCHAR(32)");
        String role=kind.equals("PROJECT_ADMIN")?io.metersphere.sdk.constants.InternalUserRole.PROJECT_ADMIN.getValue():"role";
        jdbc.update("UPDATE user_role SET id=?,type=?",role,kind.equals("SYSTEM")?"SYSTEM":"PROJECT");
        jdbc.update("UPDATE user_role_relation SET role_id=?,source_id=?",role,kind.equals("SYSTEM")?"system":"p");
        jdbc.update("UPDATE user_role_permission SET role_id=?",role);
        if(kind.equals("PROJECT_ADMIN")) jdbc.update("DELETE FROM user_role_permission");
        var factory=new SqlSessionFactoryBean();factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("io/metersphere/system/mapper/UserRoleMapper.xml"),
                new ClassPathResource("io/metersphere/system/mapper/UserRoleRelationMapper.xml"),
                new ClassPathResource("io/metersphere/system/mapper/UserRolePermissionMapper.xml"));
        var actualSessions=new SqlSessionTemplate(factory.getObject());
        var users=new io.metersphere.system.service.UserLoginService();
        var base=org.mockito.Mockito.mock(io.metersphere.system.mapper.BaseUserMapper.class);
        org.mockito.Mockito.when(base.selectById("u")).thenAnswer(call->{
            var fresh=new io.metersphere.system.dto.user.UserDTO();fresh.setId("u");fresh.setEnable(true);fresh.setDeleted(false);return fresh;
        });
        org.springframework.test.util.ReflectionTestUtils.setField(users,"baseUserMapper",base);
        org.springframework.test.util.ReflectionTestUtils.setField(users,"permissionUiService",org.mockito.Mockito.mock(io.metersphere.system.service.PermissionUiService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(users,"userRoleMapper",actualSessions.getMapper(io.metersphere.system.mapper.UserRoleMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(users,"userRoleRelationMapper",actualSessions.getMapper(io.metersphere.system.mapper.UserRoleRelationMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(users,"userRolePermissionMapper",actualSessions.getMapper(io.metersphere.system.mapper.UserRolePermissionMapper.class));
        var projects=org.mockito.Mockito.mock(io.metersphere.project.mapper.ProjectMapper.class);
        var project=new io.metersphere.project.domain.Project();project.setId("p");project.setDeleted(false);
        org.mockito.Mockito.when(projects.selectByPrimaryKey("p")).thenReturn(project);
        var actualPermissions=new AgentBugImagePermission(users,projects);
        var f=new AgentBugImageServiceTests();f.setup();
        org.springframework.test.util.ReflectionTestUtils.setField(f.service,"permissions",actualPermissions);
        org.springframework.test.util.ReflectionTestUtils.setField(f.service,"sessions",actualSessions);
        var actualGuard=new AgentBugImageFinalCheck(jdbc,actualSessions,manager);
        var attacker=new JdbcTemplate(new DriverManagerDataSource(url+"&sessionVariables=innodb_lock_wait_timeout=1","root",""));
        final boolean[] inFinal={false};
        org.mockito.Mockito.doAnswer(call->{actualGuard.run("p","b","f",()->{inFinal[0]=true;((Runnable)call.getArgument(3)).run();});return null;})
                .when(f.finalCheck).run(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(Runnable.class));
        String ref=f.ref();
        org.mockito.Mockito.when(f.store.revision(org.mockito.ArgumentMatchers.any())).thenAnswer(call->{
            if(inFinal[0]) assertThrows(org.springframework.dao.CannotAcquireLockException.class,()->attacker.update("DELETE FROM user_role_relation WHERE id='rel'"));
            return "object-version-1";
        });
        assertNotNull(f.service.read("p","b",ref));
        assertEquals(1,attacker.update("DELETE FROM user_role_relation WHERE id='rel'"));
        assertThrows(io.metersphere.sdk.exception.MSException.class,()->actualPermissions.require("u","p"));
        for(String mapper:List.of("UserRoleMapper","UserRoleRelationMapper","UserRolePermissionMapper"))
            assertNull(actualSessions.getConfiguration().getMappedStatement("io.metersphere.system.mapper."+mapper+".selectByExample").getCache());
    }

    public static class SuspendedRead {
        final AgentBugImageFinalCheck guard;final BugContentMapper mapper;
        public SuspendedRead(AgentBugImageFinalCheck guard,BugContentMapper mapper){this.guard=guard;this.mapper=mapper;}
        @Transactional(propagation=Propagation.NOT_SUPPORTED)
        public String read(){String[] value={null};guard.run("p","b","f",()->value[0]=mapper.selectByPrimaryKey("b").getDescription());return value[0];}
    }

    @Test void realMyBatisSessionAndRepeatableReadTransactionAreSuspendedAndRestored() {
        var target=new SuspendedRead(guard,content);var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));var read=(SuspendedRead)proxy.getProxy();
        var outer=new TransactionTemplate(manager);outer.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        outer.executeWithoutResult(status->{
            assertEquals("original",content.selectByPrimaryKey("b").getDescription());
            new JdbcTemplate(new DriverManagerDataSource(url,"root","")).update("UPDATE bug_content SET description='current' WHERE bug_id='b'");
            assertEquals("original",content.selectByPrimaryKey("b").getDescription()); // Actual first-level cache/snapshot.
            assertEquals("current",read.read()); // NOT_SUPPORTED + REQUIRES_NEW + actual mapped query.
            assertEquals("original",content.selectByPrimaryKey("b").getDescription()); // Outer session was restored, not cleared accidentally.
        });
        assertEquals(org.apache.ibatis.session.LocalCacheScope.SESSION,sessions.getConfiguration().getLocalCacheScope());
        assertNull(sessions.getConfiguration().getMappedStatement("io.metersphere.bug.mapper.BugContentMapper.selectByPrimaryKey").getCache(),"Production mapper namespace defines no second-level cache");
    }
}
