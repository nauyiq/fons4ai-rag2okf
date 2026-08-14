package com.fons.cloud.ai.rag2okf.domain.entity.user;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelConnectionStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelProtocolType;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelTestStatus;
import com.fons.cloud.ai.rag2okf.common.model.user.EncryptedCredential;
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
public class KbModelConnection extends CommonEntity {

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

    /**
     * 创建一个属于指定用户的启用状态 Provider 连接。
     *
     * @param connectionKey 连接业务标识
     * @param ownerUserId 所有者用户主键
     * @param providerCode 厂商代码
     * @param providerName 厂商名称
     * @param displayName 连接展示名称
     * @param protocolType 调用协议类型
     * @param baseUrl 模型 API 根地址
     * @param credential 加密后的 API Key 载荷
     * @param apiKeyMask API Key 展示掩码
     * @return 待持久化的启用状态连接实体
     */
    public static KbModelConnection create(String connectionKey, Long ownerUserId,
                                                  String providerCode, String providerName,
                                                  String displayName, ModelProtocolType protocolType,
                                                  String baseUrl, EncryptedCredential credential,
                                                  String apiKeyMask) {
        KbModelConnection connection = new KbModelConnection();
        connection.connectionKey = connectionKey;
        connection.ownerUserId = ownerUserId;
        connection.providerCode = providerCode;
        connection.providerName = providerName;
        connection.displayName = displayName;
        connection.protocolType = protocolType;
        connection.baseUrl = baseUrl;
        connection.apiKeyCiphertext = credential.ciphertext();
        connection.apiKeyNonce = credential.nonce();
        connection.keyVersion = credential.keyVersion();
        connection.apiKeyMask = apiKeyMask;
        connection.status = ModelConnectionStatus.ACTIVE;
        return connection;
    }

    /**
     * 更新连接的可编辑配置；参数为 {@code null} 时保留原值。
     *
     * @param providerName 厂商名称
     * @param displayName 连接展示名称
     * @param baseUrl 模型 API 根地址
     * @param status 连接状态
     */
    public void updateConfiguration(String providerName, String displayName, String baseUrl,
                                    ModelConnectionStatus status) {
        if (providerName != null) {
            this.providerName = providerName;
        }
        if (displayName != null) {
            this.displayName = displayName;
        }
        if (baseUrl != null) {
            this.baseUrl = baseUrl;
        }
        if (status != null) {
            this.status = status;
        }
    }

    /**
     * 整体替换连接凭证，确保密文、nonce、密钥版本和展示掩码同步更新。
     *
     * @param credential 新的加密凭证载荷
     * @param apiKeyMask 新凭证的展示掩码
     */
    public void replaceCredential(EncryptedCredential credential, String apiKeyMask) {
        this.apiKeyCiphertext = credential.ciphertext();
        this.apiKeyNonce = credential.nonce();
        this.keyVersion = credential.keyVersion();
        this.apiKeyMask = apiKeyMask;
    }

    /**
     * 记录最近一次连接测试的安全化结果，不保存异常详情或明文凭证。
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
