package com.fons.cloud.ai.rag2okf.model;

import com.fons.cloud.ai.rag2okf.infrastructure.model.ModelEndpointPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用户自定义模型地址的 SSRF 防护测试。
 *
 * @author hongqy
 */
class ModelEndpointPolicyTest {

    private final ModelEndpointPolicy policy = new ModelEndpointPolicy();

    @Test
    void shouldAcceptAnHttpsPublicEndpoint() {
        assertThatCode(() -> policy.validate("https://8.8.8.8/v1"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectInsecureAndPrivateNetworkEndpoints() {
        assertThatThrownBy(() -> policy.validate("http://api.example.com/v1"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> policy.validate("https://127.0.0.1/v1"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> policy.validate("https://[::1]/v1"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> policy.validate("https://169.254.169.254/latest/meta-data"))
                .isInstanceOf(RuntimeException.class);
    }
}
