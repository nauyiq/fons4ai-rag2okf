package com.fons.cloud.ai.rag2okf.domain.mapper.user;

import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelConnection;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户级 Provider 连接 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbModelConnectionMapper extends BasePlusMapper<KbModelConnection> {
}
