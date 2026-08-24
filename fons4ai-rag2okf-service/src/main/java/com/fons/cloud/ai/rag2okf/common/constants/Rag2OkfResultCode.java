package com.fons.cloud.ai.rag2okf.common.constants;

import com.fons.cloud.common.result.Result;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Rag2OKF 项目统一错误码。
 *
 * <p>错误码遵循 {@code RF + 错误类型 + 五位序号}：1 表示参数或资源错误，
 * 3 表示业务执行错误，4 表示权限错误，5 表示技术执行错误。</p>
 *
 * @author hongqy
 */
@Getter
@AllArgsConstructor
public enum Rag2OkfResultCode implements Result {

    //  ==================== 参数异常 ====================
    PASSWORD_INCORRECT("RF100001", "用户名或密码错误"),
    DUPLICATE_PASSWORD_INCORRECT("RF100002", "两次密码不一致"),
    USER_EXIST("RF100003", "用户已存在"),
    WORKSPACE_NOT_FOUND("RF100004", "工作空间不存在"),
    KNOWLEDGE_BASE_NOT_FOUND("RF100005", "知识库不存在"),
    KNOWLEDGE_BASE_NAME_DUPLICATED("RF100006", "重复的知识库名称"),
    //  ==================== 文档域参数异常 ====================
    DOCUMENT_UNSUPPORTED_FILE_TYPE("RF100007", "文件类型尚不支持"),
    DOCUMENT_FILE_SECURITY_REJECTED("RF100008", "文件未通过安全校验"),
    DOCUMENT_FILE_LIMIT_EXCEEDED("RF100009", "文件、页数或时长超过限制"),
    DOCUMENT_CURRENT_FILE_CONFLICT("RF100010", "当前文件已变化"),
    CHUNK_POLICY_INVALID("RF100011", "分块参数不合法"),


    //  ==================== 业务异常 ====================
    KB_VERSION_CONFLICT("RF300001", "知识库版本冲突，请刷新后重试"),
    BINDING_USAGE_TYPE_DUPLICATE("RF300002", "模型用途绑定不能重复"),
    MODEL_PROFILE_INVALID("RF300003", "模型档案无效或不兼容"),
    EMBEDDING_DIMS_MISMATCH("RF300004", "Embedding模型维度与系统配置不一致"),

    //  ==================== 模型与任务执行错误 ====================
    MODEL_TEST_FAILED("RF300005", "模型连通性测试失败"),
    EMBEDDING_KB_NOT_FOUND("RF300006", "向量化知识库不存在"),
    EMBEDDING_PROFILE_UNAVAILABLE("RF300007", "向量化模型档案不可用"),
    EMBEDDING_TASK_DIMS_MISMATCH("RF300008", "向量化结果维度不匹配"),
    EMBEDDING_MODEL_ERROR("RF300009", "向量化模型调用失败"),
    RECHUNK_CONFIRMATION_REQUIRED("RF300010", "重新分块需要显式确认"),
    PARSE_NOT_SUCCEEDED("RF300011", "文档解析尚未成功"),
    PUBLISH_PARSE_NOT_SUCCEEDED("RF300012", "发布前解析尚未成功"),
    PUBLISH_CHUNK_NOT_SUCCEEDED("RF300013", "发布前分块尚未成功"),
    TASK_NOT_RETRYABLE("RF300014", "当前任务状态不可重试"),

    //  ==================== 文档域业务异常 ====================
    PARSER_NOT_AVAILABLE("RF300015", "解析器暂未开放"),
    MODEL_CAPABILITY_MISSING("RF300016", "缺少必要模型能力"),
    MODEL_CAPABILITY_PROTOCOL_UNSUPPORTED("RF300017", "模型协议不支持所需能力"),
    PARSED_DOCUMENT_INVALID("RF300018", "解析结果不符合规范"),
    SEMANTIC_EMBEDDING_UNAVAILABLE("RF300019", "语义分块向量能力不可用"),
    TASK_INPUT_SUPERSEDED("RF300020", "任务输入已不是当前内容"),
    PUBLICATION_EMBEDDING_UNAVAILABLE("RF300021", "发布所需向量能力不可用"),
    DOCUMENT_DELETED("RF300022", "文档已删除"),
    DOCUMENT_CLEANUP_FAILED("RF300023", "内容清理未完成"),

    //  ==================== 权限异常 ====================
    NOT_PERMISSION_CREATE_DATABASES("RF400001", "无权限创建工作空间"),
    NOT_PERMISSION_UPDATE_DATABASES("RF400002", "无权限编辑知识库"),
    NOT_PERMISSION_DELETE_DATABASES("RF400003", "无权限删除知识库"),


    //  ==================== 技术执行异常 ====================
    PROJECTION_WRITE_ERROR("RF500001", "检索投影写入失败"),
    PROJECTION_CLEANUP_ERROR("RF500002", "检索投影清理失败"),
    PROJECTION_QUERY_ERROR("RF500003", "检索投影查询失败"),
    PROJECTION_BOOTSTRAP_ERROR("RF500004", "检索投影初始化失败"),
    PROJECTION_VERIFY_ERROR("RF500005", "检索投影校验失败"),
    NO_EXECUTOR("RF500006", "任务执行器不存在"),
    TASK_EXECUTION_ERROR("RF500007", "任务执行异常"),
    PAYLOAD_INVALID("RF500008", "任务输入快照无效"),
    PARSE_ARTIFACT_ERROR("RF500009", "解析产物访问失败"),
    PARSE_UNEXPECTED_ERROR("RF500010", "解析执行异常"),
    RECHUNK_ARTIFACT_ERROR("RF500011", "重新分块产物访问失败"),
    RECHUNK_UNEXPECTED_ERROR("RF500012", "重新分块执行异常"),
    PROJECTION_COUNT_MISMATCH("RF500013", "投影写入数量不一致"),
    PUBLISH_EMPTY_CHUNKS("RF500014", "发布分块产物为空"),
    PUBLISH_CAS_CONFLICT("RF500015", "发布指针切换冲突"),
    PUBLISH_PROJECTION_ERROR("RF500016", "发布投影失败"),
    PUBLISH_ARTIFACT_ERROR("RF500017", "发布产物访问失败"),
    PUBLISH_UNEXPECTED_ERROR("RF500018", "发布执行异常"),

    //  ==================== 文档域技术执行异常 ====================
    MODEL_CAPABILITY_CALL_FAILED("RF500019", "模型能力调用失败"),
    DOCUMENT_SOURCE_ARTIFACT_ERROR("RF500020", "源文件存储访问失败"),




    ;

    private final String code;
    private final String message;
}
