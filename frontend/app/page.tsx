import { API_BASE_URL, apiGet } from "@/lib/api";

type Health = {
  status: string;
  postgres: string;
  redis: string;
  flywayMigrations: number | string;
};

export default async function Home() {
  let health: Health | null = null;
  let error: string | null = null;

  try {
    health = await apiGet<Health>("/api/v1/health");
  } catch (e) {
    error = e instanceof Error ? e.message : String(e);
  }

  return (
    <main className="mx-auto max-w-xl px-4 py-16 font-sans">
      <h1 className="text-2xl font-semibold">채용공고 Inbox — MVP</h1>
      <p className="mt-2 text-sm text-gray-500">
        Step 1–4 stack check. Backend: <code>{API_BASE_URL}</code>
      </p>

      <section className="mt-8 rounded-lg border border-gray-200 p-4">
        <h2 className="text-sm font-medium text-gray-700">Backend health</h2>
        {error ? (
          <p className="mt-2 text-sm text-red-600">연결 실패: {error}</p>
        ) : (
          <dl className="mt-2 grid grid-cols-2 gap-1 text-sm">
            <dt className="text-gray-500">status</dt>
            <dd>{health?.status}</dd>
            <dt className="text-gray-500">postgres</dt>
            <dd>{health?.postgres}</dd>
            <dt className="text-gray-500">redis</dt>
            <dd>{health?.redis}</dd>
            <dt className="text-gray-500">flyway migrations</dt>
            <dd>{String(health?.flywayMigrations)}</dd>
          </dl>
        )}
      </section>

      <p className="mt-6 text-xs text-gray-400">
        다음 단계: JPA 엔티티 / Application CRUD API / 지원현황 화면.
      </p>
    </main>
  );
}
