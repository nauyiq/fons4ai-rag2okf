package com.fons.cloud.ai.rag2okf.infrastructure.model;

import com.fons.cloud.ai.rag2okf.common.constants.ModelType;
import com.fons.cloud.ai.rag2okf.common.dto.ResolvedModelDescriptor;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 动态 LangChain4j Client 工厂契约测试。
 *
 * @author hongqy
 */
class LangChain4jModelClientFactoryTest {

    private final LangChain4jModelClientFactory factory = new LangChain4jModelClientFactory(new ModelEndpointPolicy());

    @Test
    void shouldCreatePerInvocationOpenAiCompatibleClientsWithoutGlobalConfiguration() {
        var chat = factory.createChatModel(
                new ResolvedModelDescriptor("profile-key", ModelType.CHAT, "https://8.8.8.8/v1",
                        "chat-model", null, 30, 0.2D), "api-key"
        );
        var embedding = factory.createEmbeddingModel(
                new ResolvedModelDescriptor("profile-key", ModelType.EMBEDDING, "https://8.8.8.8/v1",
                        "embedding-model", 1024, 30, null), "api-key"
        );

        assertThat(chat).isInstanceOf(OpenAiChatModel.class);
        assertThat(embedding).isInstanceOf(OpenAiEmbeddingModel.class);
    }

    @Test
    void shouldCallAnOpenAiCompatibleMockProviderWithTheDynamicApiKey() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-api-key");
            byte[] response = """
                    {"id":"test","object":"chat.completion","created":1,"model":"chat-model",
                    "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],
                    "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            ModelEndpointPolicy endpointPolicy = mock(ModelEndpointPolicy.class);
            LangChain4jModelClientFactory localFactory = new LangChain4jModelClientFactory(endpointPolicy);
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

            String answer = localFactory.createChatModel(
                    new ResolvedModelDescriptor("profile-key", ModelType.CHAT, baseUrl,
                            "chat-model", null, 30, 0.2D), "test-api-key"
            ).chat("health check");

            assertThat(answer).isEqualTo("ok");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldCallAnOpenAiCompatibleEmbeddingMockProviderWithTheDynamicApiKey() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-api-key");
            byte[] response = """
                    {"object":"list","data":[{"object":"embedding","embedding":[0.1,0.2,0.3],"index":0}],
                    "model":"embedding-model","usage":{"prompt_tokens":1,"total_tokens":1}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            LangChain4jModelClientFactory localFactory = new LangChain4jModelClientFactory(mock(ModelEndpointPolicy.class));
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

            float[] vector = localFactory.createEmbeddingModel(
                    new ResolvedModelDescriptor("profile-key", ModelType.EMBEDDING, baseUrl,
                            "embedding-model", 3, 30, null), "test-api-key"
            ).embed("health check").content().vector();

            assertThat(vector).hasSize(3);
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429})
    void shouldExposeProviderFailuresForTheApplicationLayerToSanitize(int status) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            LangChain4jModelClientFactory localFactory = new LangChain4jModelClientFactory(mock(ModelEndpointPolicy.class));
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

            assertThatThrownBy(() -> localFactory.createChatModel(
                    new ResolvedModelDescriptor("profile-key", ModelType.CHAT, baseUrl,
                            "chat-model", null, 30, 0.2D), "test-api-key"
            ).chat("health check")).isInstanceOf(RuntimeException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldHonorTheControlledRequestTimeout() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(1_500L);
                exchange.sendResponseHeaders(503, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            LangChain4jModelClientFactory localFactory = new LangChain4jModelClientFactory(mock(ModelEndpointPolicy.class));
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

            assertThatThrownBy(() -> localFactory.createChatModel(
                    new ResolvedModelDescriptor("profile-key", ModelType.CHAT, baseUrl,
                            "chat-model", null, 1, 0.2D), "test-api-key"
            ).chat("health check")).isInstanceOf(RuntimeException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldNotFollowProviderRedirectsToAnotherEndpoint() throws Exception {
        AtomicBoolean redirectedEndpointCalled = new AtomicBoolean(false);
        HttpServer redirectedServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        redirectedServer.createContext("/v1/chat/completions", exchange -> {
            redirectedEndpointCalled.set(true);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        redirectedServer.start();
        HttpServer sourceServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sourceServer.createContext("/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + redirectedServer.getAddress().getPort()
                    + "/v1/chat/completions");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        sourceServer.start();
        try {
            LangChain4jModelClientFactory localFactory = new LangChain4jModelClientFactory(mock(ModelEndpointPolicy.class));
            String baseUrl = "http://127.0.0.1:" + sourceServer.getAddress().getPort() + "/v1";

            assertThatThrownBy(() -> localFactory.createChatModel(
                    new ResolvedModelDescriptor("profile-key", ModelType.CHAT, baseUrl,
                            "chat-model", null, 30, 0.2D), "test-api-key"
            ).chat("health check")).isInstanceOf(RuntimeException.class);

            assertThat(redirectedEndpointCalled).isFalse();
        } finally {
            sourceServer.stop(0);
            redirectedServer.stop(0);
        }
    }
}
