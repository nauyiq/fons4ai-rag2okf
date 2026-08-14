package com.fons.cloud.ai.rag2okf.infrastructure.support.user;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.fons.cloud.ai.rag2okf.common.exception.user.ModelConfigurationException;
import org.springframework.stereotype.Component;

/**
 * 模型受控参数的 Fastjson2 持久化编解码器，禁止接收任意 Provider 参数。
 *
 * @author hongqy
 */
@Component
public class ModelParameterCodec {

    private static final int MIN_TIMEOUT_SECONDS = 1;
    private static final int MAX_TIMEOUT_SECONDS = 120;
    private static final double MIN_TEMPERATURE = 0D;
    private static final double MAX_TEMPERATURE = 2D;
    private static final int MAX_CONTEXT_WINDOW_LENGTH = 2_000_000;

    /**
     * 将受控参数编码为持久化 JSON。
     *
     * @param timeoutSeconds 请求超时秒数
     * @param temperature 对话温度
     * @return 受控参数 JSON
     */
    public String encode(Integer timeoutSeconds, Double temperature) {
        return encode(timeoutSeconds, temperature, null);
    }

    /**
     * 将受控参数编码为持久化 JSON（含上下文窗口长度）。
     *
     * @param timeoutSeconds 请求超时秒数
     * @param temperature 对话温度
     * @param contextWindowLength 上下文窗口长度，NULL 表示未设置
     * @return 受控参数 JSON
     */
    public String encode(Integer timeoutSeconds, Double temperature, Integer contextWindowLength) {
        validate(timeoutSeconds, temperature, contextWindowLength);
        try {
            return JSON.toJSONString(new ModelParameters(timeoutSeconds, temperature, contextWindowLength));
        } catch (JSONException exception) {
            throw new ModelConfigurationException(exception);
        }
    }

    /**
     * 解析并验证已保存的受控参数。
     *
     * @param parametersJson 持久化 JSON
     * @return 参数对象
     */
    public ModelParameters decode(String parametersJson) {
        ModelParameters parameters;
        try {
            parameters = JSON.parseObject(parametersJson, ModelParameters.class);
        } catch (JSONException exception) {
            throw new ModelConfigurationException(exception);
        }
        if (parameters == null) {
            throw new ModelConfigurationException();
        }
        validate(parameters.timeoutSeconds(), parameters.temperature(), parameters.contextWindowLength());
        return parameters;
    }

    private void validate(Integer timeoutSeconds, Double temperature, Integer contextWindowLength) {
        if (timeoutSeconds != null && (timeoutSeconds < MIN_TIMEOUT_SECONDS || timeoutSeconds > MAX_TIMEOUT_SECONDS)
                || temperature != null && (temperature < MIN_TEMPERATURE || temperature > MAX_TEMPERATURE)
                || contextWindowLength != null
                && (contextWindowLength < 1 || contextWindowLength > MAX_CONTEXT_WINDOW_LENGTH)) {
            throw new ModelConfigurationException();
        }
    }

    /**
     * 允许持久化的模型参数白名单。
     *
     * @param timeoutSeconds 请求超时秒数
     * @param temperature 对话温度
     * @param contextWindowLength 上下文窗口长度，NULL 表示未设置
     * @author hongqy
     */
    public record ModelParameters(Integer timeoutSeconds, Double temperature, Integer contextWindowLength) {
    }
}
