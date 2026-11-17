package io.metersphere.agent.service;

import java.util.*;

/** Allowlisted failure metadata. Exception messages, request values and Throwable stacks never enter logs. */
public final class AgentProtocolDiagnostics {
    private AgentProtocolDiagnostics(){}
    public record Failure(String category,String rootType,String sqlState,int vendorCode,String location){}
    public static Failure describe(Throwable failure){
        String category="INTERNAL",root="UNKNOWN",state="UNKNOWN",location="UNKNOWN";int vendor=0;Set<Throwable> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable cause=failure;cause!=null&&visited.size()<32&&visited.add(cause);cause=cause.getCause()){
            root=cause.getClass().getSimpleName();
            if(cause instanceof java.sql.SQLException sql){category="DATABASE";String candidate=sql.getSQLState();if(candidate!=null&&candidate.matches("[A-Za-z0-9]{5}"))state=candidate;vendor=sql.getErrorCode();}
            else if(cause instanceof java.net.SocketTimeoutException||cause instanceof java.net.http.HttpTimeoutException)category="NETWORK_TIMEOUT";
            else if(cause instanceof java.net.ConnectException)category="NETWORK_CONNECTION";
            else if(cause instanceof java.net.ProtocolException||cause instanceof javax.net.ssl.SSLException)category="NETWORK_PROTOCOL";
            else if(cause instanceof java.io.IOException&&"INTERNAL".equals(category))category="STORAGE_IO";
            if("UNKNOWN".equals(location))for(var frame:cause.getStackTrace())if(frame.getClassName().startsWith("io.metersphere.")){location=frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber();break;}
        }
        return new Failure(category,root,state,vendor,location);
    }
    public static void warn(org.slf4j.Logger logger,String stage,String code,String resource,Throwable failure){
        Failure safe=describe(failure);String trace=Objects.toString(org.slf4j.MDC.get("traceId"),UUID.randomUUID().toString());
        logger.warn("Protocol failure traceId={} stage={} code={} resourceId={} category={} rootType={} sqlState={} vendorCode={} location={}",trace,stage,code,resource,safe.category(),safe.rootType(),safe.sqlState(),safe.vendorCode(),safe.location());
    }
}
