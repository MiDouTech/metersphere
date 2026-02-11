package io.metersphere.system.uid.impl;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.uid.BitsAllocator;
import io.metersphere.system.uid.utils.TimeUtils;
import io.metersphere.system.uid.worker.WorkerIdAssigner;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class DefaultUidGenerator implements DisposableBean {

    /**
     * Bits allocate
     */
    protected int timeBits = 29;
    protected int workerBits = 21;
    protected int seqBits = 13;

    /**
     * Customer epoch, unit: second
     * Example: 2023-09-01
     */
    protected String epochStr = "2023-09-01";
    protected long epochSeconds;

    /**
     * Stable fields after spring bean initializing
     */
    protected BitsAllocator bitsAllocator;
    protected long workerId;

    /**
     * Volatile fields caused by nextId()
     */
    protected long sequence = 0L;
    protected long lastSecond = -1L;

    /**
     * Spring property
     */
    @Resource
    protected WorkerIdAssigner workerIdAssigner;

    /**
     * Initialize UID generator
     */
    @PostConstruct
    public void init() {
        // init epoch (fixed, must not change after deployment)
        setEpochStr(epochStr);

        // initialize bits allocator
        bitsAllocator = new BitsAllocator(timeBits, workerBits, seqBits);

        // initialize worker id
        workerId = workerIdAssigner.assignWorkerId();
        if (workerId > bitsAllocator.getMaxWorkerId()) {
            throw new IllegalStateException(
                    "Worker id " + workerId + " exceeds the max " + bitsAllocator.getMaxWorkerId()
            );
        }

        LogUtils.info(
                "Initialized UID generator bits(time={}, worker={}, seq={}) for workerId={}",
                timeBits, workerBits, seqBits, workerId
        );
    }

    /**
     * Get UID
     */
    public long getUID() {
        return nextId();
    }

    /**
     * Generate next UID
     *
     * @return UID
     * @throws MSException clock rollback or timestamp overflow
     */
    protected synchronized long nextId() {
        long currentSecond = getCurrentSecond();

        // Clock moved backwards
        if (currentSecond < lastSecond) {
            long refusedSeconds = lastSecond - currentSecond;
            throw new MSException(
                    String.format("Clock moved backwards. Refusing for %d seconds", refusedSeconds)
            );
        }

        // Same second, sequence increase
        if (currentSecond == lastSecond) {
            sequence = (sequence + 1) & bitsAllocator.getMaxSequence();
            if (sequence == 0) {
                currentSecond = getNextSecond(lastSecond);
            }
        } else {
            sequence = 0L;
        }

        lastSecond = currentSecond;

        return bitsAllocator.allocate(
                currentSecond - epochSeconds,
                workerId,
                sequence
        );
    }

    /**
     * Wait until next second
     */
    private long getNextSecond(long lastTimestamp) {
        long timestamp;
        do {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MSException("Thread interrupted while waiting for next second");
            }
            timestamp = getCurrentSecond();
        } while (timestamp <= lastTimestamp);

        return timestamp;
    }

    /**
     * Get current second
     */
    private long getCurrentSecond() {
        long currentSecond = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());

        if (currentSecond - epochSeconds > bitsAllocator.getMaxDeltaSeconds()) {
            throw new MSException(
                    "Timestamp bits exhausted. Refusing UID generate. Now second: " + currentSecond
            );
        }

        return currentSecond;
    }

    public void setEpochStr(String epochStr) {
        if (StringUtils.isNotBlank(epochStr)) {
            this.epochStr = epochStr;
            this.epochSeconds = TimeUnit.MILLISECONDS.toSeconds(
                    TimeUtils.parseByDayPattern(epochStr).getTime()
            );
        }
    }

    @Override
    public void destroy() {
        LogUtils.info("Shutdown UID Generator...");
    }
}
