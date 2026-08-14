package com.fons.cloud.ai.rag2okf.common.constants;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Rag2OkfResultCode} 编码格式和唯一性测试。
 */
class Rag2OkfResultCodeTest {

    @Test
    void shouldUseUniqueRfCodes() {
        List<String> codes = Arrays.stream(Rag2OkfResultCode.values())
                .map(Rag2OkfResultCode::getCode)
                .toList();

        assertTrue(codes.stream().allMatch(code -> code.matches("RF[0-9][0-9]{5}")));
        assertEquals(codes.size(), new HashSet<>(codes).size());
    }
}
