"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { clsx } from "clsx";

const NAV_ITEMS = [
  { href: "/app", label: "지원현황" },
  { href: "/app/jobs", label: "채용공고" },
  { href: "/app/notifications", label: "알림함" },
  { href: "/app/profile", label: "내 지원정보" },
  { href: "/app/settings", label: "설정" },
] as const;

export function AppNav() {
  const pathname = usePathname();

  return (
    <nav className="flex items-center gap-1 text-sm">
      {NAV_ITEMS.map((item) => {
        const active = item.href === "/app" ? pathname === "/app" : pathname.startsWith(item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            className={clsx(
              "rounded-md px-3 py-1.5 font-medium transition-colors",
              active ? "bg-brand-light text-brand" : "text-gray-500 hover:bg-gray-100 hover:text-gray-900",
            )}
          >
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}
