package com.fons.cloud.ai.rag2okf.infrastructure.document.parser;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentPrecheckedFile;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseIntent;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** 解析意图必须建立在上传预检确认的文件事实之上。 */
class ParseRecognizerTest {

    private final ParseRecognizer recognizer = new ParseRecognizer(new BuiltInFileCapabilityCatalog());

    @Test
    void shouldRecognizeVerifiedFactsWithoutReadingSourceContent() {
        InputStream sourceStream = mock(InputStream.class);
        ParseIntent intent = recognizer.recognize(
                new DocumentPrecheckedFile("scan.png", "image/png", sourceStream));

        assertThat(intent.parserType()).isEqualTo(ParserType.BUILT_IN);
        assertThat(intent.requiredCapabilities()).containsExactly("OCR");
        assertThat(intent.reasons()).containsExactly("VERIFIED_EXTENSION:png", "VERIFIED_MIME:image/png");
        verifyNoInteractions(sourceStream);
    }

    @Test
    void shouldRejectExtensionAndVerifiedMimeConflict() {
        assertThatThrownBy(() -> recognizer.recognize(
                new DocumentPrecheckedFile("report.pdf", "text/plain", mock(InputStream.class))))
                .isInstanceOf(DocumentProcessingException.class)
                .extracting("code")
                .isEqualTo(Rag2OkfResultCode.DOCUMENT_UNSUPPORTED_FILE_TYPE.getCode());
    }

    @Test
    void shouldRejectUnknownTypeWithoutFallback() {
        assertThatThrownBy(() -> recognizer.recognize(
                new DocumentPrecheckedFile("report.exe", "application/octet-stream", mock(InputStream.class))))
                .isInstanceOf(DocumentProcessingException.class)
                .extracting("code")
                .isEqualTo(Rag2OkfResultCode.DOCUMENT_UNSUPPORTED_FILE_TYPE.getCode());
    }
}
