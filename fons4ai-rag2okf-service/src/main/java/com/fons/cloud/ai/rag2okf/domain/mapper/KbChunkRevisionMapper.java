package com.fons.cloud.ai.rag2okf.domain.mapper;

import com.fons.cloud.ai.rag2okf.domain.entity.KbChunkRevisionEntity;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分块 Revision MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbChunkRevisionMapper extends BasePlusMapper<KbChunkRevisionEntity> {
}
