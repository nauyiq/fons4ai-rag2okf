package com.fons.cloud.ai.rag2okf.domain.mapper;

import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 源文档 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbSourceDocumentMapper extends BasePlusMapper<KbSourceDocumentEntity> {
}
