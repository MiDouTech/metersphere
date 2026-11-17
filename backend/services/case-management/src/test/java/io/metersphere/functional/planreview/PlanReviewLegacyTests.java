package io.metersphere.functional.planreview;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanReviewLegacyTests {
    JdbcTemplate jdbc;PlanReviewLegacyInventory inventory;
    @BeforeEach void setup() throws Exception {
        var database=PlanReviewTestDatabase.create();jdbc=database.jdbc();
        if(System.getProperty("plan.review.mysql.config")!=null)for(String table:List.of("functional_import_followup_group","functional_import_followup","functional_import_followup_item"))jdbc.execute("ALTER TABLE "+table+" CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        jdbc.execute("CREATE TABLE user(id VARCHAR(50),enable BOOLEAN,deleted BOOLEAN)");jdbc.execute("CREATE TABLE project(id VARCHAR(50),enable BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role(id VARCHAR(50),type VARCHAR(32),enabled BOOLEAN)");jdbc.execute("CREATE TABLE user_role_relation(user_id VARCHAR(50),role_id VARCHAR(50),source_id VARCHAR(50))");jdbc.execute("CREATE TABLE user_role_permission(role_id VARCHAR(50),permission_id VARCHAR(100))");
        jdbc.update("INSERT INTO user VALUES('reader',true,false)");jdbc.update("INSERT INTO project VALUES('project',true,false)");jdbc.update("INSERT INTO user_role VALUES('reader','PROJECT',true)");jdbc.update("INSERT INTO user_role_relation VALUES('reader','reader','project')");
        jdbc.update("INSERT INTO user_role_permission VALUES('reader','PROJECT_TEST_PLAN:READ'),('reader','CASE_REVIEW:READ')");
        inventory=new PlanReviewLegacyInventory(jdbc,new PlanReviewAuthorization(jdbc),new PlanReviewPlanStateResolver(List.of()));
    }
    void group(String id,String plan,String review){jdbc.update("INSERT INTO functional_import_followup_group(id,project_id,plan_id,review_id) VALUES(?,'project',?,?)",id,plan,review);}
    @SuppressWarnings("unchecked") List<Map<String,Object>> rows(Map<String,Object> result){return (List<Map<String,Object>>)result.get("groups");}
    @Test void scanIsStableReadOnlyAndNeverPairsUnlinkedPlans(){
        group("old","plan","review");
        jdbc.update("INSERT INTO test_plan(id,project_id,type,status) VALUES('unlinked','project','TEST_PLAN','PREPARED')");
        var first=inventory.scan("project","reader");var second=inventory.scan("project","reader");
        assertEquals(first.get("manifestHash"),second.get("manifestHash"));assertEquals(false,first.get("executionAllowed"));assertEquals(1L,first.get("unlinkedPlanCount"));
        assertEquals("LEGAL_UNIQUE",rows(first).getFirst().get("classification"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
    }
    @Test void duplicatePairsFoldOnlyInClassificationAndMultipleTargetsAreNeverChosen(){
        group("first","plan","review");group("duplicate","plan","review");
        assertTrue(rows(inventory.scan("project","reader")).stream().allMatch(row->"DUPLICATE_PAIR".equals(row.get("classification"))));
        jdbc.update("INSERT INTO case_review(id,project_id,status) VALUES('another','project','UNDERWAY')");group("conflict","plan","another");
        assertTrue(rows(inventory.scan("project","reader")).stream().allMatch(row->"MULTIPLE_TARGETS".equals(row.get("classification"))));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup_group",Integer.class));
    }
    @Test void foreignOrphanAndUnknownStatesAreExplicitAndForeignObjectIdsAreMasked(){
        jdbc.update("INSERT INTO case_review(id,project_id,status) VALUES('foreign-review','other','UNDERWAY')");group("cross","plan","foreign-review");group("orphan","missing","review");
        jdbc.update("INSERT INTO test_plan(id,project_id,type,status) VALUES('unknown','project','TEST_PLAN','NEW_UNKNOWN')");group("unknown","unknown","review");
        var rows=rows(inventory.scan("project","reader"));
        var cross=rows.stream().filter(row->"cross".equals(row.get("legacyGroupId"))).findFirst().orElseThrow();assertEquals("CROSS_PROJECT",cross.get("classification"));assertEquals("",cross.get("reviewId"));
        assertEquals("ORPHAN",rows.stream().filter(row->"orphan".equals(row.get("legacyGroupId"))).findFirst().orElseThrow().get("classification"));
        // The shared review also creates a conflict: no automatic preference for the apparently valid pair.
        assertEquals("MULTIPLE_TARGETS",rows.stream().filter(row->"unknown".equals(row.get("legacyGroupId"))).findFirst().orElseThrow().get("classification"));
    }
    @Test void sourceSetOrTaskWatermarkChangesInvalidateManifestAndMissingReadIsDenied(){
        group("old","plan","review");String hash=(String)inventory.scan("project","reader").get("manifestHash");
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('member','plan','a')");assertNotEquals(hash,inventory.scan("project","reader").get("manifestHash"));
        jdbc.update("DELETE FROM user_role_permission WHERE permission_id='CASE_REVIEW:READ'");assertThrows(PlanReviewFailure.class,()->inventory.scan("project","reader"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));
    }
    @Test void nativeNotArchivedStorageStateIsClassifiedUsingTheExistingBusinessResolver(){
        jdbc.update("UPDATE test_plan SET status='NOT_ARCHIVED'");group("old","plan","review");
        inventory=new PlanReviewLegacyInventory(jdbc,new PlanReviewAuthorization(jdbc),new PlanReviewPlanStateResolver(List.of(new PlanReviewPlanStateProvider(){public String businessState(String plan){return "UNDERWAY";}})));
        assertEquals("LEGAL_UNIQUE",rows(inventory.scan("project","reader")).getFirst().get("classification"));
        assertEquals("NOT_ARCHIVED",jdbc.queryForObject("SELECT status FROM test_plan",String.class));
    }
}
