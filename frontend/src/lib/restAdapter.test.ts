import assert from 'node:assert/strict'
import { test } from 'node:test'
import { AxiosError, AxiosHeaders, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { createRestAdapter } from './restAdapter'

function config(url: string, method = 'get', data?: unknown): InternalAxiosRequestConfig {
  return {
    url,
    method,
    data: data === undefined ? undefined : JSON.stringify(data),
    headers: new AxiosHeaders(),
  }
}
function response(request: InternalAxiosRequestConfig, data: unknown) {
  return { status: 200, statusText: 'OK', headers: {}, config: request, data: JSON.stringify(data) }
}

test('embedded note metadata avoids a book and chapter request for every row', async () => {
  const calls: string[] = []
  const adapter = createRestAdapter(async (request) => {
    calls.push(request.url!)
    return response(request, {
      items: [
        {
          id: 'note',
          bookId: 'book',
          chapterId: 'chapter',
          book: { id: 'book', title: 'Title', author: 'Author' },
          chapter: { id: 'chapter', title: 'Chapter' },
        },
      ],
      page: 1,
      pageSize: 20,
      total: 1,
      hasMore: false,
    })
  })
  const result = await adapter(config('/book-notes'))
  assert.equal(result.data.items[0].book.title, 'Title')
  assert.deepEqual(calls, ['/book-notes'])
})
test('library uses batched details from the paginated REST response', async () => {
  const calls: string[] = []
  const adapter = createRestAdapter(async (request) => {
    calls.push(request.url!)
    return response(request, {
      items: [
        {
          bookId: 'book',
          shelfState: 'saved',
          book: { id: 'book', title: 'Title', status: 'completed', ownerId: 'reader' },
        },
      ],
      page: 1,
      pageSize: 100,
      total: 1,
      hasMore: false,
    })
  })
  const result = await adapter({
    ...config('/library/books'),
    _authUserId: 'reader',
  } as InternalAxiosRequestConfig)
  assert.equal(result.data.books[0].title, 'Title')
  assert.deepEqual(calls, ['/users/me/library/books'])
})
test('upload listing sends the owner filter to the backend', async () => {
  const adapter = createRestAdapter(async (request) => {
    assert.equal(request.url, '/books')
    assert.equal(request.params.view, 'uploads')
    return response(request, { items: [], page: 1, pageSize: 100, total: 0, hasMore: false })
  })
  const request = config('/library/books')
  request.params = { view: 'uploads' }
  await adapter(request)
})
test('note creation consumes embedded metadata without replay-prone follow-up requests', async () => {
  const calls: string[] = []
  const adapter = createRestAdapter(async (request) => {
    calls.push(request.method + ' ' + request.url)
    return response(request, {
      id: 'note',
      book: { id: 'book', title: 'Title', author: 'Author' },
      chapter: { id: 'chapter', title: 'Chapter' },
    })
  })
  const result = await adapter(
    config('/book-notes', 'post', {
      bookId: 'book',
      chapterId: 'chapter',
      quote: 'Synthetic quote',
    }),
  )
  assert.equal(result.data.item.id, 'note')
  assert.deepEqual(calls, ['post /book-notes'])
})

test('book chat allows bounded embedding batches before the answer provider completes', async () => {
  const transport: AxiosAdapter = async (request) => {
    assert.equal(request.url, '/books/book-one/chat-messages')
    assert.equal(request.timeout, 60000)
    return response(request, { answer: 'A quiet river.', sources: [] })
  }
  const request = {
    ...config('/books/book-one/chat', 'post', { question: 'Where are they?' }),
    timeout: 15000,
  }
  const result = await createRestAdapter(transport)(request)
  assert.equal(result.data.answer, 'A quiet river.')
  assert.equal(request.timeout, 15000)
})
test('auth uses REST wire paths and leaves caller config intact', async () => {
  const request = config('/auth/login', 'post', {
    email: 'reader@example.com',
    password: 'test-only',
  })
  const transport: AxiosAdapter = async (value) => {
    assert.equal(value.url, '/sessions')
    assert.equal(value.method, 'post')
    return response(value, { user: { id: 'reader' }, accessToken: 'test-token' })
  }
  const result = await createRestAdapter(transport)(request)
  assert.equal(result.data.user.id, 'reader')
  assert.equal(request.url, '/auth/login')
})
test('catalog decodes raw JSON and fetches all pages', async () => {
  const calls: number[] = []
  const transport: AxiosAdapter = async (request) => {
    const page = request.params.page as number
    calls.push(page)
    return response(request, {
      items: [{ id: `book-${page}`, ownerId: 'reader', title: 'Book' }],
      page,
      pageSize: 100,
      total: 2,
      hasMore: page === 1,
    })
  }
  const request = { ...config('/books'), _authUserId: 'reader' }
  const result = await createRestAdapter(transport)(request)
  assert.equal(result.data.length, 2)
  assert.equal(result.data[0].isUploader, true)
  assert.deepEqual(calls, [1, 2])
})
test('chapter collection provides the view model expected by reader pages', async () => {
  const transport: AxiosAdapter = async (request) =>
    request.url?.endsWith('/chapters')
      ? response(request, [{ id: 'chapter', stageTitle: 'Chapter 1', content: 'Hello.' }])
      : response(request, { id: 'book', title: 'Book' })
  const result = await createRestAdapter(transport)(config('/books/book/chapters'))
  assert.equal(result.data.book.id, 'book')
  assert.equal(result.data.chapters[0].content, 'Hello.')
  assert.equal(result.data.toc[0].stageId, 'chapter')
})
test('authentication retry reuses the reading event ID', async () => {
  let first = true
  const events: string[] = []
  const transport: AxiosAdapter = async (request) => {
    assert.equal(request.method, 'post')
    assert.equal(request.url, '/books/book/chapters/chapter/reading-events')
    events.push(JSON.parse(request.data as string).eventId as string)
    if (first) {
      first = false
      throw new AxiosError('expired', 'ERR_BAD_REQUEST', request, undefined, {
        ...response(request, {}),
        status: 401,
      })
    }
    return response(request, { totalReadingMs: 500 })
  }
  const adapter = createRestAdapter(transport)
  let retry: InternalAxiosRequestConfig | undefined
  try {
    await adapter(
      config('/reading-progress', 'put', {
        bookId: 'book',
        chapterId: 'chapter',
        currentParagraphIndex: 0,
        completedParagraphs: 0,
        totalParagraphs: 2,
        readingDurationMs: 500,
      }),
    )
  } catch (error) {
    assert.ok(error instanceof AxiosError)
    retry = error.config
  }
  assert.ok(retry)
  await adapter(retry)
  assert.equal(events.length, 2)
  assert.equal(events[0], events[1])
})

test('additional modules use REST resource paths', async () => {
  const seen: string[] = []
  const adapter = createRestAdapter(async (request) => {
    seen.push(`${request.method} ${request.url}`)
    if (request.url === '/reading-interventions')
      assert.deepEqual(JSON.parse(request.data as string), {
        surroundingText: 'Context',
        stuckChunk: 'Target',
      })
    return response(request, {})
  })
  await adapter(config('/imports/url/inspect', 'post', { url: 'https://example.com' }))
  await adapter(
    config('/imports/url', 'post', { url: 'https://example.com', rightsAttested: true }),
  )
  await adapter(config('/imports/catalog', 'post', { providerId: 'googlebooks' }))
  await adapter(config('/imports/job'))
  await adapter(
    config('/module2/intervene', 'post', { surrounding_text: 'Context', stuck_chunk: 'Target' }),
  )
  await adapter(config('/account/export'))
  await adapter(
    config('/account/delete', 'post', { confirmation: 'DELETE', password: 'test-only' }),
  )
  assert.deepEqual(seen, [
    'post /source-inspections',
    'post /remote-imports',
    'post /catalog-imports',
    'get /remote-imports/job',
    'post /reading-interventions',
    'get /users/me/data-export',
    'post /users/me/deletion-requests',
  ])
})

test('dictionary upload keeps multipart body while changing method', async () => {
  const data = new FormData()
  data.append('file', new Blob(['synthetic']), 'dictionary.mdx')
  const request = { ...config('/dictionaries', 'post'), data }
  const adapter = createRestAdapter(async (value) => {
    assert.equal(value.method, 'put')
    assert.equal(value.url, '/users/me/dictionary')
    assert.equal(value.data, data)
    return response(value, { dictionary: { id: 'dictionary' } })
  })
  await adapter(request)
  assert.equal(request.method, 'post')
})

test('EPUB navigation preserves hierarchy and resolves chapter IDs', async () => {
  const adapter = createRestAdapter(async (request) =>
    response(
      request,
      request.url?.endsWith('/chapters')
        ? [
            { id: 'chapter-one', sourceHref: 'OPS/one.xhtml' },
            { id: 'chapter-two', sourceHref: 'OPS/two.xhtml' },
          ]
        : {
            id: 'book',
            metadata: {
              toc: [
                {
                  id: 'part',
                  label: 'Part One',
                  hrefNoHash: 'OPS/one.xhtml',
                  children: [
                    { id: 'nested', hrefNoHash: 'OPS/two.xhtml', fragment: 'start', children: [] },
                  ],
                },
              ],
            },
          },
    ),
  )
  const result = await adapter(config('/books/book/chapters'))
  assert.equal(result.data.toc[0].stageId, 'chapter-one')
  assert.equal(result.data.toc[0].children[0].stageId, 'chapter-two')
  assert.equal(result.data.toc[0].children[0].fragment, 'start')
})
