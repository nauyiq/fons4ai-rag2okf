package com.fons.cloud.ai.rag2okf.domain.entity.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** 文档实体日志输出不得泄露对象键和任务快照。 */
class DocumentEntitySensitiveToStringTest {

    @Test
    void shouldExcludeArtifactKeysAndSnapshotsFromToString() {
        KbDocumentResult result = new KbDocumentResult();
        result.setSourceObjectKey("sensitive-source-key");
        result.setParserSnapshotJson("sensitive-parser-snapshot");
        result.setParsedDocumentObjectKey("sensitive-parsed-key");
        result.setPolicySnapshotJson("sensitive-policy-snapshot");
        KbProcessingTask task = new KbProcessingTask();
        task.setSnapshotJson("sensitive-task-snapshot");

        String rendered = result + " " + task;

        assertFalse(rendered.contains("sensitive-source-key"));
        assertFalse(rendered.contains("sensitive-parser-snapshot"));
        assertFalse(rendered.contains("sensitive-parsed-key"));
        assertFalse(rendered.contains("sensitive-policy-snapshot"));
        assertFalse(rendered.contains("sensitive-task-snapshot"));
    }
}
