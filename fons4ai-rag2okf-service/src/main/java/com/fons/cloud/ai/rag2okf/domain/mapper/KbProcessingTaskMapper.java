package com.fons.cloud.ai.rag2okf.domain.mapper;

import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异步处理任务 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbProcessingTaskMapper extends BasePlusMapper<KbProcessingTaskEntity> {
}
