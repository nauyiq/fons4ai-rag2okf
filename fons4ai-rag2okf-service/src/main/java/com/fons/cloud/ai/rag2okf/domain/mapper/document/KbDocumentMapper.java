package com.fons.cloud.ai.rag2okf.domain.mapper.document;

import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocument;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档身份与用户可见状态 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbDocumentMapper extends BasePlusMapper<KbDocument> {
}
