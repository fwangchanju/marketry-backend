const express = require('express')
const fs = require('fs')
const { chromium } = require('playwright')

const app = express()
app.use(express.json())

const PORT = process.env.PORT || 3000
const BASE_URL = process.env.CAPTURE_URL || 'http://localhost:5173'
const CAPTURE_USER = process.env.CAPTURE_USER || ''
const CAPTURE_PASS = process.env.CAPTURE_PASS || ''

const USER_DATA_DIR = '/tmp/renderer-profile'
const CLEANUP_TIMEOUT_MS = 10000
const MAX_QUEUE_WAIT_MS = 30000
const MAX_CONSECUTIVE_FAILURES = 3
const RECYCLE_AFTER_CAPTURES = 200
const SHUTDOWN_GRACE_MS = 5000
const CAPTURE_READY_TIMEOUT_MS = 30000
const CAPTURE_STATE_MAX_LENGTH = 400
const CAPTURE_TOKEN_HEADER = 'X-Capture-Token'
const SHARED_MAP_CAPTURE_SELECTOR = "[data-captureid='map-mobile-capture']"

// 죽은 Chromium이 남긴 SingletonLock 등이 재시작 후에도 남아 있지 않도록, 기동 시 한 번 지운다 —
// process.exit(1) + restart: always는 컨테이너 파일시스템을 보존하므로 이 정리가 없으면 다음
// launchPersistentContext가 그 락 파일 때문에 실패할 수 있다.
fs.rmSync(USER_DATA_DIR, { recursive: true, force: true })

let context = null
let contextState = 'not_created' // 'not_created' | 'alive' | 'dead'
let launchPromise = null
let consecutiveFailures = 0
let capturesSinceRecycle = 0
let totalCaptureCount = 0
let queueTail = Promise.resolve()
let shutdownRequested = false

// promise를 최대 ms까지만 기다린다. 그 안에 안 끝나면(성공/실패 무관) 기다리지 않고 넘어간다 — 정리
// 작업(페이지·컨텍스트 닫기)이 응답을 멈춘 Chromium 때문에 무기한 대기하는 것을 막기 위해서다.
function withTimeout(promise, ms) {
  return Promise.race([promise.catch(() => {}), new Promise((resolve) => setTimeout(resolve, ms))])
}

// 종료가 예약된 뒤에는 응답이 실제로 나간 다음에 프로세스를 끝낸다. 먼저 죽으면 백엔드는 500을 받지
// 못하고 read timeout(90초)까지 매달린다 — 재기동보다 "빨리 실패를 알리는 것"이 급하다. 응답이 끝내
// 안 나가는 경우를 대비해 상한도 같이 건다.
function exitAfterResponse(res) {
  if (!shutdownRequested) {
    return
  }
  const exit = () => process.exit(1)
  res.on('finish', exit)
  res.on('close', exit)
  setTimeout(exit, SHUTDOWN_GRACE_MS).unref()
}

app.get('/health', (req, res) => {
  const healthy = contextState !== 'dead' && !shutdownRequested
  res.status(healthy ? 200 : 503).json({
    status: healthy ? 'ok' : 'degraded',
    contextState,
    consecutiveFailures,
    totalCaptureCount,
  })
})

// path: BASE_URL 뒤에 붙일 프론트 경로(쿼리 포함 가능), selector: 캡처할 요소들의 CSS 셀렉터,
// token: 있으면 이번 캡처에만 소유자 인증 헤더를 실어 보낸다(선택 필드 — 없는 백엔드도 그대로 동작),
// rowSelector: 있으면 그 요소들의 data-capture-row 값을 이미지와 같은 화면에서 함께 읽어 rows로 돌려준다(선택 필드).
app.post('/capture', (req, res) => {
  const { path, selector, token, rowSelector } = req.body

  if (!path || !selector) {
    res.status(400).json({ error: 'path와 selector는 필수입니다' })
    return
  }

  // 요청을 직렬화한다 — 램이 넉넉하지 않은 머신이라 페이지를 동시에 여러 개 열면 압박이 크다.
  const enqueuedAt = Date.now()
  const turn = queueTail.then(() => {
    if (Date.now() - enqueuedAt > MAX_QUEUE_WAIT_MS) {
      const error = new Error('대기열 초과')
      error.isQueueTimeout = true
      throw error
    }
    return handleCapture(path, selector, token, rowSelector)
  })
  // 이번 요청이 실패해도 다음 요청이 대기열에서 계속 이어지도록, 체인 자체는 항상 성공으로 막는다.
  queueTail = turn.then(
    () => {},
    () => {},
  )

  turn.then(
    ({ images, rows }) => {
      onCaptureSucceeded()
      maybeRecycleContext()
      res.json({ images, rows })
      exitAfterResponse(res)
    },
    (err) => {
      if (err.isQueueTimeout) {
        console.error('[renderer] 캡처 요청이 대기열에서 시간 초과:', path, err.message)
        res.status(503).json({ error: err.message })
        exitAfterResponse(res)
        return
      }
      console.error('[renderer] 캡처 오류:', path, err.message)
      res.status(500).json({ error: err.message })
      exitAfterResponse(res)
    },
  )
})

