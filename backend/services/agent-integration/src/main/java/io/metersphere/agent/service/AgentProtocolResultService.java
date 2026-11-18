package io.metersphere.agent.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** Submission reception, assertion facts and independent acceptance are distinct transitions. */
@Service
public class AgentProtocolResultService {
    private static final Set<String> RESULTS=Set.of("PASS","FAIL","BLOCKED","EXECUTION_ERROR","NOT_RUN");
    private final JdbcTemplate jdbc;private final AgentProtocolStore store;private final AgentProtocolAccess access;
    private final AgentProtocolEvidenceService evidence;
    private java.time.Clock verificationClock=java.time.Clock.systemUTC();
    @jakarta.annotation.Resource private AgentProtocolPublicationService publication;
    public AgentProtocolResultService(JdbcTemplate jdbc,AgentProtocolStore store,AgentProtocolAccess access,AgentProtocolEvidenceService evidence){this.jdbc=jdbc;this.store=store;this.access=access;this.evidence=evidence;}
    void snapshot(Map<String,Object> instance,Map<String,Object> data,String operation){if(instance.get("result")==null)return;var state=new LinkedHashMap<>(instance);state.remove("payload");jdbc.update("INSERT INTO msp_protocol_result_revision(instance_id,result_version,submission_revision,result,verification,operation_id,payload,create_time) VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE instance_id=instance_id",instance.get("id"),instance.get("result_version"),instance.get("revision"),instance.get("result"),instance.get("verification"),operation,store.payload(Map.of("instance",state,"data",data)),System.currentTimeMillis());}
    public Map<String,Object> apply(String op,Map<String,Object> args,Map<String,Object>s,Map<String,Object>i,Map<String,Object>data,AgentProtocolAccess.Actor actor,String operationId) {
        return switch(op) {
            case "case.finish","result.evidence.append" -> submit(op,args,s,i,data,actor);
            case "result.verify" -> verify(args,s,i,data,actor);
            case "result.close-unaccepted" -> close(args,s,i,data,actor);
            case "result.correction.request" -> correct(args,s,i,data,actor);
            default -> throw new io.metersphere.sdk.exception.MSException("AGENT_VALIDATION_ERROR");
        };
    }
    private Map<String,Object> submit(String op,Map<String,Object>args,Map<String,Object>s,Map<String,Object>i,Map<String,Object>data,AgentProtocolAccess.Actor actor) {
        require(actor.person()!=null,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");
        fields(args,"sessionId","caseExecutionId","expectedContextVersion","leaseGeneration","expectedResultVersion","submissionRevision","result","assertions","outputs","reason","evidence","stepEvidence","correctedSteps");
        require(!AgentGovernedExecutionService.unresolved(data),"PROTOCOL_ACTION_UNKNOWN");
        if("case.finish".equals(op))require(i.get("result")==null,"PROTOCOL_ALREADY_SUBMITTED");
        else {require(i.get("result")!=null&&number(args,"submissionRevision")==((Number)i.get("revision")).longValue(),"VERSION_CONFLICT");require(!"ACCEPTED".equals(i.get("verification")),"PROTOCOL_INSTANCE_ENDED");}
        access.project(actor,(String)s.get("project_id"),true);access.capability(actor,(String)s.get("environment_id"),"EVIDENCE_WRITE",false);
        var existingSteps=new ArrayList<>(objects(data.get("steps")));
        for(var addition:objects(args.getOrDefault("stepEvidence",List.of()))) {
            fields(addition,"stepId","evidence");var step=existingSteps.stream().filter(item->addition.get("stepId").equals(item.get("stepId"))).findFirst().orElseThrow(AgentBusinessAccess::denied);
            var merged=new ArrayList<>(objects(step.get("evidence")));merged.addAll(evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),addition.get("evidence"),"RESULT"));step.put("evidence",merged);
        }
        data.put("steps",existingSteps);
        if(args.containsKey("correctedSteps")) {
            require("result.evidence.append".equals(op),"PROTOCOL_CORRECTION_REQUIRED");var corrected=new ArrayList<>(objects(data.getOrDefault("correctedSteps",List.of())));var changed=new HashSet<String>();
            for(var step:objects(args.get("correctedSteps"))) {require(changed.add(id(step,"stepId")),"PROTOCOL_STEP_UNKNOWN");corrected.removeIf(old->step.get("stepId").equals(old.get("stepId")));fields(step,"stepId","observation","outcome","evidence","legacyAssertions");require(existingSteps.stream().anyMatch(original->step.get("stepId").equals(original.get("stepId")))&&Set.of("SATISFIED","VIOLATED").contains(step.get("outcome")),"PROTOCOL_STEP_UNKNOWN");text(step,"observation");var copy=new LinkedHashMap<>(step);copy.put("evidence",evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),step.getOrDefault("evidence",List.of()),"CORRECTION"));corrected.add(copy);}
            data.put("correctedSteps",corrected);
        }
        String result=args.containsKey("result")?text(args,"result"):Objects.toString(i.get("result"),"");require(RESULTS.contains(result),"AGENT_VALIDATION_ERROR");
        if("NOT_RUN".equals(result))require(objects(data.get("steps")).isEmpty()&&objects(data.get("actions")).isEmpty(),"PROTOCOL_RESULT_CLASSIFICATION_CONFLICT");
        AgentProtocolTypes.values(object(object(data.get("contract")).getOrDefault("outputs",Map.of())),object(args.getOrDefault("outputs",data.getOrDefault("outputs",Map.of()))),"PASS".equals(result));
        if(!Set.of("PASS","FAIL").contains(result))text(args,"reason");
        var declared=objects(object(data.get("contract")).get("assertions"));
        var facts=new LinkedHashMap<String,Map<String,Object>>();
        if("result.evidence.append".equals(op))for(var fact:objects(data.getOrDefault("submittedAssertions",List.of())))facts.put(text(fact,"assertionId"),new LinkedHashMap<>(fact));
        Set<String> incomingIds=new HashSet<>();for(var incoming:objects(args.getOrDefault("assertions",List.of()))) {
            fields(incoming,"assertionId","observation","evidence");String id=id(incoming,"assertionId");
            require(incomingIds.add(id),"PROTOCOL_ASSERTION_UNKNOWN");
            require(declared.stream().anyMatch(a->id.equals(a.get("id"))),"PROTOCOL_ASSERTION_UNKNOWN");
            var refs=evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),incoming.getOrDefault("evidence",List.of()),"RESULT");
            var prior=facts.getOrDefault(id,new LinkedHashMap<>());var all=new ArrayList<>(objects(prior.getOrDefault("evidence",List.of())));for(var ref:refs)if(all.stream().noneMatch(existing->existing.get("kind").equals(ref.get("kind"))&&existing.get("id").equals(ref.get("id"))))all.add(ref);
            prior.put("assertionId",id);prior.put("observation",text(incoming,"observation"));prior.put("evidence",all);facts.put(id,prior);
        }
        var missing=new ArrayList<String>();for(var assertion:declared)if(Boolean.TRUE.equals(assertion.get("required"))&&(!facts.containsKey(assertion.get("id"))||objects(facts.get(assertion.get("id")).get("evidence")).isEmpty()))missing.add((String)assertion.get("id"));
        var contributors=new HashSet<String>(strings(data.getOrDefault("evidencePersons",List.of())));contributors.add(actor.person());
        var contributorIdentities=object(data.getOrDefault("evidenceIdentities",Map.of()));contributorIdentities.put(actor.user(),actor.person());
        for(var fact:facts.values())for(var ref:objects(fact.get("evidence"))) {
            String user="PERSONAL".equals(ref.get("kind"))?jdbc.queryForObject("SELECT user_id FROM agent_case_evidence WHERE id=?",String.class,ref.get("id")):
                    jdbc.queryForObject("SELECT create_user FROM ai_execution_artifact WHERE id=?",String.class,ref.get("id"));
            var persons=jdbc.queryForList("SELECT person_ref FROM msp_protocol_identity WHERE user_id=? AND organization_id=?",String.class,user,s.get("organization_id"));
            require(persons.size()==1,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");contributors.add(persons.getFirst());contributorIdentities.put(user,persons.getFirst());
        }
        data.put("evidencePersons",new ArrayList<>(contributors));data.put("evidenceIdentities",contributorIdentities);data.put("submittedAssertions",new ArrayList<>(facts.values()));data.put("reason",args.getOrDefault("reason",""));data.put("outputs",object(args.getOrDefault("outputs",data.getOrDefault("outputs",Map.of()))));
        i.put("result",result);i.put("revision",((Number)i.get("revision")).longValue()+1);i.put("result_version",((Number)i.get("result_version")).longValue()+1);
        i.put("verification",missing.isEmpty()?"PENDING":"NEEDS_EVIDENCE");i.put("phase","VERIFYING");i.put("channel","CLOSED");data.put("missingAssertions",missing);data.put("syncStatus","NOT_ACCEPTED");if(missing.isEmpty())data.put("pendingSince",verificationClock.millis());else data.remove("pendingSince");
        AgentProtocolEvidenceService.refreshHistoricalContributors(jdbc,store,s,data,(String)i.get("id"));
        return Map.of("acceptedSubmission",true,"formalResultAccepted",false,"missingAssertions",missing);
    }
    private void independent(Map<String,Object>s,Map<String,Object>data,AgentProtocolAccess.Actor actor,String instance) {
        require(actor.person()!=null,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");
        access.capability(actor,(String)s.get("environment_id"),"RESULT_REVIEW",true);access.capability(actor,(String)s.get("environment_id"),"VERIFY_EXTERNAL",true);
        access.capability(actor,(String)s.get("environment_id"),"EVIDENCE_READ",true);
        AgentProtocolEvidenceService.refreshHistoricalContributors(jdbc,store,s,data,instance);
        var identities=object(data.getOrDefault("executorIdentities",Map.of()));identities.putAll(object(object(data.getOrDefault("cleanup",Map.of())).getOrDefault("executors",Map.of())));identities.putAll(object(data.getOrDefault("evidenceIdentities",Map.of())));
        var contract=object(data.get("contract"));identities.put(text(contract,"authorUserId"),text(contract,"authorPerson"));
        for(var identity:new TreeMap<>(identities).entrySet()){var people=jdbc.queryForList("SELECT person_ref FROM msp_protocol_identity WHERE organization_id=? AND user_id=? FOR UPDATE",String.class,s.get("organization_id"),identity.getKey());require(people.size()==1&&Objects.equals(people.getFirst(),identity.getValue()),"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");}
        require(!object(object(data.getOrDefault("cleanup",Map.of())).getOrDefault("executors",Map.of())).containsValue(actor.person())&&!strings(data.getOrDefault("executorPersons",List.of())).contains(actor.person())&&!strings(data.getOrDefault("evidencePersons",List.of())).contains(actor.person())
                &&!actor.person().equals(object(data.get("contract")).get("authorPerson")),"PROTOCOL_REVIEW_CONFLICT");
        if(data.containsKey("originalAccepted"))require(!actor.person().equals(object(data.get("originalAccepted")).get("reviewerPerson")),"PROTOCOL_REVIEW_CONFLICT");
    }
    private Map<String,Object> verify(Map<String,Object>args,Map<String,Object>s,Map<String,Object>i,Map<String,Object>data,AgentProtocolAccess.Actor actor) {
        fields(args,"sessionId","caseExecutionId","expectedResultVersion","submissionRevision","verificationAttemptId","assertions","reason","outputsReview","classificationReview");independent(s,data,actor,(String)i.get("id"));
        var incomingReview=objects(args.get("assertions"));String attempt=id(args,"verificationAttemptId");var attempts=object(data.getOrDefault("verificationAttempts",Map.of()));String decisionHash=hash(Map.of("assertions",incomingReview,"outputsReview",args.getOrDefault("outputsReview",Map.of()),"classificationReview",args.getOrDefault("classificationReview",Map.of())));
        if(number(args,"submissionRevision")!=((Number)i.get("revision")).longValue())return Map.of("auditOnly",true,"staleRevision",true);
        if(attempts.containsKey(attempt)) {
            var previous=object(attempts.get(attempt));if(decisionHash.equals(previous.get("hash")))return Map.of("auditOnly",true,"replayedVerification",true);
            data.put("disputed",true);data.put("correctionRequired",true);invalidateConsumers((String)s.get("id"),(String)i.get("id"));
            if(!"ACCEPTED".equals(i.get("verification"))){i.put("verification","UNVERIFIABLE");i.put("phase","WAITING_HUMAN");}
            return Map.of("conflictingAttempt",true,"requiresIndependentCorrection",true);
        }
        require(!"ACCEPTED".equals(i.get("verification")),"PROTOCOL_CORRECTION_REQUIRED");
        require(number(args,"expectedResultVersion")==((Number)i.get("result_version")).longValue(),"VERSION_CONFLICT");
        var reviewed=incomingReview;var declarations=objects(object(data.get("contract")).get("assertions"));
        Set<String> ids=new HashSet<>();boolean violation=false,incomplete=false,unreachable=false;
        for(var decision:reviewed){fields(decision,"assertionId","fact","sufficient","reason","evidence","authorityQuery","queryTime");require(ids.add(id(decision,"assertionId")),"PROTOCOL_ASSERTION_UNKNOWN");
            require(declarations.stream().anyMatch(a->decision.get("assertionId").equals(a.get("id"))),"PROTOCOL_ASSERTION_UNKNOWN");text(decision,"reason");
            require(Set.of("SATISFIED","VIOLATED","UNKNOWN","NOT_REACHED").contains(text(decision,"fact"))&&decision.get("sufficient") instanceof Boolean,"AGENT_VALIDATION_ERROR");
            if(Boolean.TRUE.equals(decision.get("sufficient"))) {
                text(decision,"authorityQuery");number(decision,"queryTime");var refs=evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),decision.getOrDefault("evidence",List.of()),"RESULT");require(!refs.isEmpty(),"PROTOCOL_EVIDENCE_UNAVAILABLE");
                requireIndependentEvidence(s,data,actor,refs);decision.put("evidence",refs);
            }
        }
        for(var declaration:declarations)if(Boolean.TRUE.equals(declaration.get("required"))) {
            var decision=reviewed.stream().filter(d->declaration.get("id").equals(d.get("assertionId"))).findFirst();
            if(decision.isEmpty()||!Boolean.TRUE.equals(decision.get().get("sufficient"))||"UNKNOWN".equals(decision.get().get("fact")))incomplete=true;
            else if("NOT_REACHED".equals(decision.get().get("fact")))unreachable=true;
            else if("VIOLATED".equals(decision.get().get("fact")))violation=true;
        }
        String result=(String)i.get("result"),verification;
        if(unreachable)verification="UNVERIFIABLE";
        else if(incomplete)verification="NEEDS_EVIDENCE";
        else if("PASS".equals(result)&&violation||"FAIL".equals(result)&&!violation)verification="REJECTED";
        else verification="ACCEPTED";
        if(!Set.of("PASS","FAIL").contains(result)){
            var classification=object(args.get("classificationReview"));fields(classification,"result","decision","reason","authorityQuery","queryTime","evidence");require(Objects.equals(result,classification.get("result")),"PROTOCOL_RESULT_CLASSIFICATION_CONFLICT");String decision=text(classification,"decision");require(Set.of("SUPPORTED","REJECTED","UNVERIFIABLE").contains(decision),"AGENT_VALIDATION_ERROR");text(classification,"reason");
            if("SUPPORTED".equals(decision)){text(classification,"authorityQuery");number(classification,"queryTime");var refs=evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),classification.getOrDefault("evidence",List.of()),"RESULT");require(!refs.isEmpty(),"PROTOCOL_EVIDENCE_UNAVAILABLE");requireIndependentEvidence(s,data,actor,refs);classification.put("evidence",refs);}
            verification="SUPPORTED".equals(decision)?violation?"REJECTED":"ACCEPTED":decision;
            if("NOT_RUN".equals(result))require(objects(data.get("steps")).isEmpty()&&objects(data.get("actions")).isEmpty(),"PROTOCOL_RESULT_CLASSIFICATION_CONFLICT");data.put("classificationReview",classification);
        }
        data.put("reviewedAssertions",reviewed);
        if("ACCEPTED".equals(verification)&&"PASS".equals(result)&&!object(data.getOrDefault("outputs",Map.of())).isEmpty()){
            var outputReview=object(args.get("outputsReview"));fields(outputReview,"outputsHash","reason","authorityQuery","queryTime","evidence");require(hash(data.get("outputs")).equals(text(outputReview,"outputsHash")),"VERSION_CONFLICT");text(outputReview,"reason");text(outputReview,"authorityQuery");number(outputReview,"queryTime");var refs=evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),outputReview.getOrDefault("evidence",List.of()),"RESULT");require(!refs.isEmpty(),"PROTOCOL_EVIDENCE_UNAVAILABLE");requireIndependentEvidence(s,data,actor,refs);outputReview.put("evidence",refs);data.put("reviewedOutputs",outputReview);
        }
        if("ACCEPTED".equals(verification)&&Set.of("PASS","FAIL").contains(result))try {
            publication.validateCandidate(s,i,data);
        }catch(io.metersphere.sdk.exception.MSException legacyRejected) {
            String code=legacyRejected.getMessage();data.put("legacyGateIssue",code!=null&&code.matches("[A-Z][A-Z0-9_]{2,63}")?code:"PROTOCOL_LEGACY_GATE_UNAVAILABLE");
            verification="VERSION_CONFLICT".equals(code)?"UNVERIFIABLE":"NEEDS_EVIDENCE";
        }
        attempts.put(attempt,Map.of("hash",decisionHash,"reviewer",actor.user(),"person",actor.person(),"revision",i.get("revision"),"decision",verification));
        data.put("verificationAttempts",attempts);data.put("reviewedAssertions",reviewed);data.put("reviewerPerson",actor.person());data.put("reviewerUserId",actor.user());data.put("reviewedAt",System.currentTimeMillis());
        data.put("confirmedDefects",reviewed.stream().filter(d->"VIOLATED".equals(d.get("fact"))&&Boolean.TRUE.equals(d.get("sufficient"))).toList());
        i.put("verification",verification);i.put("result_version",((Number)i.get("result_version")).longValue()+1);
        i.put("phase","ACCEPTED".equals(verification)?"ENDED":Set.of("REJECTED","UNVERIFIABLE").contains(verification)?"WAITING_HUMAN":"VERIFYING");
        if("ACCEPTED".equals(verification)) {
            data.put("syncStatus",Set.of("PASS","FAIL").contains(result)?"PENDING":"NOT_APPLICABLE");data.put("disputed",false);
            jdbc.update("UPDATE msp_knowledge_usage SET result_version=? WHERE instance_id=? AND state='OUTCOME_RECORDED'",i.get("result_version"),i.get("id"));
            for(var used:jdbc.queryForList("SELECT DISTINCT knowledge_id,knowledge_version FROM msp_knowledge_usage WHERE instance_id=? AND state='OUTCOME_RECORDED'",i.get("id")))jdbc.update("UPDATE msp_knowledge_version SET last_verified_at=? WHERE knowledge_id=? AND version=?",System.currentTimeMillis(),used.get("knowledge_id"),used.get("knowledge_version"));
        }
        return Map.of("verificationAccepted","ACCEPTED".equals(verification),"formalResultAccepted",false,"verification",verification,"syncStatus",data.getOrDefault("syncStatus","NOT_ACCEPTED"));
    }
    private void requireIndependentEvidence(Map<String,Object> session,Map<String,Object> data,AgentProtocolAccess.Actor actor,List<Map<String,Object>> refs){for(var ref:refs){String user="PERSONAL".equals(ref.get("kind"))?jdbc.queryForObject("SELECT user_id FROM agent_case_evidence WHERE id=?",String.class,ref.get("id")):jdbc.queryForObject("SELECT create_user FROM ai_execution_artifact WHERE id=?",String.class,ref.get("id"));var persons=jdbc.queryForList("SELECT person_ref FROM msp_protocol_identity WHERE organization_id=? AND user_id=? FOR UPDATE",String.class,session.get("organization_id"),user);require(persons.size()==1,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");require(!actor.person().equals(persons.getFirst()),"PROTOCOL_REVIEW_CONFLICT");var identities=object(data.getOrDefault("evidenceIdentities",Map.of()));identities.put(user,persons.getFirst());data.put("evidenceIdentities",identities);var contributors=new HashSet<>(strings(data.getOrDefault("evidencePersons",List.of())));contributors.add(persons.getFirst());data.put("evidencePersons",new ArrayList<>(contributors));}}
    private Map<String,Object> close(Map<String,Object>args,Map<String,Object>s,Map<String,Object>i,Map<String,Object>data,AgentProtocolAccess.Actor actor) {
        fields(args,"sessionId","caseExecutionId","expectedResultVersion","submissionRevision","reason","retainUnknown","unverifiableReason");access.capability(actor,(String)s.get("environment_id"),"GOVERN",true);text(args,"reason");
        if(Set.of("PENDING","NEEDS_EVIDENCE").contains(i.get("verification"))){require(Set.of("NO_QUALIFIED_REVIEWER","NO_AUTHORITY_QUERY","EVIDENCE_UNAVAILABLE").contains(args.get("unverifiableReason")),"PROTOCOL_RESULT_STATE_CONFLICT");i.put("verification","UNVERIFIABLE");i.put("result_version",((Number)i.get("result_version")).longValue()+1);data.put("unverifiableReason",args.get("unverifiableReason"));}
        require(Set.of("REJECTED","UNVERIFIABLE","EXPIRED").contains(i.get("verification")),"PROTOCOL_RESULT_STATE_CONFLICT");
        require(!AgentGovernedExecutionService.unresolved(data)||Boolean.TRUE.equals(args.get("retainUnknown")),"PROTOCOL_ACTION_UNKNOWN");
        i.put("phase","ENDED");i.put("channel","CLOSED");data.put("closedUnacceptedReason",args.get("reason"));return Map.of("accepted",false,"unknownRetained",AgentGovernedExecutionService.unresolved(data));
    }
    private Map<String,Object> correct(Map<String,Object>args,Map<String,Object>s,Map<String,Object>i,Map<String,Object>data,AgentProtocolAccess.Actor actor) {
        fields(args,"sessionId","caseExecutionId","expectedResultVersion","reason","result");independent(s,data,actor,(String)i.get("id"));text(args,"reason");require(!actor.person().equals(data.get("reviewerPerson")),"PROTOCOL_REVIEW_CONFLICT");
        require("ACCEPTED".equals(i.get("verification")),"PROTOCOL_RESULT_STATE_CONFLICT");
        data.put("originalAccepted",Map.of("result",i.get("result"),"resultVersion",i.get("result_version"),"reviewerPerson",data.get("reviewerPerson"),"assertions",data.get("reviewedAssertions"),"syncStatus",data.getOrDefault("syncStatus","UNKNOWN")));
        if(args.containsKey("result")){require(RESULTS.contains(args.get("result")),"AGENT_VALIDATION_ERROR");i.put("result",args.get("result"));}
        i.put("revision",((Number)i.get("revision")).longValue()+1);i.put("result_version",((Number)i.get("result_version")).longValue()+1);i.put("verification","PENDING");i.put("phase","VERIFYING");
        data.put("disputed",true);data.put("correctionReason",args.get("reason"));data.put("syncStatus","NOT_ACCEPTED");data.put("verificationAttempts",new LinkedHashMap<>());
        data.put("pendingSince",verificationClock.millis());
        invalidateConsumers((String)s.get("id"),(String)i.get("id"));return Map.of("correctionRequested",true,"originalPreserved",true);
    }
    void invalidateConsumers(String session,String producer) {
        var affected=new HashSet<>(Set.of(producer));boolean changed;
        do {changed=false;for(var row:jdbc.queryForList("SELECT * FROM msp_protocol_instance WHERE session_id=? ORDER BY id FOR UPDATE",session)) {
            if(affected.contains(row.get("id")))continue;var content=store.data(row);
            if(object(content.getOrDefault("dependencies",Map.of())).values().stream().map(AgentProtocolData::object).anyMatch(dep->affected.contains(dep.get("instanceId")))) {
                content.put("upstreamDisputed",true);content.put("needsRevalidation",true);content.put("affectedAt",System.currentTimeMillis());
                jdbc.update("UPDATE msp_protocol_instance SET payload=?,phase=CASE WHEN phase IN ('PRECHECK','QUEUED') THEN 'PAUSED' ELSE phase END WHERE id=?",store.payload(content),row.get("id"));affected.add((String)row.get("id"));changed=true;
            }
        }}while(changed);
    }
}
