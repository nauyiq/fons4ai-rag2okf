package com.fons.cloud.ai.rag2okf.domain.mapper.document;

import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

/**
 * 文档域异步处理任务 MyBatis-Plus Mapper。
 *
 * <p>与旧 {@code domain.mapper.KbProcessingTaskMapper}（映射
 * {@code KbProcessingTaskEntity}）类名相同：MyBatis 自动扫描的默认 bean 名
 * 均为首字母小写类名，同名会导致 Spring bean 冲突。本接口以
 * {@code @Component} 声明显式 bean 名 {@code documentKbProcessingTaskMapper}
 * 区分，旧 Mapper 保持默认名 {@code kbProcessingTaskMapper} 不动；
 * 注入按类型（{@code BasePlusMapper<KbProcessingTask>}）无歧义。
 * 旧链路退出归 TP-008。</p>
 *
 * @author hongqy
 */
@Mapper
@Component("documentKbProcessingTaskMapper")
public interface KbProcessingTaskMapper extends BasePlusMapper<KbProcessingTask> {
}