// 준비 표식을 끝내 못 받았을 때 어느 단계에서 멈췄는지 알 수 있도록 API 호출과 페이지 오류를 모은다.
// 주소는 경로만 남긴다. 토큰이 든 헤더와 쿼리는 기록하지 않는다.
function trackPageActivity(page, sameOrigin) {
  const pending = new Map()
  const failed = []
  const pageErrors = []
  const apiPath = (request) => {
    const url = new URL(request.url())
    return url.origin === sameOrigin && url.pathname.startsWith('/api/') ? url.pathname : null
  }
  page.on('request', (request) => {
    const path = apiPath(request)
    if (path) pending.set(request, path)
  })
  page.on('requestfinished', async (request) => {
    const path = pending.get(request)
    pending.delete(request)
    if (!path) return
    const response = await request.response().catch(() => null)
    if (response && response.status() >= 400) failed.push(`${path} ${response.status()}`)
  })
  page.on('requestfailed', (request) => {
    const path = pending.get(request)
    pending.delete(request)
    if (path) failed.push(`${path} 연결 실패`)
  })
  page.on('pageerror', (error) => pageErrors.push(String(error.message).split('\n')[0].slice(0, 80)))
  return { pending, failed, pageErrors }
}

async function describeCaptureState(page, selector, activity) {
  const screen = await withTimeout(
    page.evaluate((targetSelector) => {
      const target = document.querySelector(targetSelector)
      const layouts = [...document.querySelectorAll('[data-heatmap-layout-ready]')].map(
        (el) => `${el.getAttribute('data-heatmap-layout-ready')}:${el.clientWidth}x${el.clientHeight}`,
      )
      return {
        target: target ? `있음 ready=${target.getAttribute('data-capture-ready')}` : '없음',
        layouts: layouts.join(',') || '없음',
      }
    }, selector),
    CLEANUP_TIMEOUT_MS,
  )
  const parts = [
    `틀 ${screen?.target ?? '확인 불가'}`,
    `배치 ${screen?.layouts ?? '확인 불가'}`,
    `API 실패 ${activity.failed.join(', ') || '없음'}`,
    `API 대기 ${[...activity.pending.values()].join(', ') || '없음'}`,
    `페이지 오류 ${activity.pageErrors.join(', ') || '없음'}`,
  ]
  return parts.join(' / ').slice(0, CAPTURE_STATE_MAX_LENGTH)
}

