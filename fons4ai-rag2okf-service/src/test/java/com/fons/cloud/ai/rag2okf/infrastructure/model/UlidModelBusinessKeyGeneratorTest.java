package com.fons.cloud.ai.rag2okf.infrastructure.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型业务主键格式测试。
 *
 * @author hongqy
 */
class UlidModelBusinessKeyGeneratorTest {

    @Test
    void shouldGenerateUniqueTwentySixCharacterBusinessKeys() {
        UlidModelBusinessKeyGenerator generator = new UlidModelBusinessKeyGenerator();

        String first = generator.nextKey();
        String second = generator.nextKey();

        assertThat(first).hasSize(26).matches("[0-9ABCDEFGHJKMNPQRSTVWXYZ]{26}");
        assertThat(second).hasSize(26).isNotEqualTo(first);
    }
}
