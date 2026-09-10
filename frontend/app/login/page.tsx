import Link from "next/link";
import { API_BASE_URL } from "@/lib/api";

export default function LoginPage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center px-6">
      <h1 className="text-2xl font-semibold">채용공고 Inbox</h1>
      <p className="mt-2 text-sm text-gray-500">
        채용공고 링크를 지원현황 표로 만들고, 서류·NCS·코딩테스트·면접 등 각 절차의 일정을 알려줍니다.
      </p>

      <a
        href={`${API_BASE_URL}/oauth2/authorization/google`}
        className="mt-8 inline-flex items-center justify-center rounded-md border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium shadow-sm hover:bg-gray-50"
      >
        Google로 계속하기
      </a>

      <div className="mt-6 rounded-md bg-amber-50 p-3 text-xs text-amber-800">
        개발 환경에서는 Google 자격증명 없이 시드 사용자로 자동 로그인됩니다.{" "}
        <Link href="/app" className="font-medium underline">
          바로 시작하기
        </Link>
      </div>
    </main>
  );
}
