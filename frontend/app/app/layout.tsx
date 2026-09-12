import Image from "next/image";
import Link from "next/link";
import { AuthGate } from "@/components/auth-gate";
import { AppNav } from "@/components/app-nav";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gray-50">
      <header className="sticky top-0 z-10 border-b border-gray-200 bg-white/90 backdrop-blur">
        {/* Below lg (tablet and phone) this fluidly fills the viewport; at lg+ it locks to
            a fixed desktop width instead of shrinking, so the page scrolls horizontally
            rather than cramming columns together on a narrower desktop window. */}
        <div className="mx-auto flex w-full max-w-6xl items-center justify-between px-4 py-3 lg:w-[1152px] lg:max-w-none">
          <Link href="/app" className="flex items-center gap-2">
            <Image src="/logo.png" alt="InBOX" width={104} height={38} priority className="h-8 w-auto" />
          </Link>
          <AppNav />
        </div>
      </header>
      <main className="mx-auto w-full max-w-6xl px-4 py-6 lg:w-[1152px] lg:max-w-none">
        <AuthGate>{children}</AuthGate>
      </main>
    </div>
  );
}
