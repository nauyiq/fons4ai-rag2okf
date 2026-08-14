package com.fons.cloud.ai.rag2okf.infrastructure.support.user;

import com.fons.cloud.ai.rag2okf.common.exception.user.ModelConfigurationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ModelParameterCodec} 的 Fastjson2 编解码与参数边界测试。
 */
class ModelParameterCodecTest {

    private final ModelParameterCodec codec = new ModelParameterCodec();

    @Test
    void shouldEncodeAndDecodeWhitelistedParameters() {
        String json = codec.encode(30, 0.7D, 128_000);

        ModelParameterCodec.ModelParameters parameters = codec.decode(json);

        assertEquals(30, parameters.timeoutSeconds());
        assertEquals(0.7D, parameters.temperature());
        assertEquals(128_000, parameters.contextWindowLength());
    }

    @Test
    void shouldRejectMalformedOrEmptyJson() {
        assertThrows(ModelConfigurationException.class, () -> codec.decode("not-json"));
        assertThrows(ModelConfigurationException.class, () -> codec.decode(null));
    }

    @Test
    void shouldRejectParametersOutsideWhitelistedRange() {
        assertThrows(ModelConfigurationException.class, () -> codec.encode(0, null, null));
        assertThrows(ModelConfigurationException.class, () -> codec.encode(30, 2.1D, null));
        assertThrows(ModelConfigurationException.class, () -> codec.encode(30, null, 2_000_001));
    }
}
