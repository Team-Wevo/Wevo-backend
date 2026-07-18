import { readFile } from "node:fs/promises";

const NOTION_TOKEN = process.env.NOTION_TOKEN;
const NOTION_DATA_SOURCE_ID = process.env.NOTION_DATA_SOURCE_ID;
const REQUEST_TIMEOUT_MS = 15_000;
const MAX_RETRIES = 3;
const RETRYABLE_STATUS_CODES = new Set([409, 429, 500, 502, 503, 504]);
const NOTION_VERSION = "2026-03-11";

if (!NOTION_TOKEN) {
  throw new Error("NOTION_TOKEN이 설정되지 않았습니다.");
}

if (!NOTION_DATA_SOURCE_ID) {
  throw new Error("NOTION_DATA_SOURCE_ID가 설정되지 않았습니다.");
}

if (!process.env.GITHUB_EVENT_PATH) {
  throw new Error("GITHUB_EVENT_PATH가 없습니다.");
}

const event = JSON.parse(await readFile(process.env.GITHUB_EVENT_PATH, "utf8"));

const issue = event.issue;
const action = event.action;
const repository = process.env.GITHUB_REPOSITORY;

if (!issue) {
  throw new Error("GitHub Issue 이벤트 정보가 없습니다.");
}

if (!repository) {
  throw new Error("GITHUB_REPOSITORY 값이 없습니다.");
}

const repositoryName = repository.split("/").at(-1).toLowerCase();

const part = repositoryName.includes("frontend")
  ? "FE"
  : repositoryName.includes("backend")
    ? "BE"
    : "PM&DS";

const issueKey = `${repository}#${issue.number}`;

const assignees = (issue.assignees ?? [])
  .map((assignee) => assignee.login)
  .join(", ");

const labels = (issue.labels ?? [])
  .map((label) => {
    const name = typeof label === "string" ? label : label.name;

    if (!name) {
      return null;
    }

    // Notion의 선택 옵션 이름에는 쉼표를 사용할 수 없으므로 전각 쉼표로 변경합니다.
    return {
      name: name.replaceAll(",", "，").slice(0, 100),
    };
  })
  .filter(Boolean);

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
      responseBody = {
        raw: responseText,
      };
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
  const isCreatePageRequest = method === "POST" && path === "/pages";

  for (let attempt = 0; attempt <= MAX_RETRIES; attempt += 1) {
    try {
      return await requestNotionOnce(path, options);
    } catch (error) {
      const status = error?.status;
      const canRetryCreate = status === 409 || status === 429;
      const isRetryableResponse = RETRYABLE_STATUS_CODES.has(status);
      const isRetryableNetworkError =
        status === undefined && !isCreatePageRequest;
      const canRetry =
        attempt < MAX_RETRIES &&
        (isRetryableNetworkError ||
          (isRetryableResponse && (!isCreatePageRequest || canRetryCreate)));

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

function buildCommonProperties() {
  return {
    작업명: {
      title: [
        {
          type: "text",
          text: {
            content: `#${issue.number} ${issue.title}`,
          },
        },
      ],
    },

    "담당 파트": {
      select: {
        name: part,
      },
    },

    Label: {
      multi_select: labels,
    },

    "GitHub 담당자": {
      rich_text: assignees
        ? [
            {
              type: "text",
              text: {
                content: assignees,
              },
            },
          ]
        : [],
    },

    "GitHub Issue": {
      url: issue.html_url,
    },

    "GitHub Issue Key": {
      rich_text: [
        {
          type: "text",
          text: {
            content: issueKey,
          },
        },
      ],
    },
  };
}

// 같은 GitHub Issue Key를 가진 노션 항목이 있는지 검색합니다.
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

const existingPage = queryResult.results?.[0];
const properties = buildCommonProperties();

if (existingPage) {
  // 기존 항목의 진행 상태는 평소에는 유지합니다.
  // GitHub 이슈가 닫히거나 다시 열릴 때만 상태를 자동 변경합니다.
  if (issue.state === "closed") {
    properties["상태"] = {
      status: {
        name: "완료",
      },
    };
  } else if (action === "reopened") {
    properties["상태"] = {
      status: {
        name: "할 일",
      },
    };
  }

  await requestNotion(`/pages/${existingPage.id}`, {
    method: "PATCH",
    body: JSON.stringify({
      properties,
    }),
  });

  console.log(`Notion 항목 업데이트 완료: ${issueKey}`);
} else {
  properties["상태"] = {
    status: {
      name: issue.state === "closed" ? "완료" : "할 일",
    },
  };

  await requestNotion("/pages", {
    method: "POST",
    body: JSON.stringify({
      parent: {
        type: "data_source_id",
        data_source_id: NOTION_DATA_SOURCE_ID,
      },
      properties,
    }),
  });

  console.log(`Notion 항목 생성 완료: ${issueKey}`);
}
