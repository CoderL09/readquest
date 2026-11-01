export type BookImportJob = {
  id: string
  bookId: string
  status: 'queued' | 'processing' | 'completed' | 'failed' | 'cancelled'
  phase: string
  progress: number
  attempts: number
  errorMessage?: string | null
}

export function activeImport(job: BookImportJob | null) {
  return job?.status === 'queued' || job?.status === 'processing'
}

export function importPhase(job: BookImportJob) {
  const labels: Record<string, string> = {
    queued: '等待处理',
    parsing: '解析文件与目录',
    segmenting: '整理阅读章节',
    persisting: '保存章节与书籍信息',
    completed: '准备完成',
    failed: '准备失败',
    cancelled: '已取消',
  }
  return labels[job.phase] ?? '正在准备书籍'
}
