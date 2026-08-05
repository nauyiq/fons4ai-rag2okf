package com.fons.cloud.ai.rag2okf.domain.mapper;

import com.fons.cloud.ai.rag2okf.domain.entity.KbPublicationRevisionEntity;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发布 Revision MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbPublicationRevisionMapper extends BasePlusMapper<KbPublicationRevisionEntity> {
}
