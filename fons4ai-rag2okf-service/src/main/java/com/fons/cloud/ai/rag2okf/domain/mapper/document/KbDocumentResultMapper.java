package com.fons.cloud.ai.rag2okf.domain.mapper.document;

import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocumentResult;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档结果生命周期与制品登记 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbDocumentResultMapper extends BasePlusMapper<KbDocumentResult> {
}
