package com.fons.cloud.ai.rag2okf.infrastructure.document.parser;

import com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy.DocumentParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 解析器策略注册表装配。
 *
 * <p>以 Spring 容器内的全部 {@link DocumentParser} 实现（本期为 Built-in 启用骨架
 * 与 MinerU 禁用骨架）装配 {@link DocumentParserRegistry}；同类型重复注册由注册表
 * 构造时拒绝。上传链路创建任务前通过注册表做可用性预检，MINERU 在任何 IO 前失败。</p>
 *
 * @author hongqy
 */
@Configuration
public class DocumentParserConfiguration {

    /**
     * 装配解析器注册表。
     *
     * @param parsers 容器内全部解析器实现
     * @return 已按类型索引的解析器注册表
     */
    @Bean
    public DocumentParserRegistry documentParserRegistry(List<DocumentParser> parsers) {
        return new DocumentParserRegistry(parsers);
    }
}
