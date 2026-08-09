package com.fons.cloud.ai.rag2okf.infrastructure.model;

import com.fons.cloud.ai.rag2okf.common.constants.ModelType;
import com.fons.cloud.ai.rag2okf.common.exeception.ModelConfigurationException;
import com.fons.cloud.ai.rag2okf.common.dto.ModelClientFactory;
import com.fons.cloud.ai.rag2okf.common.dto.ResolvedModelDescriptor;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;

/**
 * 动态创建 OpenAI-compatible LangChain4j 客户端的基础设施适配器。
 *
 * <p>客户端只在当前调用栈创建，不注册为全局 ChatModel 或 EmbeddingModel Bean。</p>
 *
 * @author hongqy
 */
@Component
@RequiredArgsConstructor
public class LangChain4jModelClientFactory implements ModelClientFactory {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 120;

    private final ModelEndpointPolicy endpointPolicy;

    @Override
    public ChatModel createChatModel(ResolvedModelDescriptor descriptor, String apiKey) {
        // 读取别名兼容：旧值 CHAT 归一为 LLM 走对话客户端。
        if (!"LLM".equals(ModelType.normalize(descriptor.modelType().getValue()))) {
            throw new ModelConfigurationException();
        }
        validateInvocationInput(descriptor, apiKey);
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .httpClientBuilder(noRedirectHttpClientBuilder(timeout(descriptor.timeoutSeconds())))
                .baseUrl(descriptor.baseUrl())
                .apiKey(apiKey)
                .modelName(descriptor.modelName())
                .timeout(timeout(descriptor.timeoutSeconds()))
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false);
        if (descriptor.temperature() != null) {
            builder.temperature(descriptor.temperature());
        }
        return builder.build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(ResolvedModelDescriptor descriptor, String apiKey) {
        if (!"EMBEDDING".equals(ModelType.normalize(descriptor.modelType().getValue()))) {
            throw new ModelConfigurationException();
        }
        validateInvocationInput(descriptor, apiKey);
        return OpenAiEmbeddingModel.builder()
                .httpClientBuilder(noRedirectHttpClientBuilder(timeout(descriptor.timeoutSeconds())))
                .baseUrl(descriptor.baseUrl())
                .apiKey(apiKey)
                .modelName(descriptor.modelName())
                .dimensions(descriptor.dimensions())
                .timeout(timeout(descriptor.timeoutSeconds()))
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    private void validateInvocationInput(ResolvedModelDescriptor descriptor, String apiKey) {
        if (descriptor == null || apiKey == null || apiKey.isBlank() || descriptor.modelName() == null
                || descriptor.modelName().isBlank()) {
            throw new ModelConfigurationException();
        }
        endpointPolicy.validate(descriptor.baseUrl());
    }

    private Duration timeout(Integer timeoutSeconds) {
        int effectiveTimeout = timeoutSeconds == null ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
        if (effectiveTimeout < 1 || effectiveTimeout > MAX_TIMEOUT_SECONDS) {
            throw new ModelConfigurationException();
        }
        return Duration.ofSeconds(effectiveTimeout);
    }

    private HttpClientBuilder noRedirectHttpClientBuilder(Duration requestTimeout) {
        return new NoRedirectHttpClientBuilder(requestTimeout);
    }

    /**
     * 使用 JDK HTTP 客户端实现 OpenAI-compatible 调用，并强制禁用重定向。
     *
     * @author hongqy
     */
    private static final class NoRedirectHttpClientBuilder implements HttpClientBuilder {

        private Duration connectTimeout;
        private Duration readTimeout;

        private NoRedirectHttpClientBuilder(Duration requestTimeout) {
            this.connectTimeout = requestTimeout;
            this.readTimeout = requestTimeout;
        }

        @Override
        public Duration connectTimeout() {
            return connectTimeout;
        }

        @Override
        public HttpClientBuilder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        @Override
        public Duration readTimeout() {
            return readTimeout;
        }

        @Override
        public HttpClientBuilder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        @Override
        public dev.langchain4j.http.client.HttpClient build() {
            return new NoRedirectHttpClient(connectTimeout, readTimeout);
        }
    }

    /**
     * 不允许重定向的 LangChain4j HTTP 适配器。
     *
     * @author hongqy
     */
    private static final class NoRedirectHttpClient implements dev.langchain4j.http.client.HttpClient {

        private final java.net.http.HttpClient delegate;
        private final Duration readTimeout;

        private NoRedirectHttpClient(Duration connectTimeout, Duration readTimeout) {
            this.delegate = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .followRedirects(Redirect.NEVER)
                    .build();
            this.readTimeout = readTimeout;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            try {
                java.net.http.HttpResponse<String> response = delegate.send(toJdkRequest(request), BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new HttpException(response.statusCode(), "Provider request failed");
                }
                return SuccessfulHttpResponse.builder()
                        .statusCode(response.statusCode())
                        .headers(response.headers().map())
                        .body(response.body())
                        .build();
            } catch (java.net.http.HttpTimeoutException exception) {
                throw new TimeoutException(exception);
            } catch (IOException exception) {
                throw new ModelConfigurationException();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ModelConfigurationException();
            }
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventListener listener) {
            try {
                listener.onOpen(execute(request));
                listener.onClose();
            } catch (RuntimeException exception) {
                listener.onError(exception);
            }
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            try {
                java.net.http.HttpResponse<java.io.InputStream> response = delegate.send(toJdkRequest(request),
                        BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new HttpException(response.statusCode(), "Provider request failed");
                }
                try (java.io.InputStream body = response.body()) {
                    listener.onOpen(SuccessfulHttpResponse.builder().statusCode(response.statusCode())
                            .headers(response.headers().map()).build());
                    parser.parse(body, listener);
                    listener.onClose();
                }
            } catch (java.net.http.HttpTimeoutException exception) {
                listener.onError(new TimeoutException(exception));
            } catch (IOException exception) {
                listener.onError(new ModelConfigurationException());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                listener.onError(new ModelConfigurationException());
            } catch (RuntimeException exception) {
                listener.onError(exception);
            }
        }

        private java.net.http.HttpRequest toJdkRequest(HttpRequest request) {
            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(URI.create(request.url()))
                    .timeout(readTimeout)
                    .method(request.method().name(), request.body() == null
                            ? java.net.http.HttpRequest.BodyPublishers.noBody()
                            : java.net.http.HttpRequest.BodyPublishers.ofString(request.body()));
            for (Map.Entry<String, java.util.List<String>> entry : request.headers().entrySet()) {
                for (String value : entry.getValue()) {
                    builder.header(entry.getKey(), value);
                }
            }
            return builder.build();
        }
    }
}
