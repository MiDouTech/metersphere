package io.metersphere.agent.service;

/** Monotonic elapsed time anchored to a server epoch for durable recovery intervals. */
final class AgentProtocolClock {
    private static final long EPOCH=System.currentTimeMillis(),NANOS=System.nanoTime();
    private AgentProtocolClock(){}
    static long now(){return EPOCH+java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-NANOS);}
}
