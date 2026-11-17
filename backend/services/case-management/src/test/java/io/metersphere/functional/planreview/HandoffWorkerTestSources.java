package io.metersphere.functional.planreview;

import io.metersphere.functional.handoff.*;
import java.util.*;

/** Real source setup shared by concurrency tests and controlled TLS experiments. */
final class HandoffWorkerTestSources {
    static void prepare(HandoffPersistenceTests fixture,int count,boolean separateProjects,HandoffWebhookTarget transport,String receiver){
        fixture.worker.destroy();
        var auth=new PlanReviewAuthorization(fixture.jdbc);var states=new PlanReviewPlanStateResolver(List.of());var locks=new PlanReviewPersistence(fixture.jdbc);var store=new HandoffStore(fixture.jdbc,locks);
        fixture.service=new HandoffService(store,new HandoffSourceReader(fixture.jdbc,states,auth,new PlanReviewFrozenReader(fixture.jdbc)),new HandoffAuthorization(fixture.jdbc,auth),locks,transport,new HandoffSecrets(Base64.getEncoder().encodeToString(new byte[32])),"isolated-test");fixture.service.setSourceBaseUrl("https://msp.example.test");
        fixture.worker=new HandoffWorker(store,fixture.service,fixture.tx.getTransactionManager());fixture.worker.setEnabled(true);
        fixture.save(receiver,"NONE","");fixture.run(()->fixture.service.grant(HandoffPersistenceTests.PLATFORM,fixture.revision(),true,"actor"));
        var app=new PlanReviewApplicationService(fixture.jdbc,locks,auth,fixture.tx.getTransactionManager(),states);
        var projects=new LinkedHashSet<String>();projects.add("project");
        for(int n=2;n<=count;n++){
            String plan="plan"+n,review="review"+n,project=separateProjects?"project"+n:"project";
            if(projects.add(project)){
                fixture.jdbc.update("INSERT INTO project(id,enable,deleted,name) VALUES(?,true,false,?)",project,project);
                fixture.run(()->app.grant(project,0L,true,"actor"));
            }
            String a=separateProjects?plan+"a":"a",b=separateProjects?plan+"b":"b";
            if(separateProjects)fixture.jdbc.update("INSERT INTO functional_case(id,project_id,deleted) VALUES(?,?,false),(?,?,false)",a,project,b,project);
            fixture.jdbc.update("INSERT INTO test_plan(id,project_id,type,status) VALUES(?,?,'TEST_PLAN','UNDERWAY')",plan,project);
            fixture.jdbc.update("INSERT INTO case_review(id,project_id,status) VALUES(?,?,'UNDERWAY')",review,project);
            fixture.jdbc.update("INSERT INTO test_plan_functional_case VALUES(?,?,?),(?,?,?)",plan+"ma",plan,a,plan+"mb",plan,b);
            var preview=fixture.run(()->app.preview(plan,review,"PAIR_EXISTING","actor"));var pair=fixture.run(()->app.confirm((String)preview.get("previewId"),"pair"+plan,true,"actor"));
            fixture.tx.executeWithoutResult(s->app.synchronize((String)pair.get("relationId"),(String)pair.get("generation")));
        }
        fixture.enable();
        fixture.jdbc.update("UPDATE case_review_functional_case SET status='PASS'");
        for(int n=1;n<=count;n++){
            String suffix=n==1?"":Integer.toString(n),review="review"+suffix,plan="plan"+suffix,project=separateProjects&&n>1?"project"+n:"project";
            var preview=fixture.run(()->fixture.lifecycle.preview("review",review,"COMPLETE","actor"));fixture.run(()->fixture.lifecycle.confirm((String)preview.get("previewId"),true,"actor"));fixture.tx.executeWithoutResult(s->fixture.service.capture(project,plan,true));
        }
    }
}
