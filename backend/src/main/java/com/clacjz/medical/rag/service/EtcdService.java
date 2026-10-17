package com.clacjz.medical.rag.service;

import com.clacjz.medical.rag.config.EtcdConfig;
import com.clacjz.medical.rag.config.EtcdProperties;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Lock;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.watch.WatchResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtcdService {

    private final Client client;
    private final EtcdConfig etcdConfig;
    private final EtcdProperties properties;

    private final Map<String, Watch> watchMap = new ConcurrentHashMap<>();
    private final Map<String, Lock> lockMap = new ConcurrentHashMap<>();

    private static final int MAX_WATCHERS = 100;

    @PostConstruct
    public void init() {
        log.info("Etcd service initialized with namespace: {}",
                etcdConfig.getNamespace() != null ? etcdConfig.getNamespace() : "(none)");
    }

    private void checkWatcherLimit() {
        if (watchMap.size() >= MAX_WATCHERS) {
            throw new IllegalStateException(
                    "Maximum number of watchers (" + MAX_WATCHERS + ") exceeded. Please stop some watchers before adding new ones.");
        }
    }

    public int getActiveWatcherCount() {
        return watchMap.size();
    }

    public int getActiveLockCount() {
        return lockMap.size();
    }

    private ByteSequence toBytes(String value) {
        return ByteSequence.from(value.getBytes(StandardCharsets.UTF_8));
    }

    public void put(String key, String value) throws Exception {
        put(key, value, 0);
    }

    public void put(String key, String value, long leaseId) throws Exception {
        String fullKey = etcdConfig.getFullKey(key);
        log.debug("Putting key-value: {}={}", fullKey, value);

        KV kvClient = client.getKVClient();
        PutOption.Builder optionBuilder = PutOption.builder();
        if (leaseId > 0) {
            optionBuilder.withLeaseId(leaseId);
        }

        kvClient.put(toBytes(fullKey), toBytes(value))
                .get(properties.getTimeout(), TimeUnit.MILLISECONDS);

        log.info("Put successful: {}", fullKey);
    }

    public String get(String key) throws Exception {
        String fullKey = etcdConfig.getFullKey(key);
        log.debug("Getting key: {}", fullKey);

        KV kvClient = client.getKVClient();
        GetResponse response = kvClient
                .get(toBytes(fullKey))
                .get(properties.getTimeout(), TimeUnit.MILLISECONDS);

        // NPE防护：虽然上面检查了isEmpty()，但为了代码健壮性，在获取元素前再次检查
        if (response.getKvs().isEmpty() || response.getKvs().get(0) == null) {
            log.debug("Key not found: {}", fullKey);
            return null;
        }

        String value = response.getKvs().get(0).getValue().toString(StandardCharsets.UTF_8);
        log.debug("Got value for key {}: {}", fullKey, value);
        return value;
    }

    public Map<String, String> getPrefix(String prefix) throws Exception {
        String fullPrefix = etcdConfig.getFullKey(prefix);
        log.debug("Getting keys with prefix: {}", fullPrefix);

        KV kvClient = client.getKVClient();
        GetOption option = GetOption.builder()
                .withPrefix(toBytes(fullPrefix))
                .build();

        GetResponse response = kvClient
                .get(toBytes(fullPrefix), option)
                .get(properties.getTimeout(), TimeUnit.MILLISECONDS);

        Map<String, String> result = new HashMap<>();
        response.getKvs().forEach(kv -> {
            String k = kv.getKey().toString(StandardCharsets.UTF_8);
            String v = kv.getValue().toString(StandardCharsets.UTF_8);
            result.put(k, v);
        });

        log.info("Found {} keys with prefix {}", result.size(), fullPrefix);
        return result;
    }

    public void delete(String key) throws Exception {
        String fullKey = etcdConfig.getFullKey(key);
        log.debug("Deleting key: {}", fullKey);

        KV kvClient = client.getKVClient();
        kvClient.delete(toBytes(fullKey))
                .get(properties.getTimeout(), TimeUnit.MILLISECONDS);

        log.info("Deleted key: {}", fullKey);
    }

    public void deletePrefix(String prefix) throws Exception {
        String fullPrefix = etcdConfig.getFullKey(prefix);
        log.debug("Deleting keys with prefix: {}", fullPrefix);

        KV kvClient = client.getKVClient();
        DeleteOption option = DeleteOption.builder()
                .withPrefix(toBytes(fullPrefix))
                .build();

        kvClient.delete(toBytes(fullPrefix), option)
                .get(properties.getTimeout(), TimeUnit.MILLISECONDS);

        log.info("Deleted keys with prefix: {}", fullPrefix);
    }

    public long createLease(long ttlSeconds) throws Exception {
        log.debug("Creating lease with TTL: {} seconds", ttlSeconds);

        if (ttlSeconds <= 0) {
            ttlSeconds = 60;
        }

        Lease leaseClient = client.getLeaseClient();
        io.etcd.jetcd.lease.LeaseGrantResponse response = leaseClient.grant(ttlSeconds)
                .get(properties.getTimeout(), TimeUnit.MILLISECONDS);

        log.info("Created lease: {}", response.getID());
        return response.getID();
    }

    public void revokeLease(long leaseId) throws Exception {
        log.debug("Revoking lease: {}", leaseId);
        client.getLeaseClient().revoke(leaseId)
                .get(properties.getTimeout(), TimeUnit.MILLISECONDS);
        log.info("Revoked lease: {}", leaseId);
    }

    public void keepAlive(long leaseId) throws Exception {
        log.debug("Keeping lease alive: {}", leaseId);
        client.getLeaseClient().keepAliveOnce(leaseId)
                .get(properties.getTimeout(), TimeUnit.MILLISECONDS);
        log.debug("Lease {} keep alive successful", leaseId);
    }

    public void watch(String key, Consumer<WatchResponse> consumer) {
        String fullKey = etcdConfig.getFullKey(key);
        log.info("Starting watch on key: {}", fullKey);

        if (watchMap.containsKey(fullKey)) {
            log.warn("Already watching key: {}", fullKey);
            return;
        }

        checkWatcherLimit();

        Watch watch = client.getWatchClient();
        watch.watch(toBytes(fullKey), event -> {
            log.debug("Watch event received for {}: {}", fullKey, event);
            try {
                consumer.accept(event);
            } catch (Exception e) {
                log.error("Error processing watch event for key {}", fullKey, e);
            }
        });
        watchMap.put(fullKey, watch);

        log.info("Started watching key: {} (active watchers: {})", fullKey, watchMap.size());
    }

    public void watchPrefix(String prefix, Consumer<WatchResponse> consumer) {
        String fullPrefix = etcdConfig.getFullKey(prefix);
        log.info("Starting watch on prefix: {}", fullPrefix);

        String watchKey = "prefix:" + fullPrefix;
        if (watchMap.containsKey(watchKey)) {
            log.warn("Already watching prefix: {}", fullPrefix);
            return;
        }

        checkWatcherLimit();

        Watch watch = client.getWatchClient();
        watch.watch(toBytes(fullPrefix), event -> {
            log.debug("Watch event received for prefix {}: {}", fullPrefix, event);
            try {
                consumer.accept(event);
            } catch (Exception e) {
                log.error("Error processing watch event for prefix {}", fullPrefix, e);
            }
        });
        watchMap.put(watchKey, watch);

        log.info("Started watching prefix: {} (active watchers: {})", fullPrefix, watchMap.size());
    }

    public void stopWatch(String key) {
        String fullKey = etcdConfig.getFullKey(key);
        String watchKey = "prefix:" + fullKey;

        Watch watch = watchMap.remove(fullKey);
        if (watch == null) {
            watch = watchMap.remove(watchKey);
        }

        if (watch != null) {
            watch.close();
            log.info("Stopped watching: {}", fullKey);
        }
    }

    public void stopAllWatches() {
        log.info("Stopping all watches");
        watchMap.values().forEach(Watch::close);
        watchMap.clear();
        log.info("All watches stopped");
    }

    public String acquireLock(String lockName) throws Exception {
        return acquireLock(lockName, properties.getLock().getMaxLockWaitTimeMs());
    }

    public String acquireLock(String lockName, long waitTimeMs) throws Exception {
        String fullLockName = etcdConfig.getFullKey(lockName);
        log.info("Acquiring lock: {}", fullLockName);

        Lock lock = client.getLockClient();
        lockMap.put(fullLockName, lock);

        io.etcd.jetcd.lock.LockResponse lockResponse = lock.lock(toBytes(fullLockName), waitTimeMs)
                .get(waitTimeMs, TimeUnit.MILLISECONDS);

        String lockKey = lockResponse.getKey().toString(StandardCharsets.UTF_8);
        log.info("Lock acquired: {} -> {}", fullLockName, lockKey);
        return lockKey;
    }

    public void releaseLock(String lockName) throws Exception {
        String fullLockName = etcdConfig.getFullKey(lockName);
        log.info("Releasing lock: {}", fullLockName);

        Lock lock = lockMap.remove(fullLockName);
        if (lock != null) {
            lock.unlock(toBytes(fullLockName))
                    .get(properties.getTimeout(), TimeUnit.MILLISECONDS);
            log.info("Lock released: {}", fullLockName);
        } else {
            log.warn("Lock not found: {}", fullLockName);
        }
    }

    public void registerService(String serviceName, String host, int port) throws Exception {
        String key = "services/" + serviceName;
        String value = host + ":" + port;
        long leaseId = createLease(30);
        put(key, value, leaseId);
        log.info("Service registered: {} -> {} (lease: {})", serviceName, value, leaseId);

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(() -> {
            try {
                keepAlive(leaseId);
                log.debug("Service lease refreshed: {}", serviceName);
            } catch (Exception e) {
                log.error("Failed to refresh service lease: {}", serviceName, e);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    public void deregisterService(String serviceName) throws Exception {
        String key = "services/" + serviceName;
        delete(key);
        log.info("Service deregistered: {}", serviceName);
    }

    public Map<String, String> getServices() throws Exception {
        return getPrefix("services/");
    }

    public String getService(String serviceName) throws Exception {
        String key = "services/" + serviceName;
        return get(key);
    }

    public boolean tryLock(String lockName) {
        try {
            acquireLock(lockName, 100);
            return true;
        } catch (Exception e) {
            log.debug("Failed to acquire lock {}: {}", lockName, e.getMessage());
            return false;
        }
    }

    public <T> T executeWithLock(String lockName, Callable<T> callable) throws Exception {
        String lockKey = acquireLock(lockName);
        try {
            return callable.call();
        } finally {
            releaseLock(lockName);
        }
    }

    public long incrementCounter(String counterName) throws Exception {
        String currentValue = get("counters/" + counterName);
        long newValue = (currentValue == null) ? 1 : Long.parseLong(currentValue) + 1;
        put("counters/" + counterName, String.valueOf(newValue));
        log.debug("Counter {} incremented to {}", counterName, newValue);
        return newValue;
    }

    public long getCounter(String counterName) throws Exception {
        String value = get("counters/" + counterName);
        return (value == null) ? 0 : Long.parseLong(value);
    }

    public void putWithTtl(String key, String value, long ttlSeconds) throws Exception {
        long leaseId = createLease(ttlSeconds);
        put(key, value, leaseId);
        log.debug("Put key {} with TTL {} seconds", key, ttlSeconds);
    }

    public boolean exists(String key) throws Exception {
        String fullKey = etcdConfig.getFullKey(key);
        KV kvClient = client.getKVClient();
        GetResponse response = kvClient
                .get(toBytes(fullKey))
                .get(properties.getTimeout(), TimeUnit.MILLISECONDS);
        return !response.getKvs().isEmpty();
    }

    public List<String> listKeys(String prefix) throws Exception {
        Map<String, String> all = getPrefix(prefix);
        return new ArrayList<>(all.keySet());
    }

    public long getTtl(String key) throws Exception {
        String fullKey = etcdConfig.getFullKey(key);
        KV kvClient = client.getKVClient();

        GetResponse response = kvClient
                .get(toBytes(fullKey))
                .get(properties.getTimeout(), TimeUnit.MILLISECONDS);

        // NPE防护：确保kvs列表不为空且第一个元素不为null
        if (response.getKvs().isEmpty() || response.getKvs().get(0) == null) {
            return -1;
        }

        long leaseId = response.getKvs().get(0).getLease();
        if (leaseId == 0) {
            return -1;
        }

        // Lease 非零，但无法通过客户端 lease 查询获取剩余 TTL
        // 且不额外发起 API 调用，返回 -1 表示 TTL 未知
        return -1;
    }

    public void close() {
        log.info("Closing EtcdService resources");
        stopAllWatches();
        lockMap.clear();
        client.close();
        log.info("EtcdService resources closed");
    }
}
