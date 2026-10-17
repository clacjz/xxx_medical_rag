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
import io.etcd.jetcd.lease.LeaseGrantResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class EtcdServiceTest {

    @Mock
    private Client client;

    @Mock
    private KV kvClient;

    @Mock
    private Lease leaseClient;

    @Mock
    private Lock lockClient;

    @Mock
    private Watch watchClient;

    @Mock
    private EtcdConfig etcdConfig;

    @Mock
    private EtcdProperties properties;

    private EtcdService etcdService;

    @BeforeEach
    void setUp() {
        when(client.getKVClient()).thenReturn(kvClient);
        when(client.getLeaseClient()).thenReturn(leaseClient);
        when(client.getLockClient()).thenReturn(lockClient);
        when(client.getWatchClient()).thenReturn(watchClient);

        when(properties.getTimeout()).thenReturn(5000);
        when(properties.getLock()).thenReturn(createLockConfig());
        when(properties.getWatch()).thenReturn(createWatchConfig());
        when(properties.getNamespace()).thenReturn("");

        etcdService = new EtcdService(client, etcdConfig, properties);
    }

    private EtcdProperties.LockConfig createLockConfig() {
        EtcdProperties.LockConfig config = new EtcdProperties.LockConfig();
        config.setMaxRetries(3);
        config.setRetryDelayMs(100);
        config.setMaxLockWaitTimeMs(30000);
        return config;
    }

    private EtcdProperties.WatchConfig createWatchConfig() {
        EtcdProperties.WatchConfig config = new EtcdProperties.WatchConfig();
        config.setMaxRetries(3);
        config.setRetryDelayMs(1000);
        return config;
    }

    @Test
    void testPut_Success() throws Exception {
        when(etcdConfig.getFullKey("testKey")).thenReturn("testKey");

        CompletableFuture<io.etcd.jetcd.kv.PutResponse> future = new CompletableFuture<>();
        io.etcd.jetcd.kv.PutResponse putResponse = mock(io.etcd.jetcd.kv.PutResponse.class);
        future.complete(putResponse);

        when(kvClient.put(any(ByteSequence.class), any(ByteSequence.class)))
                .thenReturn(future);

        etcdService.put("testKey", "testValue");

        verify(kvClient).put(any(ByteSequence.class), any(ByteSequence.class));
    }

    @Test
    void testGet_Success() throws Exception {
        when(etcdConfig.getFullKey("testKey")).thenReturn("testKey");

        List<io.etcd.jetcd.KeyValue> keyValues = new ArrayList<>();
        io.etcd.jetcd.KeyValue keyValue = mock(io.etcd.jetcd.KeyValue.class);
        when(keyValue.getValue()).thenReturn(ByteSequence.from("testValue".getBytes(StandardCharsets.UTF_8)));
        keyValues.add(keyValue);

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(keyValues);

        CompletableFuture<GetResponse> future = new CompletableFuture<>();
        future.complete(getResponse);

        when(kvClient.get(any(ByteSequence.class))).thenReturn(future);

        String result = etcdService.get("testKey");

        assertEquals("testValue", result);
    }

    @Test
    void testGet_NotFound() throws Exception {
        when(etcdConfig.getFullKey("testKey")).thenReturn("testKey");

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(Collections.emptyList());

        CompletableFuture<GetResponse> future = new CompletableFuture<>();
        future.complete(getResponse);

        when(kvClient.get(any(ByteSequence.class))).thenReturn(future);

        String result = etcdService.get("testKey");

        assertNull(result);
    }

    @Test
    void testDelete_Success() throws Exception {
        when(etcdConfig.getFullKey("testKey")).thenReturn("testKey");

        CompletableFuture<io.etcd.jetcd.kv.DeleteResponse> future = new CompletableFuture<>();
        io.etcd.jetcd.kv.DeleteResponse deleteResponse = mock(io.etcd.jetcd.kv.DeleteResponse.class);
        future.complete(deleteResponse);

        when(kvClient.delete(any(ByteSequence.class))).thenReturn(future);

        etcdService.delete("testKey");

        verify(kvClient).delete(any(ByteSequence.class));
    }

    @Test
    void testCreateLease_Success() throws Exception {
        LeaseGrantResponse grantResponse = mock(LeaseGrantResponse.class);
        when(grantResponse.getID()).thenReturn(12345L);

        CompletableFuture<LeaseGrantResponse> future = new CompletableFuture<>();
        future.complete(grantResponse);

        when(leaseClient.grant(anyLong())).thenReturn(future);

        long leaseId = etcdService.createLease(60);

        assertEquals(12345L, leaseId);
        verify(leaseClient).grant(60);
    }

    @Test
    void testCreateLease_DefaultTTL() throws Exception {
        LeaseGrantResponse grantResponse = mock(LeaseGrantResponse.class);
        when(grantResponse.getID()).thenReturn(12345L);

        CompletableFuture<LeaseGrantResponse> future = new CompletableFuture<>();
        future.complete(grantResponse);

        when(leaseClient.grant(anyLong())).thenReturn(future);

        long leaseId = etcdService.createLease(0);

        verify(leaseClient).grant(60);
    }

    @Test
    void testRevokeLease_Success() throws Exception {
        CompletableFuture<io.etcd.jetcd.lease.LeaseRevokeResponse> future = new CompletableFuture<>();
        io.etcd.jetcd.lease.LeaseRevokeResponse revokeResponse = mock(io.etcd.jetcd.lease.LeaseRevokeResponse.class);
        future.complete(revokeResponse);

        when(leaseClient.revoke(anyLong())).thenReturn(future);

        etcdService.revokeLease(12345L);

        verify(leaseClient).revoke(12345L);
    }

    @Test
    void testAcquireLock_Success() throws Exception {
        when(etcdConfig.getFullKey("lockName")).thenReturn("lockName");

        io.etcd.jetcd.lock.LockResponse lockResponse = mock(io.etcd.jetcd.lock.LockResponse.class);
        when(lockResponse.getKey()).thenReturn(ByteSequence.from("lockName_uuid".getBytes(StandardCharsets.UTF_8)));

        CompletableFuture<io.etcd.jetcd.lock.LockResponse> future = new CompletableFuture<>();
        future.complete(lockResponse);

        when(lockClient.lock(any(ByteSequence.class), anyLong())).thenReturn(future);

        String lockKey = etcdService.acquireLock("lockName");

        assertNotNull(lockKey);
    }

    @Test
    void testReleaseLock_Success() throws Exception {
        when(etcdConfig.getFullKey("lockName")).thenReturn("lockName");
        when(client.getLockClient()).thenReturn(lockClient);

        CompletableFuture<io.etcd.jetcd.lock.UnlockResponse> future = new CompletableFuture<>();
        io.etcd.jetcd.lock.UnlockResponse unlockResponse = mock(io.etcd.jetcd.lock.UnlockResponse.class);
        future.complete(unlockResponse);

        when(lockClient.unlock(any(ByteSequence.class))).thenReturn(future);

        io.etcd.jetcd.lock.LockResponse lockResponse = mock(io.etcd.jetcd.lock.LockResponse.class);
        when(lockResponse.getKey()).thenReturn(ByteSequence.from("lockName_uuid".getBytes(StandardCharsets.UTF_8)));

        CompletableFuture<io.etcd.jetcd.lock.LockResponse> lockFuture = new CompletableFuture<>();
        lockFuture.complete(lockResponse);

        when(lockClient.lock(any(ByteSequence.class), anyLong())).thenReturn(lockFuture);

        etcdService.acquireLock("lockName");
        etcdService.releaseLock("lockName");

        verify(lockClient).unlock(any(ByteSequence.class));
    }

    @Test
    void testTryLock_Success() throws Exception {
        when(etcdConfig.getFullKey("lockName")).thenReturn("lockName");

        io.etcd.jetcd.lock.LockResponse lockResponse = mock(io.etcd.jetcd.lock.LockResponse.class);
        when(lockResponse.getKey()).thenReturn(ByteSequence.from("lockName_uuid".getBytes(StandardCharsets.UTF_8)));

        CompletableFuture<io.etcd.jetcd.lock.LockResponse> future = new CompletableFuture<>();
        future.complete(lockResponse);

        when(lockClient.lock(any(ByteSequence.class), anyLong())).thenReturn(future);

        boolean result = etcdService.tryLock("lockName");

        assertTrue(result);
    }

    @Test
    void testTryLock_Failure() throws Exception {
        when(etcdConfig.getFullKey("lockName")).thenReturn("lockName");

        CompletableFuture<io.etcd.jetcd.lock.LockResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new Exception("Lock not available"));

        when(lockClient.lock(any(ByteSequence.class), anyLong())).thenReturn(future);

        boolean result = etcdService.tryLock("lockName");

        assertFalse(result);
    }

    @Test
    void testExists_True() throws Exception {
        when(etcdConfig.getFullKey("testKey")).thenReturn("testKey");

        List<io.etcd.jetcd.KeyValue> keyValues = new ArrayList<>();
        io.etcd.jetcd.KeyValue keyValue = mock(io.etcd.jetcd.KeyValue.class);
        keyValues.add(keyValue);

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(keyValues);

        CompletableFuture<GetResponse> future = new CompletableFuture<>();
        future.complete(getResponse);

        when(kvClient.get(any(ByteSequence.class))).thenReturn(future);

        boolean exists = etcdService.exists("testKey");

        assertTrue(exists);
    }

    @Test
    void testExists_False() throws Exception {
        when(etcdConfig.getFullKey("testKey")).thenReturn("testKey");

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(Collections.emptyList());

        CompletableFuture<GetResponse> future = new CompletableFuture<>();
        future.complete(getResponse);

        when(kvClient.get(any(ByteSequence.class))).thenReturn(future);

        boolean exists = etcdService.exists("testKey");

        assertFalse(exists);
    }

    @Test
    void testIncrementCounter_New() throws Exception {
        when(etcdConfig.getFullKey("counters/testKey")).thenReturn("counters/testKey");

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(Collections.emptyList());

        CompletableFuture<GetResponse> getFuture = new CompletableFuture<>();
        getFuture.complete(getResponse);

        CompletableFuture<io.etcd.jetcd.kv.PutResponse> putFuture = new CompletableFuture<>();
        io.etcd.jetcd.kv.PutResponse putResponse = mock(io.etcd.jetcd.kv.PutResponse.class);
        putFuture.complete(putResponse);

        when(kvClient.get(any(ByteSequence.class))).thenReturn(getFuture);
        when(kvClient.put(any(ByteSequence.class), any(ByteSequence.class))).thenReturn(putFuture);

        long result = etcdService.incrementCounter("testKey");

        assertEquals(1, result);
    }

    @Test
    void testIncrementCounter_Existing() throws Exception {
        when(etcdConfig.getFullKey("counters/testKey")).thenReturn("counters/testKey");

        List<io.etcd.jetcd.KeyValue> keyValues = new ArrayList<>();
        io.etcd.jetcd.KeyValue keyValue = mock(io.etcd.jetcd.KeyValue.class);
        when(keyValue.getValue()).thenReturn(ByteSequence.from("5".getBytes(StandardCharsets.UTF_8)));
        keyValues.add(keyValue);

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(keyValues);

        CompletableFuture<GetResponse> getFuture = new CompletableFuture<>();
        getFuture.complete(getResponse);

        CompletableFuture<io.etcd.jetcd.kv.PutResponse> putFuture = new CompletableFuture<>();
        io.etcd.jetcd.kv.PutResponse putResponse = mock(io.etcd.jetcd.kv.PutResponse.class);
        putFuture.complete(putResponse);

        when(kvClient.get(any(ByteSequence.class))).thenReturn(getFuture);
        when(kvClient.put(any(ByteSequence.class), any(ByteSequence.class))).thenReturn(putFuture);

        long result = etcdService.incrementCounter("testKey");

        assertEquals(6, result);
    }

    @Test
    void testGetCounter_NotExists() throws Exception {
        when(etcdConfig.getFullKey("counters/testKey")).thenReturn("counters/testKey");

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getKvs()).thenReturn(Collections.emptyList());

        CompletableFuture<GetResponse> future = new CompletableFuture<>();
        future.complete(getResponse);

        when(kvClient.get(any(ByteSequence.class))).thenReturn(future);

        long result = etcdService.getCounter("testKey");

        assertEquals(0, result);
    }

}
