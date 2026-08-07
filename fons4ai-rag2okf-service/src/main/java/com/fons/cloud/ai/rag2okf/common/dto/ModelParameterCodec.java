package com.fons.cloud.ai.rag2okf.common.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.common.exeception.ModelConfigurationException;
import org.springframework.stereotype.Component;

/**
 * 模型受控参数 JSON 的编解码器，禁止接收任意 Provider 参数。
 *
 * @author hongqy
 */
@Component
public class ModelParameterCodec {

    private static final int MIN_TIMEOUT_SECONDS = 1;
    private static final int MAX_TIMEOUT_SECONDS = 120;
    private static final double MIN_TEMPERATURE = 0D;
    private static final double MAX_TEMPERATURE = 2D;

    private final ObjectMapper objectMapper;

    /**
     * 创建参数编解码器。
     *
     * @param objectMapper Spring 管理的 JSON 编解码器
     */
    public ModelParameterCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将受控参数编码为持久化 JSON。
     *
     * @param timeoutSeconds 请求超时秒数
     * @param temperature 对话温度
     * @return 受控参数 JSON
     */
    public String encode(Integer timeoutSeconds, Double temperature) {
        validate(timeoutSeconds, temperature);
        try {
            return objectMapper.writeValueAsString(new ModelParameters(timeoutSeconds, temperature));
        } catch (JsonProcessingException exception) {
            throw new ModelConfigurationException(exception);
        }
    }

    /**
     * 解析已保存的受控参数。
     *
     * @param parametersJson 持久化 JSON
     * @return 参数对象
     */
    public ModelParameters decode(String parametersJson) {
        try {
            ModelParameters parameters = objectMapper.readValue(parametersJson, ModelParameters.class);
            validate(parameters.timeoutSeconds(), parameters.temperature());
            return parameters;
        } catch (JsonProcessingException exception) {
            throw new ModelConfigurationException(exception);
        }
    }

    private void validate(Integer timeoutSeconds, Double temperature) {
        if (timeoutSeconds != null && (timeoutSeconds < MIN_TIMEOUT_SECONDS || timeoutSeconds > MAX_TIMEOUT_SECONDS)
                || temperature != null && (temperature < MIN_TEMPERATURE || temperature > MAX_TEMPERATURE)) {
            throw new ModelConfigurationException();
        }
    }

    /**
     * 允许持久化的模型参数白名单。
     *
     * @param timeoutSeconds 请求超时秒数
     * @param temperature 对话温度
     * @author hongqy
     */
    public record ModelParameters(Integer timeoutSeconds, Double temperature) {
    }
}