async function handleCapture(path, selector, token, rowSelector) {
  const page = await acquirePage()

  try {
    const activity = trackPageActivity(page, new URL(BASE_URL).origin)
    const captureUrl = new URL(path, 'http://localhost')
    const useSharedMapCapture = selector === SHARED_MAP_CAPTURE_SELECTOR
      && captureUrl.pathname.startsWith('/map/')
      && captureUrl.searchParams.get('capture') === 'mobile'

    // 앱의 모바일 캡처는 Chrome 기기 배율 2에서 DOM을 래스터화한다.
    // Telegram 렌더러도 같은 기기 배율로 그리되, CSS viewport와 프론트의 2560px 출력 scale은 유지한다.
    if (useSharedMapCapture) {
      const cdp = await page.context().newCDPSession(page)
      await cdp.send('Emulation.setDeviceMetricsOverride', {
        width: 1920,
        height: 1080,
        deviceScaleFactor: 2,
        mobile: false,
      })
    }

    if (CAPTURE_USER && CAPTURE_PASS) {
      const credentials = Buffer.from(`${CAPTURE_USER}:${CAPTURE_PASS}`).toString('base64')
      await page.setExtraHTTPHeaders({ Authorization: `Basic ${credentials}` })
    }

    if (token) {
      await registerCaptureTokenRoute(page, token)
    }

    // 앱은 API 요청 등으로 네트워크가 계속 사용될 수 있다. 페이지 이동은 DOM 준비까지만 기다리고,
    // 실제 데이터 준비는 아래 캡처 대상의 data-capture-ready 표식으로 확인한다.
    await page.goto(BASE_URL + path, { waitUntil: 'domcontentloaded', timeout: 30000 })
    // 프론트가 데이터 로딩을 마치고 캡처 대상 엘리먼트에 data-capture-ready="true"를 붙이면 그때 캡처한다
    // (고정 딜레이로 "다 그려졌겠지" 추측하던 방식 대체).
    try {
      await page.waitForSelector(`${selector}[data-capture-ready="true"]`, { timeout: CAPTURE_READY_TIMEOUT_MS })
    } catch (err) {
      err.message = `${err.message}\n[캡처 상태] ${await describeCaptureState(page, selector, activity)}`
      throw err
    }

    // 캡처 대상의 레이아웃이 준비됐어도 웹폰트는 아직 로딩 중일 수 있다. 정기 발송처럼
    // 렌더러가 막 재시작된 직후에는 fallback 폰트 폭으로 말줄임이 계산되어 텍스트가 잘릴 수 있다.
    // 현재 페이지에서 사용 중인 폰트 로딩과 두 프레임의 재배치를 기다린 뒤 캡처한다.
    await page.evaluate(async () => {
      await document.fonts.ready
      await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    })

    const sections = await page.$$(selector)
    const images = []
    for (let i = 0; i < sections.length; i++) {
      // 설정창 이동 버튼이 본문 경계에 걸쳐 있어 캡처 순간에는 설정창 전체를 숨긴다.
      let buffer
      if (useSharedMapCapture) {
        await page.waitForFunction(() => typeof window.marketryCaptureMap === 'function', null, { timeout: CAPTURE_READY_TIMEOUT_MS })
        const dataUrl = await page.evaluate(() => window.marketryCaptureMap())
        if (!dataUrl.startsWith('data:image/png;base64,')) throw new Error('지도 캡처 결과가 올바르지 않습니다')
        buffer = Buffer.from(dataUrl.slice('data:image/png;base64,'.length), 'base64')
      } else {
        buffer = await sections[i].screenshot({
          type: 'png',
          style: '[data-settings-sidebar-shell] { visibility: hidden !important; }',
        })
      }
      images.push({ name: `section-${i}`, data: buffer.toString('base64') })
    }

    // 이미지에 그려진 숫자를 그대로 캡션에 쓰도록, 같은 화면에서 행 값을 읽는다. 값이 없는 요소는 뺀다.
    const rows = rowSelector
      ? await page.$$eval(rowSelector, (elements) => elements.map((element) => element.getAttribute('data-capture-row')).filter((row) => row !== null))
      : []

    return { images, rows }
  } finally {
    if (token) {
      // 이 페이지에만 건 라우팅이므로 컨텍스트 전체 쿠키·헤더에는 소유자 인증이 남지 않는다. page.close()가
      // 라우팅도 함께 폐기하지만, 다음 캡처에 절대 새어 나가지 않도록 명시적으로도 해제한다.
      await withTimeout(page.unrouteAll(), CLEANUP_TIMEOUT_MS)
    }
    await withTimeout(page.close(), CLEANUP_TIMEOUT_MS)
  }
}

// 캡처 대상 page에만 라우팅을 걸어, 같은 출처(BASE_URL)의 /api/ 요청에만 소유자 캡처 토큰 헤더를 얹는다.
// 컨텍스트는 여러 캡처가 공유하므로(launchPersistentContext) 컨텍스트 전체 쿠키·헤더에는 절대 넣지 않는다.
// 토큰은 로그에 남기지 않는다.
async function registerCaptureTokenRoute(page, token) {
  const sameOrigin = new URL(BASE_URL).origin
  await page.route(
    (url) => url.origin === sameOrigin && url.pathname.startsWith('/api/'),
    async (route) => {
      await route.continue({ headers: { ...route.request().headers(), [CAPTURE_TOKEN_HEADER]: token } })
    },
  )
}

