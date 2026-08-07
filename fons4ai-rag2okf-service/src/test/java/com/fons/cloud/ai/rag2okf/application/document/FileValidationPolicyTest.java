package com.fons.cloud.ai.rag2okf.application.document;

import com.fons.cloud.ai.rag2okf.common.dto.FileValidationPolicy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件上传前安全校验的契约测试。
 *
 * @author hongqy
 */
class FileValidationPolicyTest {

    private final FileValidationPolicy policy = new FileValidationPolicy();

    @Test
    void shouldAcceptPlainTextAndCalculateSha256WhileReading() throws Exception {
        FileValidationPolicy.ValidatedFile file = policy.validate(
                "notes.txt",
                "text/plain",
                new ByteArrayInputStream("knowledge".getBytes(StandardCharsets.UTF_8)),
                9L
        );

        assertThat(file.safeFilename()).isEqualTo("notes.txt");
        assertThat(file.contentType()).isEqualTo("text/plain");
        assertThat(file.inputStream().readAllBytes()).isEqualTo("knowledge".getBytes(StandardCharsets.UTF_8));
        assertThat(file.size()).isEqualTo(9L);
        assertThat(file.sha256()).isEqualTo("e0f895872d65b2528feec97350a3a212b3d4ab88748e25d022a34641d338216b");
    }

    @Test
    void shouldRejectPathTraversalFilenameBeforeItCanReachObjectMetadata() {
        assertThatThrownBy(() -> policy.validate(
                "../secrets.txt",
                "text/plain",
                new ByteArrayInputStream("safe".getBytes(StandardCharsets.UTF_8)),
                4L
        )).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldRejectEmptyFile() {
        assertThatThrownBy(() -> policy.validate(
                "empty.txt",
                "text/plain",
                new ByteArrayInputStream(new byte[0]),
                0L
        )).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldRejectAnUnsupportedExtension() {
        assertThatThrownBy(() -> policy.validate(
                "payload.exe",
                "application/octet-stream",
                new ByteArrayInputStream(new byte[]{0x4d, 0x5a}),
                2L
        )).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldRejectAFileWhosePdfSignatureDoesNotMatchItsExtension() {
        assertThatThrownBy(() -> policy.validate(
                "fake.pdf",
                "application/pdf",
                new ByteArrayInputStream("not a pdf".getBytes(StandardCharsets.UTF_8)),
                9L
        )).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldRejectADeclaredSizeAboveTheConfiguredLimit() {
        assertThatThrownBy(() -> policy.validate(
                "large.md",
                "text/markdown",
                new ByteArrayInputStream("# knowledge".getBytes(StandardCharsets.UTF_8)),
                FileValidationPolicy.MAX_FILE_SIZE_BYTES + 1
        )).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldRejectAStreamThatExceedsTheLimitEvenWhenTheDeclaredSizeIsUnknown() throws Exception {
        FileValidationPolicy smallLimitPolicy = new FileValidationPolicy(4L);
        FileValidationPolicy.ValidatedFile file = smallLimitPolicy.validate(
                "large.txt",
                "text/plain",
                new ByteArrayInputStream("12345".getBytes(StandardCharsets.UTF_8)),
                -1L
        );

        assertThatThrownBy(() -> file.inputStream().readAllBytes())
                .isInstanceOf(RuntimeException.class);
    }
}
