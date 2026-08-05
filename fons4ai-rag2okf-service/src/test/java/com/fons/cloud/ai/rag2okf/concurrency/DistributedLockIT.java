package com.fons.cloud.ai.rag2okf.concurrency;

import com.fons.cloud.ai.rag2okf.infrastructure.task.DistributedLockedTaskExecutor;
import com.fons.cloud.ai.rag2okf.infrastructure.task.OutboxDispatchExecutor;
import com.fons.cloud.lock.annotation.DistributeLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分布式锁门禁与 MySQL 行锁残留静态契约测试（T024）。
 *
 * <p>验证技术设计 §5.7、§5.8、D-005 和 CR-002 的锁约束：
 * <ul>
 *   <li>所有写命令、任务和 Outbox 都在统一的 Redisson 锁入口下互斥（AC-017、AC-023、AC-024）</li>
 *   <li>MySQL 不再承担抢锁职责，不得出现 FOR UPDATE / SKIP LOCKED / 行锁抢任务</li>
 *   <li>锁入口必须经 Spring AOP 代理（public 方法 + @DistributeLock 注解）</li>
 *   <li>锁 key 不含完整邮箱、文件名或 token</li>
 *   <li>waitTime=0（非阻塞），expireTime 由 watchdog 续期</li>
 * </ul>
 *
 * <p>该测试通过反射分析注解 + 静态扫描 SQL/Java 源码验证契约，不依赖真实 Redis/MySQL 容器。
 * 多实例并发、watchdog 续期、Redis 中断和 JVM 中止恢复由隔离环境的集成测试负责。
 *
 * @author hongqy
 */
@Execution(ExecutionMode.CONCURRENT)
class DistributedLockIT {

    private static final Path INIT_SCHEMA = Path.of("sql", "init-schema.sql");

    private static final List<String> FORBIDDEN_LOCK_SYNTAX = List.of(
            "FOR UPDATE", "SKIP LOCKED", "LOCK IN SHARE MODE");

    /**
     * AC-017 / AC-024：DistributedLockedTaskExecutor.executeLocked 必须使用 @DistributeLock 注解，
     * waitTime=0（非阻塞），锁 key 为 taskKey（不含邮箱/文件名/token）。
     */
    @Test
    void executeLockedShouldBeAnnotatedWithDistributeLockAndWaitTimeZero() throws Exception {
        Method method = DistributedLockedTaskExecutor.class.getMethod("executeLocked", String.class);
        DistributeLock annotation = method.getAnnotation(DistributeLock.class);

        assertThat(annotation).as("executeLocked 必须标注 @DistributeLock（AC-017）").isNotNull();
        assertThat(annotation.waitTime())
                .as("waitTime 必须为 0（非阻塞，未获锁即跳过）")
                .isZero();
        assertThat(annotation.keyExpression())
                .as("锁 key 表达式应引用 taskKey")
                .contains("taskKey");
    }

    /**
     * AC-017：DistributedLockedTaskExecutor.recoverStale 必须使用 @DistributeLock 注解，
     * 用于 stale 任务恢复的互斥。
     */
    @Test
    void recoverStaleShouldBeAnnotatedWithDistributeLock() throws Exception {
        Method method = DistributedLockedTaskExecutor.class.getMethod("recoverStale", String.class);
        DistributeLock annotation = method.getAnnotation(DistributeLock.class);

        assertThat(annotation).as("recoverStale 必须标注 @DistributeLock（AC-017）").isNotNull();
        assertThat(annotation.waitTime()).isZero();
    }

    /**
     * AC-024：OutboxDispatchExecutor.dispatchLocked 必须使用 @DistributeLock 注解，
     * 锁 key 为 eventKey，waitTime=0。
     */
    @Test
    void outboxDispatchShouldBeAnnotatedWithDistributeLock() throws Exception {
        Method method = OutboxDispatchExecutor.class.getMethod("dispatchLocked", String.class);
        DistributeLock annotation = method.getAnnotation(DistributeLock.class);

        assertThat(annotation).as("dispatchLocked 必须标注 @DistributeLock（AC-024）").isNotNull();
        assertThat(annotation.waitTime())
                .as("Outbox dispatch waitTime 必须为 0")
                .isZero();
        assertThat(annotation.keyExpression())
                .as("锁 key 表达式应引用 eventKey")
                .contains("eventKey");
    }

    /**
     * AC-017：锁入口必须为 public 方法（Spring AOP 代理要求），
     * 非 public 方法无法被 @DistributeLock 切面拦截。
     */
    @Test
    void lockEntryPointsMustBePublicForSpringAopProxy() {
        assertMethodPublic(DistributedLockedTaskExecutor.class, "executeLocked", String.class);
        assertMethodPublic(DistributedLockedTaskExecutor.class, "recoverStale", String.class);
        assertMethodPublic(OutboxDispatchExecutor.class, "dispatchLocked", String.class);
    }

