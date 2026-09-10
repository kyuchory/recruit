"use client";

import { clsx } from "clsx";

export function Button({
  className,
  variant = "primary",
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "ghost" | "danger" }) {
  return (
    <button
      className={clsx(
        "inline-flex items-center justify-center rounded-md px-3 py-1.5 text-sm font-medium transition disabled:opacity-50",
        variant === "primary" && "bg-gray-900 text-white hover:bg-gray-700",
        variant === "ghost" && "border border-gray-300 bg-white hover:bg-gray-50",
        variant === "danger" && "border border-red-300 bg-white text-red-600 hover:bg-red-50",
        className,
      )}
      {...props}
    />
  );
}

export function Badge({ children, tone = "gray" }: { children: React.ReactNode; tone?: string }) {
  const tones: Record<string, string> = {
    gray: "bg-gray-100 text-gray-700",
    green: "bg-green-100 text-green-800",
    blue: "bg-blue-100 text-blue-800",
    amber: "bg-amber-100 text-amber-800",
    red: "bg-red-100 text-red-800",
  };
  return (
    <span className={clsx("inline-block rounded px-1.5 py-0.5 text-xs font-medium", tones[tone] ?? tones.gray)}>
      {children}
    </span>
  );
}

export function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-medium text-gray-700">{label}</span>
      {children}
    </label>
  );
}

export const inputClass =
  "w-full rounded-md border border-gray-300 px-2.5 py-1.5 text-sm focus:border-gray-500 focus:outline-none";
