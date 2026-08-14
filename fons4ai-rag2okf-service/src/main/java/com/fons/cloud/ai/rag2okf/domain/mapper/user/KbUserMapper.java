package com.fons.cloud.ai.rag2okf.domain.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * Rag2OKF 本地用户 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbUserMapper extends BaseMapper<KbUser> {
}
