package com.fons.cloud.ai.rag2okf.application.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * 按当前用户档案动态创建 LangChain4j 模型客户端的应用端口。
 *
 * @author hongqy
 */
public interface ModelClientFactory {

    /**
     * 创建不注册为全局 Bean 的对话客户端。
     *
     * @param descriptor 已校验的非敏感模型描述
     * @param apiKey 仅当前调用栈暂存的明文 Key
     * @return 动态创建的对话客户端
     */
    ChatModel createChatModel(ResolvedModelDescriptor descriptor, String apiKey);

    /**
     * 创建不注册为全局 Bean 的向量客户端。
     *
     * @param descriptor 已校验的非敏感模型描述
     * @param apiKey 仅当前调用栈暂存的明文 Key
     * @return 动态创建的向量客户端
     */
    EmbeddingModel createEmbeddingModel(ResolvedModelDescriptor descriptor, String apiKey);
}
