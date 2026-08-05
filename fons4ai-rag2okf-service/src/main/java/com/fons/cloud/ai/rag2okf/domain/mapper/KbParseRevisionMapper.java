package com.fons.cloud.ai.rag2okf.domain.mapper;

import com.fons.cloud.ai.rag2okf.domain.entity.KbParseRevisionEntity;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 解析 Revision MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbParseRevisionMapper extends BasePlusMapper<KbParseRevisionEntity> {
}
