package com.fons.cloud.ai.rag2okf.domain.mapper;

import com.fons.cloud.ai.rag2okf.domain.entity.KbModelProfileEntity;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户模型档案 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbModelProfileMapper extends BasePlusMapper<KbModelProfileEntity> {
}
