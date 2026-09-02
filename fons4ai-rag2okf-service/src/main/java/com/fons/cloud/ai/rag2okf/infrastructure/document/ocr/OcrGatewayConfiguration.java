package com.fons.cloud.ai.rag2okf.infrastructure.document.ocr;

import com.fons.cloud.ai.capability.ocr.PaddleOcrDocumentParser;
import com.fons.cloud.ai.capability.ocr.PaddleOcrDocumentParsers;
import com.fons.cloud.ai.capability.ocr.PaddleOcrProvider;
import com.fons.cloud.ai.capability.ocr.official.PaddleOcrOfficialOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;

/**
 * `paddleocr-official` Gateway 的受控装配。
 *
 * <p>开关默认关闭；Token 仅由运行环境注入，不会进入日志、结果或异常消息。</p>
 *
 * @author hongqy
 */
@Configuration
@ConditionalOnProperty(prefix = "rag2okf.document.ocr.official", name = "enabled", havingValue = "true")
public class OcrGatewayConfiguration {

    /**
     * 构造唯一的官方 OCR Gateway，不注册 local Provider 或默认回退实现。
     *
     * @param baseUri 官方 API 基础地址
     * @param accessToken 仅用于官方 Authorization 请求头的访问令牌
     * @param requestTimeout 单次提交、查询或下载的超时
     * @param pollTimeout 异步任务的总轮询超时
     * @param pollInterval 两次状态查询之间的间隔
     * @return 已绑定 official Provider 的 OCR Gateway
     */
    @Bean
    public OcrGateway ocrGateway(
            @Value("${rag2okf.document.ocr.official.base-uri}") URI baseUri,
            @Value("${rag2okf.document.ocr.official.access-token}") String accessToken,
            @Value("${rag2okf.document.ocr.official.request-timeout}") Duration requestTimeout,
            @Value("${rag2okf.document.ocr.official.poll-timeout}") Duration pollTimeout,
            @Value("${rag2okf.document.ocr.official.poll-interval}") Duration pollInterval
    ) {
        PaddleOcrOfficialOptions options = new PaddleOcrOfficialOptions(
                baseUri, accessToken, requestTimeout, pollTimeout, pollInterval);
        PaddleOcrDocumentParser parser = PaddleOcrDocumentParsers.create(
                PaddleOcrProvider.PADDLEOCR_OFFICIAL, options);
        return new PaddleOcrOfficialGateway(parser);
    }
}
