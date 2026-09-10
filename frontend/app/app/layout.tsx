import Link from "next/link";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="mx-auto max-w-6xl px-4 py-6">
      <header className="mb-6 flex items-center justify-between">
        <Link href="/app" className="text-lg font-semibold">
          채용공고 Inbox
        </Link>
        <nav className="flex gap-4 text-sm text-gray-600">
          <Link href="/app" className="hover:text-gray-900">
            지원현황
          </Link>
          <Link href="/app/notifications" className="hover:text-gray-900">
            알림함
          </Link>
          <Link href="/app/settings" className="hover:text-gray-900">
            설정
          </Link>
        </nav>
      </header>
      {children}
    </div>
  );
}
