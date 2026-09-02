package com.fons.cloud.ai.rag2okf.common.model.document;

import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocument;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocumentResult;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbProcessingTask;
import lombok.Getter;

/** 上传事务完成后的文档事实。 */
@Getter
public class DocumentUploadOutcome {

    /** 新建文档。 */
    private final KbDocument document;

    /** 当前源文件结果。 */
    private final KbDocumentResult result;

    /** 可选解析任务。 */
    private final KbProcessingTask task;

    public DocumentUploadOutcome(KbDocument document, KbDocumentResult result, KbProcessingTask task) {
        this.document = document; this.result = result; this.task = task;
    }
}
