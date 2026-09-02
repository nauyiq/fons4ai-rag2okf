package com.fons.cloud.ai.rag2okf.infrastructure.adapter.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserAvailability;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** MinerU 本期禁用且无远程依赖的契约测试。 */
class MinerUDocumentParserAdapterTest {

    @Test
    void shouldAlwaysFailClosedWithoutFallback() {
        MinerUDocumentParserAdapter adapter = new MinerUDocumentParserAdapter();

        assertEquals(ParserType.MINERU, adapter.type());
        assertEquals(ParserAvailability.DISABLED, adapter.availability());
        assertThrows(DocumentProcessingException.class, () -> adapter.parse(null));
    }
}
