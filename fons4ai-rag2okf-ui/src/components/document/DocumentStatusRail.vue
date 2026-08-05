<script setup lang="ts">
import type { DocumentSummary } from '../../api/documents'

defineProps<{ document: Pick<DocumentSummary, 'parseStatus' | 'publishStatus' | 'hasActivePublication' | 'latestTask'> }>()

function label(status: string): string {
  return ({ NOT_STARTED: '尚未解析', QUEUED: '等待处理', RUNNING: '处理中', SUCCEEDED: '已完成', FAILED: '处理失败',
    UNPUBLISHED: '未发布', PUBLISHED: '已发布', PUBLISHING: '发布中', PUBLISH_FAILED: '发布失败' } as Record<string, string>)[status] ?? status
}
</script>

<template>
  <div class="status-rail" aria-label="文档生命周期状态">
    <span class="status-step" :data-status="document.parseStatus"><i></i>解析：{{ label(document.parseStatus) }}</span>
    <span class="status-line"></span>
    <span class="status-step" :data-status="document.publishStatus"><i></i>发布：{{ label(document.publishStatus) }}</span>
    <span v-if="document.publishStatus === 'PUBLISH_FAILED' && document.hasActivePublication" class="continuity-note">上次已发布内容仍可用</span>
    <span v-else-if="document.latestTask?.status === 'RUNNING'" class="progress-note">{{ document.latestTask.progress }}% · {{ document.latestTask.stage || '处理中' }}</span>
  </div>
</template>

<style scoped>
.status-rail { display:flex; align-items:center; gap:.55rem; color:var(--muted-foreground); font-size:.78rem; flex-wrap:wrap; }
.status-step { display:inline-flex; align-items:center; gap:.35rem; } .status-step i { width:.43rem; height:.43rem; border-radius:50%; background:#8d9aac; }
.status-step[data-status='SUCCEEDED'] i,.status-step[data-status='PUBLISHED'] i { background:#36b77e; }
.status-step[data-status='FAILED'] i,.status-step[data-status='PUBLISH_FAILED'] i { background:#e8795e; }
.status-step[data-status='RUNNING'] i,.status-step[data-status='PUBLISHING'] i { background:#8b7bff; box-shadow:0 0 0 4px color-mix(in srgb,#8b7bff 16%,transparent); }
.status-line { height:1px; width:1.15rem; background:var(--border-color); } .continuity-note,.progress-note { color:#7766e8; }
</style>
