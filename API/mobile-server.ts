import http, { IncomingMessage, ServerResponse } from "http";
import https from "https";
import { createRequire } from "module";
import path from "path";

const DEFAULT_PORT = Number(process.env["SP_API_PORT"] || process.env["VITE_SERVER_PORT"] || 1145);
const DEFAULT_HOST = process.env["SP_API_HOST"] || "127.0.0.1";
const DEFAULT_AMLL_DB_SERVER =
  process.env["SP_AMLL_DB_SERVER"] || "https://amlldb.bikonoo.com/ncm-lyrics/%s.ttml";

type ApiFunction = (params: Record<string, unknown>) => Promise<{ body?: unknown } | unknown>;

const EMBEDDED_API_VENDOR_ROOT = path.join(__dirname, "vendor", "netease-api");
const EMBEDDED_API_MAIN_ENTRY = path.join(EMBEDDED_API_VENDOR_ROOT, "main.js");
const require = createRequire(EMBEDDED_API_MAIN_ENTRY);

const toPathCase = (value: string) => {
  return value
    .replace(/([a-z0-9])([A-Z])/g, "$1-$2")
    .replace(/_/g, "/")
    .replace(/-/g, "/")
    .toLowerCase();
};

const setCorsHeaders = (request: IncomingMessage, response: ServerResponse) => {
  const origin = request.headers.origin;
  response.setHeader("Access-Control-Allow-Origin", origin || "https://localhost");
  response.setHeader("Access-Control-Allow-Credentials", "true");
  response.setHeader("Access-Control-Allow-Headers", "*");
  response.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  response.setHeader("Vary", "Origin");
};

const sendJson = (
  request: IncomingMessage,
  response: ServerResponse,
  statusCode: number,
  payload: unknown,
) => {
  setCorsHeaders(request, response);
  response.statusCode = statusCode;
  response.setHeader("Content-Type", "application/json; charset=utf-8");
  response.end(JSON.stringify(payload));
};

const sendText = (
  request: IncomingMessage,
  response: ServerResponse,
  statusCode: number,
  payload: string,
) => {
  setCorsHeaders(request, response);
  response.statusCode = statusCode;
  response.setHeader("Content-Type", "text/plain; charset=utf-8");
  response.end(payload);
};

const readRequestBody = async (request: IncomingMessage) => {
  const chunks: Buffer[] = [];
  for await (const chunk of request) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  return Buffer.concat(chunks).toString("utf8");
};

const parseBody = (rawBody: string, contentType: string | undefined) => {
  if (!rawBody) return {};

  const normalizedContentType = (contentType || "").toLowerCase();

  if (normalizedContentType.includes("application/json")) {
    try {
      return JSON.parse(rawBody) as Record<string, unknown>;
    } catch {
      return {};
    }
  }

  if (normalizedContentType.includes("application/x-www-form-urlencoded")) {
    return Object.fromEntries(new URLSearchParams(rawBody).entries());
  }

  try {
    return JSON.parse(rawBody) as Record<string, unknown>;
  } catch {
    return Object.fromEntries(new URLSearchParams(rawBody).entries());
  }
};

const mergeCookieInput = (
  query: Record<string, unknown>,
  body: Record<string, unknown>,
  cookieHeader: string | undefined,
) => {
  const cookie =
    typeof body.cookie === "string"
      ? body.cookie
      : typeof query.cookie === "string"
        ? query.cookie
        : cookieHeader;

  return {
    ...query,
    ...body,
    ...(cookie ? { cookie } : {}),
  };
};

const loadNeteaseApi = (requestPath: string): ApiFunction | null => {
  const neteaseApi = require(EMBEDDED_API_MAIN_ENTRY) as Record<string, unknown>;
  const routerName = Object.keys(neteaseApi).find((key) => {
    const value = neteaseApi[key];
    return typeof value === "function" && (toPathCase(key) === requestPath || key === requestPath);
  });
  return routerName ? (neteaseApi[routerName] as ApiFunction) : null;
};

