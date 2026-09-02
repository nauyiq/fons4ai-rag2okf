package com.fons.cloud.ai.rag2okf.common.request.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkBoundaryType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ChunkHierarchyType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 手动解析入口声明式校验契约测试。 */
class DocumentParseRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldCascadeValidateChunkPolicyWhitelist() {
        DocumentParseRequest invalid = new DocumentParseRequest(
                null, new ChunkPolicyRequest(null, null, Map.of()), false);
        DocumentParseRequest valid = new DocumentParseRequest(
                null, new ChunkPolicyRequest(
                        ChunkBoundaryType.RECURSIVE, ChunkHierarchyType.PARENT_CHILD, Map.of()), false);

        assertFalse(validator.validate(invalid).isEmpty());
        assertTrue(validator.validate(valid).isEmpty());
    }
}
