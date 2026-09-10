export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/** Dev-only: fixed seeded user, overridable via localStorage. Ignored once real OAuth is on. */
export const DEV_USER_ID = "00000000-0000-0000-0000-000000000001";

function devUserId(): string {
  if (typeof window === "undefined") return DEV_USER_ID;
  try {
    return localStorage.getItem("devUserId") || DEV_USER_ID;
  } catch {
    return DEV_USER_ID;
  }
}

export class ApiError extends Error {
  status: number;
  code: string;
  fields?: Record<string, string>;
  constructor(status: number, code: string, message: string, fields?: Record<string, string>) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

async function request<T>(method: string, path: string, body?: unknown, headers?: Record<string, string>): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: {
      Accept: "application/json",
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      "X-Dev-User-Id": devUserId(),
      ...headers,
    },
    credentials: "include",
    cache: "no-store",
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const json = text ? JSON.parse(text) : undefined;
  if (!res.ok) {
    const e = json?.error ?? {};
    throw new ApiError(res.status, e.code ?? "UNKNOWN", e.message ?? res.statusText, e.fields);
  }
  return json as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>("POST", path, body ?? {}, headers),
  patch: <T>(path: string, body: unknown) => request<T>("PATCH", path, body),
  del: (path: string, version: number) =>
    request<void>("DELETE", path, undefined, { "If-Match": `"${version}"` }),
};