const fetchText = (url: string) =>
  new Promise<string | null>((resolve, reject) => {
    const client = url.startsWith("https://") ? https : http;
    const request = client.get(url, (response) => {
      if ((response.statusCode || 500) !== 200) {
        response.resume();
        resolve(null);
        return;
      }

      const chunks: Buffer[] = [];
      response.on("data", (chunk) => {
        chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
      });
      response.on("end", () => {
        resolve(Buffer.concat(chunks).toString("utf8"));
      });
    });

    request.on("error", reject);
  });

const handleNeteaseRoute = async (
  pathname: string,
  query: Record<string, unknown>,
  body: Record<string, unknown>,
  request: IncomingMessage,
  response: ServerResponse,
) => {
  if (pathname === "/api/netease") {
    sendJson(response, 200, {
      name: "@neteasecloudmusicapienhanced/api",
      description: "NeteaseCloudMusicApi Enhanced",
      url: "https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced",
    });
    return;
  }

  if (pathname === "/api/netease/lyric/ttml") {
    const id = String(query.id || "");
    if (!id) {
      sendJson(response, 400, { error: "id is required" });
      return;
    }

    try {
      const text = await fetchText(DEFAULT_AMLL_DB_SERVER.replace("%s", id));
      if (text === null) {
        sendJson(request, response, 200, null);
        return;
      }
      sendText(request, response, 200, text);
    } catch (error) {
      console.error("[embedded-api] Fetch TTML lyric failed", error);
      sendJson(request, response, 200, null);
    }
    return;
  }

  const requestPath = pathname.replace(/^\/api\/netease\//, "");
  const neteaseApi = loadNeteaseApi(requestPath);
  if (!neteaseApi) {
    sendJson(request, response, 404, { error: "API not found" });
    return;
  }

  try {
    const result = await neteaseApi(mergeCookieInput(query, body, request.headers.cookie));
    const payload =
      typeof result === "object" && result && "body" in result
        ? (result as { body?: unknown }).body
        : result;
    sendJson(request, response, 200, payload);
  } catch (error: unknown) {
    console.error("[embedded-api] Netease API request failed", requestPath, error);

    if (typeof error === "object" && error) {
      const apiError = error as { status?: number; body?: unknown; message?: string };
      if (apiError.status && [301, 400].includes(apiError.status)) {
        sendJson(request, response, apiError.status, apiError.body);
        return;
      }
      sendJson(
        request,
        response,
        500,
        apiError.body || { error: apiError.message || "Internal Server Error" },
      );
      return;
    }

    sendJson(request, response, 500, { error: String(error) });
  }
};

export const startEmbeddedApiServer = async () => {
  const server = http.createServer(async (request, response) => {
    setCorsHeaders(request, response);

    if (!request.url) {
      sendJson(request, response, 400, { error: "Missing request URL" });
      return;
    }

    if (request.method === "OPTIONS") {
      response.statusCode = 204;
      response.end();
      return;
    }

    const url = new URL(request.url, `http://${DEFAULT_HOST}:${DEFAULT_PORT}`);
    const pathname = url.pathname.replace(/\/+$/, "") || "/";
    const query = Object.fromEntries(url.searchParams.entries());
    const rawBody = request.method === "POST" ? await readRequestBody(request) : "";
    const body = parseBody(rawBody, request.headers["content-type"]);

    if (pathname === "/api") {
      sendJson(request, response, 200, {
        name: "SPlayer API",
        description: "Embedded local API service for SPlayer Android",
        list: [
          {
            name: "NeteaseCloudMusicApi",
            url: "/api/netease",
          },
        ],
      });
      return;
    }

    if (pathname === "/api/netease" || pathname.startsWith("/api/netease/")) {
      await handleNeteaseRoute(pathname, query, body, request, response);
      return;
    }

    sendJson(request, response, 404, { error: "API not found" });
  });

  await new Promise<void>((resolve, reject) => {
    server.once("error", reject);
    server.listen(DEFAULT_PORT, DEFAULT_HOST, () => {
      server.off("error", reject);
      resolve();
    });
  });

  console.log(`[embedded-api] listening on http://${DEFAULT_HOST}:${DEFAULT_PORT}/api`);
  return server;
};

void startEmbeddedApiServer().catch((error) => {
  console.error("[embedded-api] startEmbeddedApiServer failed", error);
});
