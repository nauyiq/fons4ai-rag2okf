package com.fons.cloud.ai.rag2okf.domain.mapper;

import com.fons.cloud.ai.rag2okf.domain.entity.KbOutboxEventEntity;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事务 Outbox 事件 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbOutboxEventMapper extends BasePlusMapper<KbOutboxEventEntity> {
}
