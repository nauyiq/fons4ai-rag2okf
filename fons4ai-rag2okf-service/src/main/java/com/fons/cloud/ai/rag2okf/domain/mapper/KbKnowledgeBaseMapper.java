package com.fons.cloud.ai.rag2okf.domain.mapper;

import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbKnowledgeBaseMapper extends BasePlusMapper<KbKnowledgeBaseEntity> {
}
