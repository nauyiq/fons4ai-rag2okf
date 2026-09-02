package com.fons.cloud.ai.rag2okf.application.document;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentPrecheckedFile;
import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.BuiltInFileCapabilityCatalog;
import com.fons.cloud.common.result.R;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 上传预检拒绝扩展名与魔数不一致的伪装文件。 */
class DocumentUploadPrecheckPolicyTest {

    @Test
    void shouldRejectPdfExtensionWithPngMagic() {
        DocumentUploadPrecheckPolicy policy = new DocumentUploadPrecheckPolicy(
                new BuiltInFileCapabilityCatalog(), 1024, 10, 10240);

        R<DocumentPrecheckedFile> result = policy.precheck(
                "disguised.pdf", new ByteArrayInputStream(new byte[]{
                        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}), 8);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(Rag2OkfResultCode.DOCUMENT_FILE_SECURITY_REJECTED.getCode());
    }
}
