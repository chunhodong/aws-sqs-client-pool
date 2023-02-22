package com.github.chunhodong.awssqsclient.pool;

import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient;
import com.github.chunhodong.awssqsclient.client.AwsSQSClient;
import com.github.chunhodong.awssqsclient.client.ProxyAwsSQSClient;
import com.github.chunhodong.awssqsclient.client.SQSClient;
import com.github.chunhodong.awssqsclient.utils.Timeout;
import org.springframework.cloud.aws.messaging.core.QueueMessagingTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public abstract class DefaultAwsSQSClientPool implements AwsSQSClientPool {

    protected final Object lock = new Object();
    private final List<PoolEntry> entries;
    private final ThreadLocal<LocalDateTime> clientRequestTime;
    private final AmazonSQSBufferedAsyncClient asyncClient;
    private final Timeout connectionTimeout;
    private final Timeout idleTimeout;
    private final Map<ProxyAwsSQSClient, PoolEntry> proxySQSClients;

    public DefaultAwsSQSClientPool(List<SQSClient> clients,
                                   AmazonSQSBufferedAsyncClient asyncClient,
                                   Timeout connectionTimeout,
                                   Timeout idleTimeout) {
        validateClientPool(clients, asyncClient);
        List<PoolEntry> entries = clients.stream().map(PoolEntry::new).collect(Collectors.toList());
        this.entries = entries;
        this.asyncClient = asyncClient;
        this.clientRequestTime = new ThreadLocal();
        this.connectionTimeout = Objects.requireNonNullElse(connectionTimeout, Timeout.defaultConnectionTime());
        this.idleTimeout = Objects.requireNonNullElse(idleTimeout, Timeout.defaultIdleTime());
        this.proxySQSClients = Collections.synchronizedMap(new HashMap());
    }

    public DefaultAwsSQSClientPool(List<SQSClient> clients,
                                   AmazonSQSBufferedAsyncClient asyncClient) {
        this(clients, asyncClient, Timeout.defaultConnectionTime(), Timeout.defaultIdleTime());
    }

    private void validateClientPool(List<SQSClient> clients, AmazonSQSBufferedAsyncClient asyncClient) {
        Objects.requireNonNull(clients);
        Objects.requireNonNull(asyncClient);
    }

    @Override
    public SQSClient getClient() {
        PoolEntry entry = getEntry();
        ProxyAwsSQSClient proxyAwsSQSClient = new ProxyAwsSQSClient(entry);
        this.proxySQSClients.put(proxyAwsSQSClient, entry);
        return proxyAwsSQSClient;
    }

    @Override
    public PoolEntry getEntry() {
        clientRequestTime.set(LocalDateTime.now());
        do {
            for (PoolEntry entry : entries) {
                if (entry.isClose()) continue;
                if (entry.close()) {
                    clientRequestTime.remove();
                    return entry;
                }
            }
            PoolEntry poolEntry = publishEntry();
            if (Objects.nonNull(poolEntry)) {
                return poolEntry;
            }
        } while (isTimeout());
        clientRequestTime.remove();
        throw new ClientPoolRequestTimeoutException();
    }

    @Override
    public void release(SQSClient sqsClient) {
        PoolEntry poolEntry = proxySQSClients.remove(sqsClient);
        poolEntry.open();
    }

    protected abstract PoolEntry publishEntry();

    protected PoolEntry newEntry(PoolEntryState state) {
        return new PoolEntry(new AwsSQSClient(new QueueMessagingTemplate(asyncClient)), state);
    }

    protected void addEntry(PoolEntry entry) {
        entries.add(entry);
    }

    protected int getPoolSize() {
        return entries.size();
    }

    private boolean isTimeout() {
        return !connectionTimeout.isAfter(clientRequestTime.get());
    }

    protected void removeIdleEntry() {
        List<PoolEntry> pp = entries.stream().filter(poolEntry -> poolEntry.isIdle(idleTimeout))
                .collect(Collectors.toList());

        System.out.println("end");
        try {
            synchronized (lock) {
                try {
                    entries.removeAll(pp);

                } catch (Exception e) {
                    e.printStackTrace();
                }
                finally {
                    return;
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        finally {
            return;
        }


       /* for (PoolEntry entry : entries) {
            System.out.println("ex="+entry);
            if (entry.isIdle(idleTimeout)) {
                System.out.println("remove!!");
                synchronized (lock) {
                    try {
                        entries.remove(entry);

                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }

        }*/
    }
}