    /**
     * D-005 / CR-002：锁 scene 必须使用 rag2okf 前缀，避免与其他业务冲突。
     */
    @Test
    void lockScenesShouldUseRag2okfPrefix() throws Exception {
        String executeScene = getLockScene(DistributedLockedTaskExecutor.class, "executeLocked", String.class);
        String recoverScene = getLockScene(DistributedLockedTaskExecutor.class, "recoverStale", String.class);
        String outboxScene = getLockScene(OutboxDispatchExecutor.class, "dispatchLocked", String.class);

        assertThat(executeScene).startsWith("rag2okf:");
        assertThat(recoverScene).startsWith("rag2okf:");
        assertThat(outboxScene).startsWith("rag2okf:");
    }

    /**
     * AC-023 / CR-002：init-schema.sql 不得包含任何 MySQL 行锁语法（FOR UPDATE / SKIP LOCKED / LOCK IN SHARE MODE）。
     * MySQL 仅保留状态、幂等、heartbeat/deadline 和 CAS（乐观锁 version 字段）。
     */
    @Test
    void initSchemaShouldNotContainMysqlPessimisticLockSyntax() throws IOException {
        assertThat(INIT_SCHEMA)
                .as("初始化 SQL 必须存在于 %s", INIT_SCHEMA)
                .exists();

        String sql = Files.readString(INIT_SCHEMA, StandardCharsets.UTF_8);
        for (String forbidden : FORBIDDEN_LOCK_SYNTAX) {
            assertThat(sql)
                    .as("init-schema.sql 不得包含 MySQL 行锁语法: %s（CR-002）", forbidden)
                    .doesNotContainIgnoringCase(forbidden);
        }
    }

    /**
     * D-005：kb_processing_task 表不得包含 lease / locked_by / lock_owner / lock_until 等抢锁字段，
     * execution_owner 和 execution_deadline 仅用于 stale 检测与恢复，不表示数据库锁。
     */
    @Test
    void processingTaskTableShouldNotContainLeaseLockFields() throws IOException {
        String sql = Files.readString(INIT_SCHEMA, StandardCharsets.UTF_8);
        String taskTableDefinition = extractTableDefinition(sql, "kb_processing_task");

        assertThat(taskTableDefinition)
                .as("kb_processing_task 表定义必须存在")
                .isNotNull();

        List<String> forbiddenFields = List.of(
                "lease_id", "locked_by", "lock_owner", "lock_until", "lock_token");

        for (String field : forbiddenFields) {
            assertThat(taskTableDefinition)
                    .as("kb_processing_task 不得包含抢锁字段: %s（D-005）", field)
                    .doesNotContainIgnoringCase(field);
        }

        // execution_owner 和 execution_deadline 允许存在，但注释必须声明"不表示数据库锁"
        assertThat(taskTableDefinition)
                .as("execution_owner 字段注释必须声明不表示数据库锁")
                .contains("不表示数据库锁");
    }

    /**
     * AC-023：Java 源码中不得出现 last("FOR UPDATE") 或 last("SKIP LOCKED") 等 MyBatis-Plus 行锁拼接。
     * 该测试扫描 src/main/java 下的 .java 文件，确保无行锁拼接代码。
     */
    @Test
    void javaSourceShouldNotContainPessimisticLockConcatenation() throws IOException {
        Path mainJava = Path.of("src", "main", "java");
        assertThat(mainJava).exists();

        List<String> forbiddenPatterns = List.of(
                "last(\"FOR UPDATE\")", "last(\"SKIP LOCKED\")",
                "last(\"LOCK IN SHARE MODE\")", "FOR UPDATE NOWAIT");

        scanJavaFiles(mainJava, forbiddenPatterns);
    }

    private void assertMethodPublic(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        Method method;
        try {
            method = clazz.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(methodName + " 方法不存在", e);
        }
        assertThat(java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .as("%s.%s 必须为 public 方法（Spring AOP 代理要求）", clazz.getSimpleName(), methodName)
                .isTrue();
    }

    private String getLockScene(Class<?> clazz, String methodName, Class<?>... paramTypes) throws Exception {
        Method method = clazz.getMethod(methodName, paramTypes);
        DistributeLock annotation = method.getAnnotation(DistributeLock.class);
        assertThat(annotation).isNotNull();
        return annotation.scene();
    }

    private String extractTableDefinition(String sql, String tableName) {
        String marker = "CREATE TABLE " + tableName;
        int start = sql.indexOf(marker);
        if (start < 0) return null;

        int end = sql.indexOf("ENGINE=", start);
        if (end < 0) {
            end = sql.indexOf(";", start);
        }
        return end > start ? sql.substring(start, end) : sql.substring(start);
    }

    private void scanJavaFiles(Path root, List<String> forbiddenPatterns) throws IOException {
        try (var stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file, StandardCharsets.UTF_8);
                            for (String pattern : forbiddenPatterns) {
                                assertThat(content)
                                        .as("%s 不得包含行锁拼接: %s（AC-023）",
                                                root.relativize(file), pattern)
                                        .doesNotContain(pattern);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("读取文件失败: " + file, e);
                        }
                    });
        }
    }
}
