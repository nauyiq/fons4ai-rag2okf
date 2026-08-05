package com.fons.cloud.ai.rag2okf.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.ai.rag2okf.common.constants.ModelProfileStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelTestStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelType;
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
public class KbModelProfileEntity extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模型档案业务标识。 */
    private String profileKey;

    /** 档案所有者本地用户主键，必须与连接所有者一致。 */
    private Long ownerUserId;

    /** 所属 Provider 连接主键。 */
    private Long connectionId;

    /** 模型调用类型：CHAT 或 EMBEDDING。 */
    private ModelType modelType;

    /** 厂商实际模型 ID。 */
    private String modelName;

    /** Embedding 输出维度提示。 */
    private Integer dimensions;

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
}
