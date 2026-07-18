import { readFile } from "node:fs/promises";

const NOTION_TOKEN = process.env.NOTION_TOKEN;
const NOTION_DATA_SOURCE_ID = process.env.NOTION_DATA_SOURCE_ID;
const NOTION_VERSION = "2026-03-11";
const REQUEST_TIMEOUT_MS = 15_000;
const MAX_RETRIES = 3;
const MAX_PAGE_LOOKUP_ATTEMPTS = 6;
const PAGE_LOOKUP_DELAY_MS = 5_000;
const RETRYABLE_STATUS_CODES = new Set([409, 429, 500, 502, 503, 504]);
const ISSUE_BRANCH_PATTERN =
  /^(?:feat|fix|chore|refactor|test|docs|style|perf|build|ci|hotfix)\/(\d+)(?:[-/].+)?$/;

if (!NOTION_TOKEN) {
  throw new Error("NOTION_TOKEN이 설정되지 않았습니다.");
}

if (!NOTION_DATA_SOURCE_ID) {
  throw new Error("NOTION_DATA_SOURCE_ID가 설정되지 않았습니다.");
}

if (!process.env.GITHUB_EVENT_PATH) {
  throw new Error("GITHUB_EVENT_PATH가 없습니다.");
}

if (!process.env.GITHUB_REPOSITORY) {
  throw new Error("GITHUB_REPOSITORY 값이 없습니다.");
}

const wait = (milliseconds) =>
  new Promise((resolve) => setTimeout(resolve, milliseconds));

function getRetryDelay(retryAfterHeader, attempt) {
  const retryAfter = Number.parseInt(retryAfterHeader ?? "", 10);
  const jitter = Math.floor(Math.random() * 250);

  if (Number.isFinite(retryAfter) && retryAfter >= 0) {
    return retryAfter * 1_000 + jitter;
  }

  return Math.min(1_000 * 2 ** attempt, 8_000) + jitter;
}

async function requestNotionOnce(path, options = {}) {
  const response = await fetch(`https://api.notion.com/v1${path}`, {
    ...options,
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    headers: {
      Authorization: `Bearer ${NOTION_TOKEN}`,
      "Notion-Version": NOTION_VERSION,
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  const responseText = await response.text();
  let responseBody = {};

  if (responseText) {
    try {
      responseBody = JSON.parse(responseText);
    } catch {
      responseBody = { raw: responseText };
    }
  }

  if (!response.ok) {
    const error = new Error(
      `Notion API 오류 (${response.status}): ${JSON.stringify(responseBody)}`,
    );
    error.status = response.status;
    error.retryAfter = response.headers.get("retry-after");
    throw error;
  }

  return responseBody;
}

async function requestNotion(path, options = {}) {
  const method = (options.method ?? "GET").toUpperCase();

  for (let attempt = 0; attempt <= MAX_RETRIES; attempt += 1) {
    try {
      return await requestNotionOnce(path, options);
    } catch (error) {
      const status = error?.status;
      const isRetryableResponse = RETRYABLE_STATUS_CODES.has(status);
      const isRetryableNetworkError = status === undefined;
      const canRetry =
        attempt < MAX_RETRIES &&
        (isRetryableNetworkError || isRetryableResponse);

      if (!canRetry) {
        throw error;
      }

      const delay = getRetryDelay(error.retryAfter, attempt);
      console.warn(
        `Notion API 재시도 (${attempt + 1}/${MAX_RETRIES}, status ${status ?? "network"}): ${method} ${path}`,
      );
      await wait(delay);
    }
  }

  throw new Error(`Notion API 최대 재시도 횟수 초과: ${method} ${path}`);
}

async function findIssuePage(issueKey) {
  for (let attempt = 1; attempt <= MAX_PAGE_LOOKUP_ATTEMPTS; attempt += 1) {
    const queryResult = await requestNotion(
      `/data_sources/${NOTION_DATA_SOURCE_ID}/query`,
      {
        method: "POST",
        body: JSON.stringify({
          filter: {
            property: "GitHub Issue Key",
            rich_text: {
              equals: issueKey,
            },
          },
          page_size: 1,
        }),
      },
    );

    const page = queryResult.results?.[0];

    if (page) {
      return page;
    }

    if (attempt < MAX_PAGE_LOOKUP_ATTEMPTS) {
      console.warn(
        `Notion 항목을 찾지 못해 재조회합니다 (${attempt}/${MAX_PAGE_LOOKUP_ATTEMPTS}): ${issueKey}`,
      );
      await wait(PAGE_LOOKUP_DELAY_MS);
    }
  }

  return null;
}

async function main() {
  const event = JSON.parse(
    await readFile(process.env.GITHUB_EVENT_PATH, "utf8"),
  );

  if (event.ref_type !== "branch") {
    console.log(`브랜치 생성 이벤트가 아니므로 건너뜁니다: ${event.ref_type}`);
    return;
  }

  const branchName = event.ref;
  const issueNumber = branchName?.match(ISSUE_BRANCH_PATTERN)?.[1];

  if (!issueNumber) {
    console.log(`이슈 브랜치 형식이 아니므로 건너뜁니다: ${branchName}`);
    return;
  }

  const issueKey = `${process.env.GITHUB_REPOSITORY}#${issueNumber}`;
  const issuePage = await findIssuePage(issueKey);

  if (!issuePage) {
    throw new Error(
      `브랜치에 대응하는 Notion 항목을 찾을 수 없습니다: ${branchName} (${issueKey})`,
    );
  }

  const currentStatus = issuePage.properties?.["상태"]?.status?.name;

  if (currentStatus === "완료") {
    console.log(`이미 완료된 이슈이므로 상태를 유지합니다: ${issueKey}`);
    return;
  }

  if (currentStatus === "진행 중") {
    console.log(`이미 진행 중인 이슈입니다: ${issueKey}`);
    return;
  }

  await requestNotion(`/pages/${issuePage.id}`, {
    method: "PATCH",
    body: JSON.stringify({
      properties: {
        상태: {
          status: {
            name: "진행 중",
          },
        },
      },
    }),
  });

  console.log(`Notion 상태를 진행 중으로 변경했습니다: ${issueKey}`);
}

await main();
