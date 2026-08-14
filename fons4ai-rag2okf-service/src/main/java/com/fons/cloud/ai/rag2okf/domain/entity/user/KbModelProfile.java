package com.fons.cloud.ai.rag2okf.domain.entity.user;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelTestStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelType;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.util.Date;

/**
 * 用户可复用的模型档案持久化实体。
 *
 * <p>档案只引用 Provider 连接，不复制 API Key、密文或 nonce。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_model_profile")
public class KbModelProfile extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模型档案业务标识。 */
    private String profileKey;

    /** 档案所有者本地用户主键，必须与连接所有者一致。 */
    private Long ownerUserId;

    /** 所属 Provider 连接主键。 */
    private Long connectionId;

    /** 模型调用能力类型；旧值 CHAT 仅用于历史数据读取兼容。 */
    private ModelType modelType;

    /** 厂商实际模型 ID。 */
    private String modelName;

    /** Embedding 输出维度提示。 */
    private Integer dimensions;

    /** 上下文窗口长度，暂由前端维护，未设置时为 null。 */
    private Integer contextWindowLength;

    /** 温度、超时和重试等受控参数 JSON 快照。 */
    private String parametersJson;

    /** 档案状态。 */
    private ModelProfileStatus status;

    /** 最近一次模型能力测试状态。 */
    private ModelTestStatus lastTestStatus;

    /** 最近一次模型测试时间。 */
    private Date lastTestAt;

    /** 最近一次安全化模型测试错误码。 */
    private String lastTestErrorCode;

    /**
     * 创建一个属于指定用户和 Provider 连接的启用状态模型档案。
     *
     * @param profileKey 模型档案业务标识
     * @param ownerUserId 所有者用户主键
     * @param connectionId Provider 连接主键
     * @param modelType 模型调用类型
     * @param modelName 厂商模型 ID
     * @param dimensions Embedding 输出维度提示
     * @param contextWindowLength 上下文窗口长度
     * @param parametersJson 受控参数 JSON 快照
     * @return 待持久化的启用状态模型档案
     */
    public static KbModelProfile create(String profileKey, Long ownerUserId, Long connectionId,
                                        ModelType modelType, String modelName, Integer dimensions,
                                        Integer contextWindowLength, String parametersJson) {
        KbModelProfile profile = new KbModelProfile();
        profile.profileKey = profileKey;
        profile.ownerUserId = ownerUserId;
        profile.connectionId = connectionId;
        profile.modelType = modelType;
        profile.modelName = modelName;
        profile.dimensions = dimensions;
        profile.contextWindowLength = contextWindowLength;
        profile.parametersJson = parametersJson;
        profile.status = ModelProfileStatus.ACTIVE;
        return profile;
    }

    /**
     * 更新档案的可编辑配置；已完成合并计算的维度和参数值会整体替换。
     *
     * @param modelName 厂商模型 ID，为 {@code null} 时保留原值
     * @param dimensions Embedding 输出维度提示
     * @param contextWindowLength 上下文窗口长度
     * @param parametersJson 受控参数 JSON 快照
     * @param status 档案状态，为 {@code null} 时保留原值
     */
    public void updateConfiguration(String modelName, Integer dimensions, Integer contextWindowLength,
                                    String parametersJson, ModelProfileStatus status) {
        if (modelName != null) {
            this.modelName = modelName;
        }
        this.dimensions = dimensions;
        this.contextWindowLength = contextWindowLength;
        this.parametersJson = parametersJson;
        if (status != null) {
            this.status = status;
        }
    }

    /**
     * 记录最近一次模型能力测试的安全化结果。
     *
     * @param status 测试状态
     * @param testedAt 测试时间
     * @param errorCode 安全化错误码，成功时可为 {@code null}
     */
    public void recordTestResult(ModelTestStatus status, Date testedAt, String errorCode) {
        this.lastTestStatus = status;
        this.lastTestAt = testedAt;
        this.lastTestErrorCode = errorCode;
    }
}
