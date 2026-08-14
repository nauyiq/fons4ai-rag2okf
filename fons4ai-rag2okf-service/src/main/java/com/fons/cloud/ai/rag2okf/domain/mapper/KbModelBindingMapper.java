package com.fons.cloud.ai.rag2okf.domain.mapper;

import com.fons.cloud.ai.rag2okf.domain.entity.KbModelBinding;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库模型用途绑定 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbModelBindingMapper extends BasePlusMapper<KbModelBinding> {
}