// 매 요청 시작에 컨텍스트가 살아 있는지 확인한다 — 없으면(최초 요청, 이전 실패로 버려짐, 재활용 직후)
// 새로 띄운다. 죽어 있는 채로 쓰려다 실패하면 discard하고 다음 요청이 다시 띄우게 한다(이번 요청
// 안에서 재시도하지 않는다 — 무한 재시도 루프를 막기 위해서다).
async function acquirePage() {
  try {
    const activeContext = await ensureContext()
    return await activeContext.newPage()
  } catch (err) {
    await onBrowserFailure(err)
    throw err
  }
}

async function ensureContext() {
  if (context) {
    return context
  }
  // 동시 요청이 두 번 띄우지 않도록 생성 프로미스를 공유한다.
  if (!launchPromise) {
    launchPromise = launchContext().finally(() => {
      launchPromise = null
    })
  }
  return launchPromise
}

// 실패 시 여기서 onBrowserFailure를 부르지 않는다 — 이 함수는 항상 ensureContext를 거쳐
// acquirePage의 catch 안에서 호출되므로, 여기서도 부르면 실패 하나가 두 번 집계된다.
async function launchContext() {
  const newContext = await chromium.launchPersistentContext(USER_DATA_DIR, {
    headless: true,
    viewport: { width: 1920, height: 1080 },
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'],
  })

  // launchPersistentContext는 빈 about:blank 페이지를 자동으로 하나 만든다 — 재사용하지 않고 닫는다.
  for (const initialPage of newContext.pages()) {
    await withTimeout(initialPage.close(), CLEANUP_TIMEOUT_MS)
  }

  context = newContext
  contextState = 'alive'
  capturesSinceRecycle = 0
  return context
}

// handleCapture가 page.close()까지 전부 끝낸 뒤에만 부른다 — 그 전에 재활용이 컨텍스트를 닫아버리면
// 아직 안 닫힌 이번 요청의 page.close()가 실패한다.
function onCaptureSucceeded() {
  consecutiveFailures = 0
  totalCaptureCount += 1
  capturesSinceRecycle += 1
}

// 브라우저·컨텍스트 계열 실패(launchPersistentContext 실패, newPage 실패)만 여기로 온다. page.goto/
// waitForSelector 타임아웃이나 path·selector 누락은 프론트·요청 문제라 여기서 세지 않는다 — 재시작으로
// 안 고쳐지는 실패까지 카운트에 넣으면 프론트 장애가 렌더러 재시작 폭주로 번진다.
async function onBrowserFailure(err) {
  const failedContext = context
  context = null
  contextState = 'dead'
  consecutiveFailures += 1
  console.error(`[renderer] 브라우저/컨텍스트 실패 (연속 ${consecutiveFailures}회): ${err.message}`)

  if (failedContext) {
    await withTimeout(failedContext.close(), CLEANUP_TIMEOUT_MS)
  }

  if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
    console.error(`[renderer] 연속 실패 ${consecutiveFailures}회 — 이번 응답을 보낸 뒤 재기동한다`)
    shutdownRequested = true
  }
}

// 200회마다 컨텍스트를 닫고 다시 띄운다 — 장기 실행 Chromium의 완만한 누수에 대한 보험. 실패가 아니므로
// contextState는 'dead'가 아니라 최초 기동과 같은 'not_created'로 되돌린다.
function maybeRecycleContext() {
  if (capturesSinceRecycle < RECYCLE_AFTER_CAPTURES) {
    return
  }
  const oldContext = context
  context = null
  contextState = 'not_created'
  capturesSinceRecycle = 0
  console.log(`[renderer] 캡처 ${RECYCLE_AFTER_CAPTURES}회 도달 — 컨텍스트를 재활용한다`)
  if (oldContext) {
    withTimeout(oldContext.close(), CLEANUP_TIMEOUT_MS)
  }
}

app.listen(PORT, () => {
  console.log(`marketry-renderer 시작 — port ${PORT}, base: ${BASE_URL}`)
})
