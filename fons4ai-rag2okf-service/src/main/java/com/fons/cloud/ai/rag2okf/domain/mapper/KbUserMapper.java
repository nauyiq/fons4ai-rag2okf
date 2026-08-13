package com.fons.cloud.ai.rag2okf.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * Rag2OKF 本地用户 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbUserMapper extends BaseMapper<KbUser> {
}
