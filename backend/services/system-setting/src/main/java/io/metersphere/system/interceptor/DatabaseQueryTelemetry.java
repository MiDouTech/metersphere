package io.metersphere.system.interceptor;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Counts mapped operations without recording SQL parameters, credentials or case content. */
@Component
@Intercepts({
    @Signature(type=Executor.class, method="query", args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}),
    @Signature(type=Executor.class, method="update", args={MappedStatement.class,Object.class})
})
public class DatabaseQueryTelemetry implements Interceptor {
    public static final class Sample {
        public int operations;
        public long elapsedNanos;
    }
    private static final ThreadLocal<Sample> REQUEST = new ThreadLocal<>();
    private static final java.util.concurrent.atomic.LongAdder TOTAL = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder SLOW = new java.util.concurrent.atomic.LongAdder();
    public static Sample startRequest() { Sample previous=REQUEST.get();REQUEST.set(new Sample());return previous; }
    public static Sample finishRequest(Sample previous) {
        Sample result=REQUEST.get();
        if(previous==null)REQUEST.remove();else REQUEST.set(previous);
        return result;
    }
    public static long operations() { return TOTAL.sum(); }
    public static long slowOperations() { return SLOW.sum(); }

    @Override public Object intercept(Invocation invocation) throws Throwable {
        long start=System.nanoTime();
        try { return invocation.proceed(); }
        finally {
            long elapsed=System.nanoTime()-start;
            TOTAL.increment();
            Sample sample=REQUEST.get();
            if(sample!=null){sample.operations++;sample.elapsedNanos+=elapsed;}
            if(elapsed>=1_000_000_000L) {
                SLOW.increment();
                String mapper=((MappedStatement)invocation.getArgs()[0]).getId();
                LoggerFactory.getLogger(DatabaseQueryTelemetry.class).warn("DB_OPERATION mapper={} durationMs={}",mapper,elapsed/1_000_000);
            }
        }
    }
}
