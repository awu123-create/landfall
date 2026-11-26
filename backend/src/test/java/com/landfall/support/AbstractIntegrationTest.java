package com.landfall.support;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 真实 PostgreSQL 16 + Redis 7 集成测试基类：按测试 JVM 动态建删空库，Redis 固定隔离在 DB 15，其余连接参数沿用 application.yml 与 .env。
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    protected static final int REDIS_TEST_DATABASE = 15;
    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
    private static final Properties DOT_ENV = loadDotEnv();
    private static final String POSTGRES_HOST = setting("POSTGRES_HOST", "localhost");
    private static final String POSTGRES_PORT = setting("POSTGRES_PORT", "5432");
    private static final String POSTGRES_USER = setting("POSTGRES_USER", "landfall");
    private static final String POSTGRES_PASSWORD = setting("POSTGRES_PASSWORD", "landfall123");

    private static volatile String tempDatabaseName;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        String dbName = ensureTempDatabaseCreated();
        String jdbcUrl = "jdbc:postgresql://" + POSTGRES_HOST + ":" + POSTGRES_PORT + "/" + dbName;
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.data.redis.database", () -> REDIS_TEST_DATABASE);
    }

    @BeforeEach
    void clearRedisTestDatabase() {
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    private static synchronized String ensureTempDatabaseCreated() {
        if (tempDatabaseName != null) {
            return tempDatabaseName;
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String dbName = "landfall_it_" + suffix;
        executeMaintenanceSql("CREATE DATABASE " + dbName);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> dropTempDatabase(dbName), "landfall-it-db-cleanup"));
        tempDatabaseName = dbName;
        return dbName;
    }

    private static void executeMaintenanceSql(String sql) {
        String adminUrl = "jdbc:postgresql://" + POSTGRES_HOST + ":" + POSTGRES_PORT + "/postgres";
        try (Connection connection = DriverManager.getConnection(adminUrl, POSTGRES_USER, POSTGRES_PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to execute PostgreSQL maintenance SQL: " + sql, ex);
        }
    }

    private static void dropTempDatabase(String dbName) {
        try {
            executeMaintenanceSql("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
        } catch (RuntimeException ex) {
            log.warn("Failed to drop temporary integration test database {}", dbName, ex);
        }
    }

    private static Properties loadDotEnv() {
        Properties props = new Properties();
        for (Path candidate : List.of(Path.of("../.env"), Path.of(".env"))) {
            if (Files.isRegularFile(candidate)) {
                try (Reader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                    props.load(reader);
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to read " + candidate, ex);
                }
                break;
            }
        }
        return props;
    }

    private static String setting(String name, String fallback) {
        String env = System.getenv(name);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String fileVal = DOT_ENV.getProperty(name);
        return (fileVal == null || fileVal.isBlank()) ? fallback : fileVal.trim();
    }
}
