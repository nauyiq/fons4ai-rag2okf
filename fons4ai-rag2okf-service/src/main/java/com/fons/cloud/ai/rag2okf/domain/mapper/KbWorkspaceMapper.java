package com.fons.cloud.ai.rag2okf.domain.mapper;

import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识工作空间 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbWorkspaceMapper extends BasePlusMapper<KbWorkspaceEntity> {
}
