package com.github.chunhodong.awssqsclient.pool;

import com.github.chunhodong.awssqsclient.client.SQSClient;
import com.github.chunhodong.awssqsclient.utils.Timeout;

import java.util.Objects;

public class PoolEntry {

    private final SQSClient sqsClient;
    private PoolEntryState state = PoolEntryState.OPEN;
    private final Object mutex;
    private long accessTime;

    public PoolEntry(SQSClient sqsClient) {
        Objects.nonNull(sqsClient);
        this.sqsClient = sqsClient;
        this.mutex = this;
    }

    public PoolEntry(SQSClient sqsClient, PoolEntryState state) {
        Objects.nonNull(sqsClient);
        this.sqsClient = sqsClient;
        this.state = state;
        this.mutex = this;
    }

    public SQSClient getSqsClient() {
        return sqsClient;
    }

    public boolean isClose() {
        return state == PoolEntryState.CLOSE;
    }

    public void open() {
        state = PoolEntryState.OPEN;
        accessTime = System.currentTimeMillis();
    }

    public boolean close() {
        synchronized (mutex) {
            if (state == PoolEntryState.OPEN) {
                state = PoolEntryState.CLOSE;
                accessTime = System.currentTimeMillis();
                return true;
            }
            return false;
        }
    }

    public boolean isIdle(Timeout idleTimeout) {
        System.out.println("true1="+(state == PoolEntryState.OPEN));
        System.out.println("true2="+(System.currentTimeMillis() - accessTime > idleTimeout.toMilis()));
        System.out.println("true2.1="+(System.currentTimeMillis()));
        System.out.println("true2.2="+(accessTime));
        System.out.println("true2.3="+(idleTimeout.toMilis()));

        System.out.println("============================================");
        return state == PoolEntryState.OPEN && System.currentTimeMillis() - accessTime > idleTimeout.toMilis();
    }
}
