package io.metersphere.agent.service;

import org.springframework.stereotype.Service;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

@Service
public class AgentProtocolToolService {
    public static final List<String> OPERATIONS=List.of("start","resume","lease.takeover","case.prepare","context.save","action.prepare","action.resolve","action.status","cleanup.lease.takeover","cleanup.prepare","cleanup.resolve","cleanup.recovery.record","data.record","step.save","recovery.record","human.request","human.resolve","checkpoint.get","case.finish","finish","cancel","result.evidence.append","result.status.get","result.history.get","result.close-unaccepted","result.revalidate","result.correction.request","result.sync","governance.transfer","knowledge.adopt","knowledge.substitution.propose","knowledge.substitution.confirm","observation.append");
    private final AgentGovernedExecutionService service;private final AgentKnowledgeService knowledge;private final AgentProtocolPublicationService publication;private final AgentProtocolImageService images;
    public AgentProtocolToolService(AgentGovernedExecutionService service,AgentKnowledgeService knowledge,AgentProtocolPublicationService publication,AgentProtocolImageService images){this.service=service;this.knowledge=knowledge;this.publication=publication;this.images=images;}
    public Object execute(String operation,Map<String,Object> args) {
        if("evidence.upload".equals(operation))return images.upload(args);
        if(Set.of("knowledge.search","knowledge.get").contains(operation))return service.knowledgeRead(operation,args);
        if("knowledge.save".equals(operation)) {var request=new LinkedHashMap<>(args);String org=id(request,"organizationId");request.remove("organizationId");return knowledge.save(org,request);}
        require(OPERATIONS.contains(operation),"AGENT_VALIDATION_ERROR");
        if("start".equals(operation))return service.start(args);
        if(Set.of("checkpoint.get","action.status","result.status.get","result.history.get").contains(operation))return service.read(operation,args);
        if("result.sync".equals(operation))return publication.syncRequest(args);
        return service.write(operation,args);
    }
}
