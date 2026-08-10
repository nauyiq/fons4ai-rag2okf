package com.fons.cloud.ai.rag2okf.model;

import com.fons.cloud.ai.rag2okf.common.constants.ModelType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CR-001 可选 CHAT -> LLM 脚本与应用层别名兼容回归。
 *
 * <p>脚本不会由测试执行；测试只验证可审核性和精确回滚约束，生产/预发 DML 仍由 DBA 手工门禁控制。</p>
 *
 * @author hongqy
 */
class ChatToLlmOptionalScriptTest {

    private static final Path SCRIPT = Path.of("..", "spec", "features", "20260807", "ddl-changes",
            "CR-001-rag2okf-kb_model_profile-chat-to-llm.sql");

    @Test
    @DisplayName("不执行物理转换时应用层仍把 CHAT 读取为 LLM，并拒绝新写 CHAT")
    void shouldKeepApplicationAliasCompatibilityWithoutExecutingSql() {
        assertThat(ModelType.normalize("CHAT")).isEqualTo("LLM");
        assertThat(ModelType.normalize("LLM")).isEqualTo("LLM");
        assertThat(ModelType.isValid("LLM")).isTrue();
        assertThat(ModelType.isValid("CHAT")).isFalse();
        assertThat(ModelType.whitelist()).containsExactly(
                "LLM", "EMBEDDING", "RERANK", "TTS", "ASR", "VLM", "OCR");
    }

    @Test
    @DisplayName("可选脚本只转换档案类型并明确禁止把 binding 用途写成 LLM")
    void optionalScriptShouldBeAuditableAndPreciselyReversible() throws IOException {
        String sql = Files.readString(SCRIPT);

        assertThat(sql)
                .contains("不执行也不阻塞上线", "执行前置", "数据库快照", "每批 100 行", "执行时间预估")
                .contains("tmp_cr001_chat_profile_ids")
                .contains("WHERE model_type = 'CHAT'", "SET profile.model_type = 'LLM'")
                .contains("@profile_chat_before", "@profile_converted")
                .contains("profile_chat_remaining")
                .contains("领域值是 ANSWER_GENERATION/EMBEDDING", "当前脚本不得修改该列")
                .contains("-- COMMIT;", "ROLLBACK;", "默认按只读演练处理并回滚")
                .contains("SET profile.model_type = 'CHAT'")
                .doesNotContain("SET binding.usage_type = 'LLM'", "SET binding.usage_type = 'CHAT'",
                        "UPDATE kb_model_profile\nSET model_type = 'CHAT' WHERE model_type = 'LLM'");
    }
}
