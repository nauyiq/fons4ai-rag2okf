package com.fons.cloud.ai.rag2okf.domain.mapper;

import com.fons.cloud.ai.rag2okf.domain.entity.KbDocumentVersionEntity;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 不可变文档版本 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbDocumentVersionMapper extends BasePlusMapper<KbDocumentVersionEntity> {
}
