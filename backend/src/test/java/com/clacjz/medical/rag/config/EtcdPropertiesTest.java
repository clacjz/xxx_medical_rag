package com.clacjz.medical.rag.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EtcdPropertiesTest {

    @Test
    void testDefaultValues() {
        EtcdProperties properties = new EtcdProperties();

        assertEquals("http://localhost:2379", properties.getUris()[0]);
        assertEquals(5000, properties.getTimeout());
        assertEquals(5000, properties.getConnectionTimeout());
        assertEquals("", properties.getNamespace());
        assertNotNull(properties.getLock());
        assertNotNull(properties.getWatch());
    }

    @Test
    void testLockConfigDefaults() {
        EtcdProperties.LockConfig lockConfig = new EtcdProperties.LockConfig();

        assertEquals(3, lockConfig.getMaxRetries());
        assertEquals(100, lockConfig.getRetryDelayMs());
        assertEquals(30000, lockConfig.getMaxLockWaitTimeMs());
    }

    @Test
    void testWatchConfigDefaults() {
        EtcdProperties.WatchConfig watchConfig = new EtcdProperties.WatchConfig();

        assertEquals(3, watchConfig.getMaxRetries());
        assertEquals(1000, watchConfig.getRetryDelayMs());
    }

    @Test
    void testSetters() {
        EtcdProperties properties = new EtcdProperties();

        properties.setUris(new String[]{"http://etcd1:2379", "http://etcd2:2379"});
        properties.setUsername("root");
        properties.setPassword("password");
        properties.setTimeout(10000);
        properties.setConnectionTimeout(10000);
        properties.setNamespace("app");

        assertEquals(2, properties.getUris().length);
        assertEquals("root", properties.getUsername());
        assertEquals("password", properties.getPassword());
        assertEquals(10000, properties.getTimeout());
        assertEquals(10000, properties.getConnectionTimeout());
        assertEquals("app", properties.getNamespace());
    }

    @Test
    void testLockConfigSetters() {
        EtcdProperties.LockConfig lockConfig = new EtcdProperties.LockConfig();

        lockConfig.setMaxRetries(5);
        lockConfig.setRetryDelayMs(200);
        lockConfig.setMaxLockWaitTimeMs(60000);

        assertEquals(5, lockConfig.getMaxRetries());
        assertEquals(200, lockConfig.getRetryDelayMs());
        assertEquals(60000, lockConfig.getMaxLockWaitTimeMs());
    }

    @Test
    void testWatchConfigSetters() {
        EtcdProperties.WatchConfig watchConfig = new EtcdProperties.WatchConfig();

        watchConfig.setMaxRetries(10);
        watchConfig.setRetryDelayMs(2000);

        assertEquals(10, watchConfig.getMaxRetries());
        assertEquals(2000, watchConfig.getRetryDelayMs());
    }

    @Test
    void testNestedConfig() {
        EtcdProperties properties = new EtcdProperties();

        EtcdProperties.LockConfig lockConfig = properties.getLock();
        lockConfig.setMaxRetries(10);

        EtcdProperties.WatchConfig watchConfig = properties.getWatch();
        watchConfig.setRetryDelayMs(5000);

        assertEquals(10, properties.getLock().getMaxRetries());
        assertEquals(5000, properties.getWatch().getRetryDelayMs());
    }
}
