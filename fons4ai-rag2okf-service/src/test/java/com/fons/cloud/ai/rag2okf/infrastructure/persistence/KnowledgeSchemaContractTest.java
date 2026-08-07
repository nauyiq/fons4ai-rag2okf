package com.fons.cloud.ai.rag2okf.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识库文档生命周期初始化 SQL 的静态结构契约。
 *
 * <p>该测试不替代真实 MySQL 迁移测试，只负责在没有容器的构建环境中尽早发现
 * 表遗漏、认证禁用字段或数据库抢锁语义回归。</p>
 *
 * @author hongqy
 */
class KnowledgeSchemaContractTest {

    private static final Path INITIAL_SCHEMA = Path.of("sql", "init-schema.sql");

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(kb_[a-z0-9_]+)"
    );

    private static final Pattern MODEL_BINDING_DEFINITION = Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+kb_model_binding\\s*\\((.*?)\\)\\s*ENGINE="
    );

    private static final List<String> REQUIRED_TABLES = List.of(
            "kb_user",
            "kb_workspace",
            "kb_workspace_member",
            "kb_knowledge_base",
            "kb_source_document",
            "kb_parse_revision",
            "kb_chunk_revision",
            "kb_publication_revision",
            "kb_processing_task",
            "kb_outbox_event",
            "kb_model_connection",
            "kb_model_profile",
            "kb_model_binding"
        );

    @Test
    void initialSchemaShouldContainExactlyThirteenTablesAndSecurityBoundaries() throws IOException {
        String sql = readInitialSchema();

        assertThat(REQUIRED_TABLES)
                .allSatisfy(table -> assertThat(sql)
                        .containsIgnoringCase("CREATE TABLE " + table));
        Matcher matcher = CREATE_TABLE.matcher(sql);
        List<String> actualTables = matcher.results()
                .map(result -> result.group(1).toLowerCase())
                .toList();
        assertThat(actualTables)
                .containsExactlyInAnyOrderElementsOf(REQUIRED_TABLES)
                .doesNotHaveDuplicates();
        assertThat(sql).containsIgnoringCase("UNIQUE KEY uk_kb_user_email");
        assertThat(sql).containsIgnoringCase("password_hash");
        assertThat(sql).containsIgnoringCase("password_changed_at");
        assertThat(sql).containsIgnoringCase(
                "parse_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED'"
        );
        assertThat(sql).containsIgnoringCase(
                "api_key_ciphertext VARBINARY(2048) NOT NULL"
        );
        assertThat(sql).containsIgnoringCase(
                "api_key_nonce VARBINARY(32) NOT NULL"
        );
        assertThat(sql).containsIgnoringCase(
                "UNIQUE KEY uk_kb_model_connection_owner_display"
        );
        assertThat(sql).containsIgnoringCase(
                "UNIQUE KEY uk_kb_model_profile_owner_connection_type_name"
        );
        assertThat(sql).containsIgnoringCase(
                "UNIQUE KEY uk_kb_model_binding_knowledge_usage"
        );
        assertThat(sql).containsIgnoringCase(
                "KEY idx_kb_model_binding_profile_status"
        );
        assertThat(sql).containsIgnoringCase(
                "parameters_json JSON NOT NULL DEFAULT (JSON_OBJECT())"
        );
        assertThat(sql).containsIgnoringCase(
                "config_json JSON NOT NULL DEFAULT (JSON_OBJECT())"
        );
        assertThat(modelBindingDefinition(sql)).doesNotContainIgnoringCase(
                "owner_user_id",
                "api_key"
        );
        assertThat(sql).doesNotContainIgnoringCase(
                "chat_model_profile_id",
                "embedding_model_profile_id",
                "api_key VARCHAR",
                "api_key TEXT"
        );
        assertThat(sql).doesNotContainIgnoringCase(
                "email_verified",
                "verified_at",
                "flyway_schema_history",
                "FOR UPDATE",
                "SKIP LOCKED"
        );
    }

    private String readInitialSchema() throws IOException {
        assertThat(INITIAL_SCHEMA)
                .as("项目级初始化 SQL 必须存在于 %s", INITIAL_SCHEMA)
                .exists();
        return Files.readString(INITIAL_SCHEMA, StandardCharsets.UTF_8);
    }

    private String modelBindingDefinition(String sql) {
        Matcher matcher = MODEL_BINDING_DEFINITION.matcher(sql);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
