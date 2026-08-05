package com.fons.cloud.ai.rag2okf.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbUserMapper;
import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目级初始化 SQL 与 MyBatis-Plus 基础读写的隔离 MySQL 集成测试。
 *
 * <p>测试只连接临时容器，不读取本机或共享环境数据库。没有 Docker 的开发机
 * 会明确跳过本测试，跳过结果不能作为 T003/T004 的迁移完成证据。</p>
 *
 * @author hongqy
 */
@Testcontainers(disabledWithoutDocker = true)
class KnowledgePersistenceMySqlIT {

    private static final Path INITIAL_SCHEMA = Path.of("sql", "init-schema.sql");

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "kb_user",
            "kb_workspace",
            "kb_workspace_member",
            "kb_knowledge_base",
            "kb_source_document",
            "kb_document_version",
            "kb_parse_revision",
            "kb_chunk_revision",
            "kb_publication_revision",
            "kb_processing_task",
            "kb_outbox_event"
    );

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0.42")
                    .withDatabaseName("rag2okf_test")
                    .withUsername("rag2okf")
                    .withPassword("rag2okf_test_only");

    private static DataSource dataSource;

    @BeforeAll
    static void initializeSchema() throws Exception {
        MysqlDataSource mysqlDataSource = new MysqlDataSource();
        mysqlDataSource.setUrl(MYSQL.getJdbcUrl());
        mysqlDataSource.setUser(MYSQL.getUsername());
        mysqlDataSource.setPassword(MYSQL.getPassword());
        dataSource = mysqlDataSource;

        EncodedResource schema = new EncodedResource(
                new FileSystemResource(INITIAL_SCHEMA),
                StandardCharsets.UTF_8
        );
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, schema);
        }
    }

    @Test
    void initialSchemaShouldCreateExpectedTablesAndConstraints() throws Exception {
        assertThat(loadKnowledgeTables()).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
        assertThat(indexExists("kb_user", "uk_kb_user_email")).isTrue();
        assertThat(indexExists(
                "kb_processing_task",
                "uk_kb_processing_task_idempotency"
        )).isTrue();
        assertThat(columnExists("kb_user", "password_hash")).isTrue();
        assertThat(columnExists("kb_user", "password_changed_at")).isTrue();
        assertThat(columnExists("kb_user", "email_verified")).isFalse();
        assertThat(columnExists("kb_user", "verified_at")).isFalse();
        assertThat(columnDefaultEquals(
                "kb_source_document",
                "parse_status",
                "NOT_STARTED"
        )).isTrue();
    }

    @Test
    void userMapperShouldInsertAndReadLocalAccount() {
        SqlSessionFactory factory = createSqlSessionFactory();
        try (SqlSession session = factory.openSession(true)) {
            KbUserMapper mapper = session.getMapper(KbUserMapper.class);
            KbUserEntity user = new KbUserEntity();
            user.setUserKey("01J00000000000000000000000");
            user.setEmail("owner@example.test");
            user.setPasswordHash("{bcrypt}$2a$10$integration-test-hash");
            user.setDisplayName("Integration Owner");
            user.setStatus(UserStatus.ACTIVE);
            user.setPasswordChangedAt(new Date());

            assertThat(mapper.insert(user)).isEqualTo(1);
            assertThat(user.getId()).isNotNull();

            KbUserEntity saved = mapper.selectById(user.getId());
            assertThat(saved.getEmail()).isEqualTo("owner@example.test");
            assertThat(saved.getPasswordHash()).startsWith("{bcrypt}");
            assertThat(saved.getVersion()).isZero();
            assertThat(saved.getDeleted()).isFalse();
        }
    }

    private SqlSessionFactory createSqlSessionFactory() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(KbUserMapper.class);
        configuration.setEnvironment(new Environment(
                "rag2okf-test",
                new JdbcTransactionFactory(),
                dataSource
        ));
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private Set<String> loadKnowledgeTables() throws Exception {
        String sql = """
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = ?
                   AND table_name LIKE 'kb_%'
                """;
        Set<String> tables = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MYSQL.getDatabaseName());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString(1));
                }
            }
        }
        return tables;
    }

    private boolean indexExists(String table, String index) throws Exception {
        String sql = """
                SELECT COUNT(*)
                  FROM information_schema.statistics
                 WHERE table_schema = ?
                   AND table_name = ?
                   AND index_name = ?
                """;
        return count(sql, table, index) > 0;
    }

    private boolean columnExists(String table, String column) throws Exception {
        String sql = """
                SELECT COUNT(*)
                  FROM information_schema.columns
                 WHERE table_schema = ?
                   AND table_name = ?
                   AND column_name = ?
                """;
        return count(sql, table, column) > 0;
    }

    private boolean columnDefaultEquals(
            String table,
            String column,
            String expectedDefault
    ) throws Exception {
        String sql = """
                SELECT column_default
                  FROM information_schema.columns
                 WHERE table_schema = ?
                   AND table_name = ?
                   AND column_name = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MYSQL.getDatabaseName());
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        && expectedDefault.equals(resultSet.getString(1));
            }
        }
    }

    private long count(String sql, String table, String objectName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MYSQL.getDatabaseName());
            statement.setString(2, table);
            statement.setString(3, objectName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
