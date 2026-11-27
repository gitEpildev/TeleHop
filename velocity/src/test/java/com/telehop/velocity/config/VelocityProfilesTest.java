package com.telehop.velocity.config;

import com.telehop.common.config.ConfigYaml;
import com.telehop.common.config.DeploymentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VelocityProfilesTest {
    @TempDir Path dir;
    private static final List<String> SERVERS = List.of("arrival", "mines");

    @Test void migratesRedisTlsCredentialsInfrastructureAndLegacyUnknownFields() throws Exception {
        String original = "multi-proxy.enabled=true\nproxy.id=proxy-r\nmysql.password=private !:value\n"
                + "redis.password=a:b\nredis.ssl=true\nredis.ssl-verify=false\nredis.ssl-ca-cert=/certs/anchor.pem\n"
                + "redis.channel-prefix=old-prefix\nmysql.poolSize=9\nservers.hub=mines\nservers.backends=arrival,mines\n"
                + "messaging.requestTimeoutMs=9876\nother.custom=retained\n";
        Files.writeString(dir.resolve("config.properties"), original);
        var result = VelocityProfiles.load(dir, SERVERS);
        assertEquals(DeploymentConfig.Mode.MULTI, DeploymentConfig.read(dir).mode());
        assertTrue(result.redisConfig().ssl());
        assertFalse(result.redisConfig().sslVerify());
        assertEquals("/certs/anchor.pem", result.redisConfig().sslCaCert());
        assertEquals("a:b", result.redisConfig().password());
        assertEquals("old-prefix", result.redisConfig().channelPrefix());
        assertEquals("proxy-r", result.redisConfig().proxyId());
        assertEquals(9, result.databaseConfig().poolSize());
        assertEquals("private !:value", result.databaseConfig().password());
        assertEquals(9876, result.requestTimeoutMs());
        assertEquals("mines", result.hubServer());
        assertFalse(Files.exists(dir.resolve("config.properties")));
        try (var backups = Files.list(dir.resolve("migrations/backups"))) {
            var archived = backups.findFirst().orElseThrow().resolve("original/config.properties");
            assertEquals(original, Files.readString(archived));
        }
        assertEquals(result, VelocityProfiles.load(dir, SERVERS));
    }

    @Test void freshSingleNeedsNoRedisAndUsesRegisteredBackends() throws Exception {
        var settings = VelocityProfiles.load(dir, SERVERS);
        assertFalse(settings.multiProxyEnabled());
        assertEquals("arrival", settings.hubServer());
        assertEquals(SERVERS, settings.backends());
        assertEquals(DeploymentConfig.Mode.SINGLE, DeploymentConfig.read(dir).mode());
        for (String profile : List.of("single", "multi", "custom")) {
            var expected = profile.equals("single") ? java.util.Set.of("servers.yml", "messaging.yml")
                    : java.util.Set.of("servers.yml", "messaging.yml", "proxy.yml");
            try (var contents = Files.list(dir.resolve("config").resolve(profile))) {
                assertEquals(expected, contents.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
            }
        }
        assertFalse(Files.exists(dir.resolve("config.properties")));
    }

    @Test void inactiveProfileAndSharedCredentialsSurviveAProfileSwitch() throws Exception {
        var single = VelocityProfiles.load(dir, SERVERS);
        Path inactive = dir.resolve("config/single/messaging.yml");
        Files.writeString(inactive, "comment: [deliberately unfinished");
        Files.writeString(dir.resolve("config.yml"), DeploymentConfig.initial(DeploymentConfig.Mode.CUSTOM, false));
        var custom = VelocityProfiles.load(dir, SERVERS);
        assertEquals(single.databaseConfig(), custom.databaseConfig());
        assertFalse(custom.multiProxyEnabled());
        assertEquals("comment: [deliberately unfinished", Files.readString(inactive));
        assertTrue(Files.exists(dir.resolve("config/custom/servers.yml")));
    }

    @Test void abortsBeforeCreatingActiveFilesForInvalidMappingsOrTlsValues() throws Exception {
        Path source = dir.resolve("config.properties");
        Files.writeString(source, "redis.ssl=misspelt\nservers.hub=no-such-server\n");
        assertThrows(IllegalArgumentException.class, () -> VelocityProfiles.load(dir, SERVERS));
        assertFalse(Files.exists(dir.resolve("config.yml")));
        Files.writeString(source, "servers.hub=no-such-server\nservers.backends=no-such-server");
        assertThrows(IllegalArgumentException.class, () -> VelocityProfiles.load(dir, SERVERS));
        assertFalse(Files.exists(dir.resolve("migrations/state.yml")));
    }

    @Test void malformedSharedYamlCannotChangeAuthenticationBehaviour() throws Exception {
        VelocityProfiles.load(dir, SERVERS);
        Path file = dir.resolve("config/redis.yml");
        Files.writeString(file, "redis: {ssl: 'true'}\n");
        assertThrows(IllegalArgumentException.class, () -> VelocityProfiles.load(dir, SERVERS));
        assertEquals("true", ConfigYaml.get(ConfigYaml.read(file), "redis.ssl", null));
    }
}
