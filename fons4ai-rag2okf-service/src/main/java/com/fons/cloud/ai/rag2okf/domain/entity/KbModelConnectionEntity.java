package com.fons.cloud.ai.rag2okf.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.ai.rag2okf.common.constants.ModelConnectionStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelProtocolType;
import com.fons.cloud.ai.rag2okf.common.constants.ModelTestStatus;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.util.Date;

/**
 * 用户级 Provider 连接与加密凭证持久化实体。
 *
 * <p>API Key 只以密文和 nonce 形式存储；明文只允许在凭证适配器的短暂调用内存中存在。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true, exclude = {"apiKeyCiphertext", "apiKeyNonce", "keyVersion"})
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_model_connection")
public class KbModelConnectionEntity extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Provider 连接业务标识。 */
    private String connectionKey;

    /** 连接所有者本地用户主键。 */
    private Long ownerUserId;

    /** 内置模板或 CUSTOM 厂商代码。 */
    private String providerCode;

    /** 用户可识别的厂商名称。 */
    private String providerName;

    /** 当前用户下唯一的连接展示名称。 */
    private String displayName;

    /** 调用协议类型。 */
    private ModelProtocolType protocolType;

    /** 模型 API 根地址，调用前必须经过出站安全校验。 */
    private String baseUrl;

    /** AES-GCM 加密后的 API Key 密文，禁止进入通用 DTO 或日志。 */
    private byte[] apiKeyCiphertext;

    /** 与密文一一对应的随机 nonce。 */
    private byte[] apiKeyNonce;

    /** 部署主密钥版本，不保存主密钥本身。 */
    private String keyVersion;

    /** 仅供界面识别的不可逆 API Key 掩码。 */
    private String apiKeyMask;

    /** 连接状态。 */
    private ModelConnectionStatus status;

    /** 最近一次连接测试状态。 */
    private ModelTestStatus lastTestStatus;

    /** 最近一次连接测试时间。 */
    private Date lastTestAt;

    /** 最近一次安全化连接测试错误码。 */
    private String lastTestErrorCode;
}
