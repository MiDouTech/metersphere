package io.metersphere.functional.handoff;

import java.sql.SQLException;
import org.springframework.dao.*;
import org.springframework.transaction.TransactionTimedOutException;

/** Only allowlisted classifications cross the logging boundary; never retain a Throwable or its message. */
record HandoffDiagnostic(String category,String exceptionType,String sqlState,int vendorCode) {
    static HandoffDiagnostic from(RuntimeException error) {
        String category=error instanceof CannotAcquireLockException?"DB_LOCK":
                error instanceof QueryTimeoutException||error instanceof TransactionTimedOutException?"DB_TIMEOUT":
                error instanceof DataAccessException?"DB_STATE":"LOCAL_STATE";
        String type=error instanceof DataAccessException?"DataAccessException":
                error instanceof TransactionTimedOutException?"TransactionTimedOutException":
                error instanceof NullPointerException?"NullPointerException":
                error instanceof IllegalArgumentException?"IllegalArgumentException":
                error instanceof IllegalStateException?"IllegalStateException":"RuntimeException";
        Throwable cause=error;
        for(int depth=0;cause!=null&&depth<10;depth++,cause=cause.getCause()) {
            if(cause instanceof HandoffSecretUnavailable secret)
                return new HandoffDiagnostic(secret.reason().name(),"HandoffSecretUnavailable","NONE",0);
            if(cause instanceof SQLException sql) {
                String state=sql.getSQLState();
                return new HandoffDiagnostic(category,"SQLException",state!=null&&state.matches("[A-Z0-9]{5}")?state:"NONE",sql.getErrorCode());
            }
        }
        return new HandoffDiagnostic(category,type,"NONE",0);
    }
    static String event(String value){return value!=null&&value.matches("[a-fA-F0-9-]{36}")?value:"UNAVAILABLE";}
    static String key(String value){return value!=null&&value.matches("[a-fA-F0-9]{64}")?value:"UNAVAILABLE";}
}
