package com.clacjz.medical.rag.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElasticsearchPropertiesTest {

    @Test
    void testDefaultValues() {
        ElasticsearchProperties properties = new ElasticsearchProperties();

        assertEquals("http://localhost:9200", properties.getUris()[0]);
        assertEquals(5000, properties.getConnectionTimeout());
        assertEquals(30000, properties.getSocketTimeout());
        assertEquals(100, properties.getMaxConnections());
        assertEquals(10, properties.getMaxConnectionsPerRoute());
        assertEquals("medical_rag_", properties.getIndexPrefix());
    }

    @Test
    void testSetters() {
        ElasticsearchProperties properties = new ElasticsearchProperties();

        properties.setUris(new String[]{"http://es1:9200", "http://es2:9200"});
        properties.setConnectionTimeout(10000);
        properties.setSocketTimeout(60000);
        properties.setMaxConnections(200);
        properties.setMaxConnectionsPerRoute(20);
        properties.setIndexPrefix("custom_");
        properties.setUsername("elastic");
        properties.setPassword("password");

        assertEquals(2, properties.getUris().length);
        assertEquals(10000, properties.getConnectionTimeout());
        assertEquals(60000, properties.getSocketTimeout());
        assertEquals(200, properties.getMaxConnections());
        assertEquals(20, properties.getMaxConnectionsPerRoute());
        assertEquals("custom_", properties.getIndexPrefix());
        assertEquals("elastic", properties.getUsername());
        assertEquals("password", properties.getPassword());
    }

    @Test
    void testMultipleUris() {
        ElasticsearchProperties properties = new ElasticsearchProperties();

        String[] uris = new String[]{
                "http://node1:9200",
                "http://node2:9200",
                "http://node3:9200"
        };

        properties.setUris(uris);

        assertEquals(3, properties.getUris().length);
        assertEquals("http://node1:9200", properties.getUris()[0]);
        assertEquals("http://node2:9200", properties.getUris()[1]);
        assertEquals("http://node3:9200", properties.getUris()[2]);
    }
}
