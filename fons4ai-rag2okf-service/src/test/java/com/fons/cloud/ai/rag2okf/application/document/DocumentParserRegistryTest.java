package com.fons.cloud.ai.rag2okf.application.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserAvailability;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseIntent;
import com.fons.cloud.ai.rag2okf.infrastructure.adapter.document.MinerUDocumentParserAdapter;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.DocumentParser;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.DocumentParserRegistry;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.ParserRouter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 解析器注册表禁止 MINERU 回退到 Built-in。 */
class DocumentParserRegistryTest {

    @Test
    void shouldReturnBuiltInAndRejectDisabledMinerU() {
        DocumentParser builtIn = mock(DocumentParser.class);
        when(builtIn.type()).thenReturn(ParserType.BUILT_IN);
        when(builtIn.availability()).thenReturn(ParserAvailability.ENABLED);
        DocumentParserRegistry registry = new DocumentParserRegistry(
                builtIn, new MinerUDocumentParserAdapter());

        assertSame(builtIn, registry.requireEnabled(ParserType.BUILT_IN));
        assertThrows(DocumentProcessingException.class,
                () -> registry.requireEnabled(ParserType.MINERU));
    }

    @Test
    void shouldRouteOnlyIntentSelectedParserWithoutFallback() {
        DocumentParser builtIn = mock(DocumentParser.class);
        when(builtIn.type()).thenReturn(ParserType.BUILT_IN);
        when(builtIn.availability()).thenReturn(ParserAvailability.ENABLED);
        ParserRouter router = new ParserRouter(new DocumentParserRegistry(
                builtIn, new MinerUDocumentParserAdapter()));

        assertSame(builtIn, router.require(intent(ParserType.BUILT_IN)));
        assertThrows(DocumentProcessingException.class,
                () -> router.require(intent(ParserType.MINERU)));
    }

    private ParseIntent intent(ParserType parserType) {
        return new ParseIntent("PDF", parserType, List.of(), List.of(), List.of("TEST"));
    }
}
