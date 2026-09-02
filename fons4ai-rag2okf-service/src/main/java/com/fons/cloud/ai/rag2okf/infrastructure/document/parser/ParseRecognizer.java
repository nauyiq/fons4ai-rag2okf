package com.fons.cloud.ai.rag2okf.infrastructure.document.parser;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileCapability;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentPrecheckedFile;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseIntent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 根据已校验的文件事实生成解析意图。
 *
 * <p>上传预检已经负责扩展名、服务端 MIME 和魔数/容器的一致性校验。本组件不重复读取
 * 魔数、不调用模型或 OCR，只根据可信文件事实决定唯一解析器。</p>
 *
 * @author hongqy
 */
@Component
public class ParseRecognizer {

    private final BuiltInFileCapabilityCatalog capabilityCatalog;

    /**
     * 创建解析意图识别器。
     *
     * @param capabilityCatalog 已受理文件格式清单
     */
    public ParseRecognizer(BuiltInFileCapabilityCatalog capabilityCatalog) {
        this.capabilityCatalog = capabilityCatalog;
    }

    /**
     * 识别唯一目标解析器。
     *
     * <p>本方法只读取上传预检已经确认的文件名和服务端 MIME，不读取文件内容。
     * PDF OCR 计划由 PDF 策略在实际解析阶段生成。</p>
     *
     * @param precheckedFile 上传预检确认的文件事实
     * @return 不含秘密信息的解析意图
     */
    public ParseIntent recognize(DocumentPrecheckedFile precheckedFile) {
        if (precheckedFile == null) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID);
        }
        DocumentFileCapability capability = capabilityCatalog.require(precheckedFile.getFilename(), precheckedFile.getContentType());
        String extension = extensionOf(capability);
        return new ParseIntent(
                capability.category(), ParserType.BUILT_IN,
                capability.requiredCapabilities(), capability.enhancementCapabilities(),
                List.of("VERIFIED_EXTENSION:" + extension,
                        "VERIFIED_MIME:" + precheckedFile.getContentType().toLowerCase(Locale.ROOT)));
    }

    private String extensionOf(DocumentFileCapability capability) {
        return capability.extension().toLowerCase(Locale.ROOT);
    }
}
