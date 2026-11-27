package com.telehop.common.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigTest {

    @Test
    void validConfigCreatesSuccessfully() {
        DatabaseConfig config = new DatabaseConfig("localhost", 3306, "telehop", "root", "pass", 5);
        assertEquals("localhost", config.host());
        assertEquals(3306, config.port());
        assertEquals("telehop", config.database());
        assertEquals(5, config.poolSize());
    }

    @Test
    void jdbcUrlContainsHostPortAndDatabase() {
        DatabaseConfig config = new DatabaseConfig("db.example.com", 3307, "mydb", "user", "pw", 3);
        String url = config.jdbcUrl();
        assertTrue(url.contains("db.example.com:3307/mydb"));
        assertTrue(url.contains("useUnicode=true"));
        assertTrue(url.contains("characterEncoding=UTF-8"));
    }

    @Test
    void blankHostThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("", 3306, "db", "u", "p", 1));
    }

    @Test
    void nullHostThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig(null, 3306, "db", "u", "p", 1));
    }

    @Test
    void invalidPortThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("localhost", 0, "db", "u", "p", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("localhost", 70000, "db", "u", "p", 1));
    }

    @Test
    void blankDatabaseThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("localhost", 3306, "", "u", "p", 1));
    }

    @Test
    void zeroPoolSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("localhost", 3306, "db", "u", "p", 0));
    }

    @Test
    void boundaryPortsAreValid() {
        assertDoesNotThrow(() -> new DatabaseConfig("h", 1, "d", "u", "p", 1));
        assertDoesNotThrow(() -> new DatabaseConfig("h", 65535, "d", "u", "p", 1));
    }

    @Test
    void configurationDiagnosticsOmitBothServicePasswords() {
        String secret = "private-value-in-test";
        var mysql = new DatabaseConfig("db.internal", 3306, "travel", "operator", secret, 2);
        var redis = new RedisConfig(true, "cache.internal", 6379, secret, "travel", "proxy-a", true, true, "");
        org.junit.jupiter.api.Assertions.assertFalse(mysql.toString().contains(secret));
        org.junit.jupiter.api.Assertions.assertFalse(redis.toString().contains(secret));
    }

}
