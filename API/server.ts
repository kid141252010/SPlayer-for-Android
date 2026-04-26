import fastify from "fastify";
import fastifyCookie from "@fastify/cookie";
import fastifyMultipart from "@fastify/multipart";
import NeteaseCloudMusicApi from "@neteasecloudmusicapienhanced/api";
import { pathCase } from "change-case";

const DEFAULT_PORT = Number(process.env["SP_API_PORT"] || process.env["VITE_SERVER_PORT"] || 1145);
const DEFAULT_HOST = process.env["SP_API_HOST"] || "0.0.0.0";
const IS_EMBEDDED_RUNTIME = process.env["SP_EMBEDDED"] === "1";
const DEFAULT_AMLL_DB_SERVER =
  process.env["SP_AMLL_DB_SERVER"] || "https://amlldb.bikonoo.com/ncm-lyrics/%s.ttml";

type RequestWithCookie = {
  query?: Record<string, unknown>;
  body?: Record<string, unknown>;
  headers: { cookie?: string };
};

type DynamicRequest = RequestWithCookie & {
  params: { "*": string };
};

type ReplyShape = {
  status: (code: number) => { send: (payload: unknown) => unknown };
  send: (payload: unknown) => unknown;
  header: (name: string, value: string) => void;
};

const mergeCookieInput = (request: RequestWithCookie) => {
  const query = (request.query ?? {}) as Record<string, unknown>;
  const body = (request.body ?? {}) as Record<string, unknown>;
  const cookie =
    typeof body.cookie === "string"
      ? body.cookie
      : typeof query.cookie === "string"
        ? query.cookie
        : request.headers.cookie;

  return {
    ...query,
    ...body,
    ...(cookie ? { cookie } : {}),
  };
};

const resolveRouterName = (requestPath: string) => {
  return Object.keys(NeteaseCloudMusicApi).find((key) => {
    if (typeof (NeteaseCloudMusicApi as Record<string, unknown>)[key] !== "function") {
      return false;
    }
    return pathCase(key) === requestPath || key === requestPath;
  });
};

const createDynamicHandler =
  (server: ReturnType<typeof fastify>) => async (request: DynamicRequest, reply: ReplyShape) => {
    const requestPath = request.params["*"];
    const routerName = resolveRouterName(requestPath);

    if (!routerName) {
      return reply.status(404).send({ error: "API not found" });
    }

    const neteaseApi = (
      NeteaseCloudMusicApi as unknown as Record<
        string,
        (params: Record<string, unknown>) => Promise<any>
      >
    )[routerName];

    try {
      const result = await neteaseApi(mergeCookieInput(request));
      return reply.send(result.body);
    } catch (error: unknown) {
      server.log.error({ err: error, requestPath }, "Netease API request failed");

      if (typeof error === "object" && error) {
        const apiError = error as { status?: number; body?: unknown; message?: string };
        if (apiError.status && [301, 400].includes(apiError.status)) {
          return reply.status(apiError.status).send(apiError.body);
        }
        return reply
          .status(500)
          .send(apiError.body || { error: apiError.message || "Internal Server Error" });
      }

      return reply.status(500).send({ error: String(error) });
    }
  };

export const createStandaloneApiServer = async () => {
  const server = fastify({
    logger: true,
    routerOptions: {
      ignoreTrailingSlash: true,
    },
  });

  await server.register(fastifyCookie);
  await server.register(fastifyMultipart);

  server.addHook("onRequest", async (_request, reply) => {
    reply.header("Access-Control-Allow-Origin", "*");
    reply.header("Access-Control-Allow-Headers", "*");
    reply.header("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  });

  server.options("/*", async (_request, reply) => {
    reply.status(204).send();
  });

  server.get("/api", async () => {
    return {
      name: "SPlayer API",
      description: "Standalone local API service for SPlayer Android/Desktop",
      list: [
        {
          name: "NeteaseCloudMusicApi",
          url: "/api/netease",
        },
      ],
    };
  });

  server.get("/api/netease", async () => {
    return {
      name: "@neteasecloudmusicapienhanced/api",
      description: "NeteaseCloudMusicApi Enhanced",
      url: "https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced",
    };
  });

  const dynamicHandler = createDynamicHandler(server);
  server.get("/api/netease/*", dynamicHandler);
  server.post("/api/netease/*", dynamicHandler);

  server.get(
    "/api/netease/lyric/ttml",
    async (request: { query: { id?: string } }, reply: ReplyShape) => {
      const id = request.query.id;
      if (!id) {
        return reply.status(400).send({ error: "id is required" });
      }

      const url = DEFAULT_AMLL_DB_SERVER.replace("%s", String(id));

      try {
        const response = await fetch(url);
        if (response.status !== 200) {
          return reply.send(null);
        }
        return reply.send(await response.text());
      } catch (error) {
        server.log.error({ err: error, id }, "Fetch TTML lyric failed");
        return reply.send(null);
      }
    },
  );

  return server;
};

export const startStandaloneApiServer = async () => {
  const server = await createStandaloneApiServer();

  try {
    await server.listen({
      port: DEFAULT_PORT,
      host: DEFAULT_HOST,
    });

    server.log.info(`SPlayer standalone API running at http://${DEFAULT_HOST}:${DEFAULT_PORT}/api`);
    server.log.info(
      `Use this base URL in Android: http://<your-computer-ip>:${DEFAULT_PORT}/api/netease`,
    );
    return server;
  } catch (error) {
    server.log.error(error, "Failed to start standalone API");
    throw error;
  }
};

void startStandaloneApiServer().catch(() => {
  if (IS_EMBEDDED_RUNTIME) {
    console.error("[embedded-api] startStandaloneApiServer failed; keeping runtime alive");
    return;
  }
  process.exit(1);
});
