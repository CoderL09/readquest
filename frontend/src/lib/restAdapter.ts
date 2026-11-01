import { AxiosError, AxiosHeaders, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'

type Row = Record<string, unknown>
type Page = { items: Row[]; page: number; pageSize: number; total: number; hasMore: boolean }
function object(value: unknown): Row {
  return value && typeof value === 'object' ? (value as Row) : {}
}
function body(value: unknown): Row {
  if (typeof value !== 'string') return object(value)
  try {
    return object(JSON.parse(value))
  } catch {
    return {}
  }
}
function displayBook(row: Row, userId: unknown): Row {
  return {
    ...row,
    coverUrl: row.coverUrl ?? '',
    readingMode: row.readingMode ?? 'continuous',
    progress: row.progress ?? 0,
    isUploader: row.ownerId === userId,
    publishedAt: row.createdAt,
  }
}

/** Preserves page view models while sending REST resource paths to Java. */
export function createRestAdapter(transport: AxiosAdapter): AxiosAdapter {
  const calibrationSteps = new Map<string, number>()
  const wire: AxiosAdapter = async (config) => {
    const response = await transport(config)
    if (
      (!config.responseType || config.responseType === 'json') &&
      typeof response.data === 'string'
    ) {
      try {
        response.data = JSON.parse(response.data)
      } catch {
        /* Preserve non-JSON responses. */
      }
    }
    return response
  }
  return async (original) => {
    const config: InternalAxiosRequestConfig = {
      ...original,
      headers: new AxiosHeaders(original.headers),
    }
    const url = config.url ?? ''
    const method = (config.method ?? 'get').toLowerCase()
    const data = body(config.data)
    const userId = (original as InternalAxiosRequestConfig & { _authUserId?: string })._authUserId
    const send = (path: string, params?: Row) =>
      wire({ ...config, method: 'get', url: path, data: undefined, params })
    async function pages(path: string, params: Row = {}, first?: Page): Promise<Row[]> {
      const items: Row[] = first ? [...first.items] : []
      if (first && !first.hasMore) return items
      for (let page = first ? first.page + 1 : 1; page <= 1000; page++) {
        const result = await send(path, { ...params, page, pageSize: 100 })
        const value = result.data as Page
        if (!Array.isArray(value.items)) throw new Error('Invalid Java pagination response')
        items.push(...value.items)
        if (!value.hasMore) return items
        if (!value.items.length) throw new Error('Java pagination did not advance')
      }
      throw new Error('Java pagination exceeded supported size')
    }
    async function note(row: Row): Promise<Row> {
      if (row.book && row.chapter) return row
      let book: Row = { id: row.bookId, title: 'Unknown book', author: '' }
      let chapter: Row = { id: row.chapterId, title: 'Book note' }
      try {
        const result = await send(`/books/${encodeURIComponent(String(row.bookId))}`)
        book = { id: row.bookId, title: result.data.title, author: result.data.author }
        if (row.chapterId) {
          const result = await send(
            `/books/${encodeURIComponent(String(row.bookId))}/chapters/${encodeURIComponent(String(row.chapterId))}`,
          )
          chapter = { id: row.chapterId, title: result.data.stageTitle || 'Book note' }
        }
      } catch (error) {
        if (!(error instanceof AxiosError) || error.response?.status !== 404) throw error
      }
      return { ...row, book, chapter }
    }
    let transform = async (value: unknown): Promise<unknown> => value
    const authPaths: Record<string, [string, string]> = {
      '/auth/login': ['/sessions', 'post'],
      '/auth/register': ['/users', 'post'],
      '/auth/refresh': ['/sessions/current/tokens', 'post'],
      '/auth/logout': ['/sessions/current', 'delete'],
    }
    if (authPaths[url]) {
      ;[config.url, config.method] = authPaths[url]
      if (config.method === 'delete') config.data = undefined
    } else if (/^\/study-groups\/[^/]+\/membership$/.test(url) && method === 'post') {
      config.method = 'put'
      config.data = undefined
    } else if (/^\/study-groups\/[^/]+\/blocks\/[^/]+$/.test(url)) {
      const parts = url.split('/')
      config.url = `/users/me/blocks/${parts[4]}`
      config.data = method === 'put' ? JSON.stringify({ bookId: parts[2] }) : undefined
    } else if (/^\/study-groups\/[^/]+\/english-coach$/.test(url)) {
      config.url = url.replace(/\/english-coach$/, '/english-assistance')
    } else if (url === '/imports/catalog') {
      config.url = '/catalog-imports'
    } else if (url === '/imports/url/inspect') {
      config.url = '/source-inspections'
    } else if (url === '/imports/url') {
      config.url = '/remote-imports'
    } else if (/^\/imports\/[^/]+$/.test(url) && method === 'get') {
      config.url = url.replace('/imports/', '/remote-imports/')
    } else if (/^\/books\/[^/]+\/scene-image$/.test(url)) {
      config.url = url.replace(/\/scene-image$/, '/scene-images')
    } else if (url === '/module1/analyze') {
      config.url = `/books/${encodeURIComponent(String(data.book_id))}/context-analyses`
      config.data = JSON.stringify({ currentSentence: data.current_sentence })
      config.timeout = 60000
    } else if (url === '/module2/intervene') {
      config.url = '/reading-interventions'
      config.data = JSON.stringify({
        surroundingText: data.surrounding_text,
        stuckChunk: data.stuck_chunk,
      })
    } else if (/^\/stages\/[^/]+\/generate-quizzes$/.test(url)) {
      config.url = `/chapters/${url.split('/')[2]}/quizzes`
      config.data = undefined
    } else if (url === '/users/me/avatar' && method === 'post') {
      config.method = 'put'
    } else if (url === '/dictionaries/current') {
      config.url = '/users/me/dictionary'
      if (method === 'delete') transform = async () => ({ deleted: true })
    } else if (url === '/dictionaries' && method === 'post') {
      config.url = '/users/me/dictionary'
      config.method = 'put'
    } else if (url === '/dictionaries/lookup') {
      config.url = '/users/me/dictionary/entries'
    } else if (url === '/membership/status') {
      config.url = '/users/me/entitlements'
    } else if (url === '/growth/summary') {
      config.url = '/users/me/growth'
    } else if (url === '/library/reading-profile') {
      config.url = '/users/me/reading-profile'
    } else if (/^\/library\/metadata\/[^/]+\/start$/.test(url)) {
      config.url = `/catalog/books/${url.split('/')[3]}/imports`
      config.data = undefined
    } else if (url === '/library/metadata') {
      config.url = '/users/me/recommendations'
    } else if (/^\/library\/metadata\/[^/]+$/.test(url)) {
      config.url = url.replace('/library/metadata/', '/catalog/books/')
    } else if (url === '/passport') {
      config.url = '/users/me/reading-passport'
    } else if (url === '/calibration/start') {
      config.url = '/calibration-sessions'
      config.data = undefined
      transform = async (value) => {
        const result = object(value)
        calibrationSteps.set(String(result.sessionId), Number(result.currentStep))
        return result
      }
    } else if (/^\/calibration\/[^/]+\/submit$/.test(url)) {
      const sessionId = url.split('/')[2]
      let step = calibrationSteps.get(sessionId)
      if (!step) {
        const result = await send(`/calibration-sessions/${encodeURIComponent(sessionId)}`)
        step = Number(result.data.currentStep)
      }
      config.url = `/calibration-sessions/${encodeURIComponent(sessionId)}/samples/${step}/responses`
      transform = async (value) => {
        const result = object(value)
        if (!result.completed) calibrationSteps.set(sessionId, Number(result.currentStep))
        return result
      }
    } else if (url === '/membership/redeem') {
      config.url = '/users/me/membership-redemptions'
    } else if (url === '/account/export') {
      config.url = '/users/me/data-export'
    } else if (url === '/account/delete') {
      config.url = '/users/me/deletion-requests'
    } else if (url === '/account/sessions' || url.startsWith('/account/sessions/')) {
      config.url = url.replace('/account/sessions', '/users/me/sessions')
    } else if (url === '/review/summary') {
      config.url = '/users/me/review-summary'
      config.params = undefined
    } else if (url === '/review/library') {
      config.url = '/review-cards'
    } else if (url === '/review/sessions/active') {
      config.url = '/review-sessions/current'
    } else if (url === '/review/sessions') {
      config.url = '/review-sessions'
    } else if (/^\/review\/sessions\/[^/]+\/advance$/.test(url)) {
      config.url = url
        .replace('/review/sessions/', '/review-sessions/')
        .replace(/\/advance$/, '/progressions')
      config.data = JSON.stringify({ eventId: crypto.randomUUID() })
      config.headers.set('Content-Type', 'application/json')
    } else if (/^\/review\/sessions\/[^/]+\/celebration-complete$/.test(url)) {
      config.url = url
        .replace('/review/sessions/', '/review-sessions/')
        .replace(/\/celebration-complete$/, '/completions')
    } else if (url.startsWith('/review/sessions/')) {
      config.url = url.replace('/review/sessions/', '/review-sessions/')
    } else if (url === '/lookup-summary') {
      config.url = '/users/me/lookup-summary'
    } else if (/^\/books\/[^/]+\/reader-agent$/.test(url)) {
      config.url = url.replace(/\/reader-agent$/, '/reading-explanations')
    } else if (/^\/books\/[^/]+\/chat$/.test(url)) {
      config.url = url.replace(/\/chat$/, '/chat-messages')
      config.timeout = 60000
    } else if (url === '/books/upload' && method === 'post') {
      config.url = '/books'
    } else if (/^\/books\/[^/]+\/generate$/.test(url) && method === 'post') {
      config.url = url.replace(/\/generate$/, '/imports')
      config.data = undefined
    } else if (/^\/books\/[^/]+\/status$/.test(url)) {
      config.url = url.replace(/\/status$/, '')
    } else if (url === '/books' && method === 'get') {
      const result = await send('/books', { page: 1, pageSize: 100 })
      // Fetch all pages because the existing catalog pages expect a complete array.
      const all = result.data.hasMore
        ? await pages('/books', {}, result.data as Page)
        : (result.data.items as Row[])
      return { ...result, config: original, data: all.map((row) => displayBook(row, userId)) }
    } else if (/^\/books\/[^/]+\/chapters$/.test(url) && method === 'get') {
      const bookPath = url.replace(/\/chapters$/, '')
      const [result, bookResult] = await Promise.all([send(url), send(bookPath)])
      const chapters = result.data as Row[]
      function mapToc(value: unknown): Row {
        const row = object(value)
        const chapter = chapters.find((item) => item.sourceHref === row.hrefNoHash)
        return {
          ...row,
          stageId: chapter?.id,
          sourceHref: row.hrefNoHash,
          children: Array.isArray(row.children) ? row.children.map(mapToc) : [],
        }
      }
      const storedToc = object(bookResult.data.metadata).toc
      const toc =
        Array.isArray(storedToc) && storedToc.length
          ? storedToc.map(mapToc)
          : chapters.map((chapter) => ({
              id: chapter.id,
              stageId: chapter.id,
              label: chapter.stageTitle,
              level: 1,
              children: [],
              sourceHref: chapter.sourceHref,
            }))
      return {
        ...result,
        config: original,
        data: { book: displayBook(bookResult.data, userId), chapters, toc },
      }
    } else if (/^\/stages\/[^/]+\/(structure|chunks)$/.test(url)) {
      config.url = url.replace('/stages/', '/chapters/').replace(/\/chunks$/, '/sentences')
    } else if (url === '/library/books' && method === 'get') {
      const uploads = object(config.params).view === 'uploads'
      const rows = await pages(
        uploads ? '/books' : '/users/me/library/books',
        uploads ? { view: 'uploads' } : {},
      )
      const books = uploads
        ? rows.filter((row) => row.ownerId === userId).map((row) => displayBook(row, userId))
        : await Promise.all(
            rows.map(async (row) => {
              if (row.book)
                return { ...displayBook(object(row.book), userId), shelfState: row.shelfState }
              const result = await send(`/books/${encodeURIComponent(String(row.bookId))}`)
              return { ...displayBook(result.data, userId), shelfState: row.shelfState }
            }),
          )
      return { status: 200, statusText: 'OK', headers: {}, config: original, data: { books } }
    } else if (/^\/library\/[^/]+$/.test(url) && ['post', 'delete'].includes(method)) {
      config.url = url.replace('/library/', '/users/me/library/books/')
      if (method === 'post') {
        config.method = 'put'
        config.data = JSON.stringify({ shelfState: 'saved' })
        config.headers.set('Content-Type', 'application/json')
      }
    } else if (url === '/reading-progress') {
      const params = method === 'get' ? object(config.params) : data
      const resource = `/books/${encodeURIComponent(String(params.bookId))}/chapters/${encodeURIComponent(String(params.chapterId))}`
      config.params = undefined
      if (method === 'get') {
        config.url = `${resource}/progress`
        transform = async (value) => ({ progress: value })
      } else {
        config.url = `${resource}/reading-events`
        config.method = 'post'
        config.data = JSON.stringify({
          eventId: crypto.randomUUID(),
          currentParagraphIndex: params.currentParagraphIndex,
          completedParagraphs: params.completedParagraphs,
          totalParagraphs: params.totalParagraphs,
          readingDurationMs: params.readingDurationMs ?? 0,
        })
      }
    } else if (url === '/book-notes' && method === 'get') {
      const params = object(config.params)
      config.params = { ...params, pageSize: Math.min(Number(params.pageSize ?? 20), 100) }
      transform = async (value) => {
        const page = value as Page
        return { ...page, items: await Promise.all(page.items.map(note)) }
      }
    } else if (
      (url === '/book-notes' && method === 'post') ||
      (/^\/book-notes\/[^/]+$/.test(url) && method === 'patch')
    ) {
      transform = async (value) => ({ item: await note(object(value)) })
    }
    try {
      const response = await wire(config)
      const calibrationResponse = config.url?.match(
        /^\/calibration-sessions\/([^/]+)\/samples\/\d+\/responses$/,
      )
      if (calibrationResponse && !response.data.completed) {
        calibrationSteps.set(
          decodeURIComponent(calibrationResponse[1]),
          Number(response.data.currentStep),
        )
      }
      // Keep the canonical request config on retries of reading events so the event ID is reused.
      const responseConfig = /\/(reading-events|progressions|responses)$/.test(config.url ?? '')
        ? config
        : original
      return { ...response, config: responseConfig, data: await transform(response.data) }
    } catch (error) {
      if (error instanceof AxiosError) {
        if (url === '/reading-progress' && method === 'get' && error.response?.status === 404) {
          return {
            ...error.response,
            status: 200,
            statusText: 'OK',
            config: original,
            data: { progress: null },
          }
        }
        error.config = /\/(reading-events|progressions|responses)$/.test(config.url ?? '')
          ? config
          : original
      }
      throw error
    }
  }
}
