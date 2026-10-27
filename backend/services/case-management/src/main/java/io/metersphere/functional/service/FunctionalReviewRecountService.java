package io.metersphere.functional.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
/** Current-state recomputation survives delivery failure and never replays counter deltas. */
@Service
public class FunctionalReviewRecountService {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;
 public FunctionalReviewRecountService(JdbcTemplate jdbc,PlatformTransactionManager manager){this.jdbc=jdbc;tx=new TransactionTemplate(manager);tx.setTimeout(20);}
 public void enqueueForCases(List<String> ids) {
  if(ids==null||ids.isEmpty())return;
  for(int i=0;i<ids.size();i+=500){var batch=ids.subList(i,Math.min(i+500,ids.size()));long now=System.currentTimeMillis();var args=new ArrayList<Object>();args.add(now);args.add(now);args.addAll(batch);
   jdbc.update("INSERT INTO functional_review_recount(review_id,next_retry_at,update_time) SELECT DISTINCT review_id,?,? FROM case_review_functional_case WHERE case_id IN ("+String.join(",",Collections.nCopies(batch.size(),"?"))+") ON DUPLICATE KEY UPDATE state='PENDING',attempts=0,next_retry_at=VALUES(next_retry_at),update_time=VALUES(update_time)",args.toArray());
  }
 }
 public void enqueue(String id){long now=System.currentTimeMillis();jdbc.update("INSERT INTO functional_review_recount(review_id,next_retry_at,update_time) VALUES(?,?,?) ON DUPLICATE KEY UPDATE state='PENDING',attempts=0,next_retry_at=VALUES(next_retry_at),update_time=VALUES(update_time)",id,now,now);}
 @Scheduled(fixedDelay=1500,initialDelay=30000,scheduler="functionalImportEffectsScheduler")
 public void drain(){
  String[] selected={null};
  try{tx.executeWithoutResult(status->{
   var due=jdbc.queryForList("SELECT review_id FROM functional_review_recount WHERE state='PENDING' AND next_retry_at<=? ORDER BY next_retry_at LIMIT 1 FOR UPDATE SKIP LOCKED",String.class,System.currentTimeMillis());
   if(due.isEmpty())return;String id=due.getFirst();selected[0]=id;
   var review=jdbc.queryForList("SELECT status FROM case_review WHERE id=? FOR UPDATE",String.class,id);
   if(!review.isEmpty()){
    var counts=jdbc.queryForList("SELECT r.status,COUNT(*) total FROM case_review_functional_case r JOIN functional_case f ON f.id=r.case_id AND f.deleted=0 WHERE r.review_id=? GROUP BY r.status",id);
    long total=0,passed=0,pending=0,active=0;
    for(var count:counts){long n=((Number)count.get("total")).longValue();total+=n;switch(Objects.toString(count.get("status"),"")){case "PASS"->passed+=n;case "UN_REVIEWED"->pending+=n;case "UNDER_REVIEWED","RE_REVIEWED"->active+=n;default->{}}}
    String next="ARCHIVED".equals(review.getFirst())?"ARCHIVED":pending==total?"PREPARED":pending+active>0?"UNDERWAY":"COMPLETED";
    BigDecimal rate=total==0?BigDecimal.ZERO:BigDecimal.valueOf(passed*100).divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP);
    jdbc.update("UPDATE case_review SET case_count=?,pass_rate=?,status=? WHERE id=?",total,rate,next,id);
   }
   jdbc.update("UPDATE functional_review_recount SET state='SUCCESS',update_time=? WHERE review_id=?",System.currentTimeMillis(),id);
  });}catch(Exception failure){
   org.slf4j.LoggerFactory.getLogger(getClass()).warn("Review recount failed review={}",selected[0],failure);
   if(selected[0]!=null)jdbc.update("UPDATE functional_review_recount SET attempts=attempts+1,next_retry_at=?,update_time=? WHERE review_id=? AND state='PENDING'",System.currentTimeMillis()+30000,System.currentTimeMillis(),selected[0]);
  }
 }
}
