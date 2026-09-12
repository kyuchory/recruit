"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { ProfileCategory, ProfileItemResponse } from "@/lib/types";
import { Badge, Button, Field, inputClass } from "@/components/ui";
import { useConfirm } from "@/components/confirm-dialog";

type FieldDefinition = {
  key: string;
  label: string;
  type?: "text" | "date" | "email" | "tel" | "number" | "textarea" | "select";
  placeholder?: string;
  options?: string[];
  wide?: boolean;
};

type CategoryDefinition = {
  value: ProfileCategory;
  label: string;
  hint: string;
  singular: boolean;
  fields: FieldDefinition[];
};

const field = (key: string, label: string, options: Partial<FieldDefinition> = {}): FieldDefinition => ({ key, label, ...options });

const CATEGORIES: CategoryDefinition[] = [
  { value: "PERSONAL", label: "인적사항", hint: "지원서마다 반복되는 기본 신상 정보", singular: true, fields: [
    field("koreanName", "성명(한글)", { placeholder: "홍길동" }), field("hanjaName", "성명(한자)"),
    field("englishName", "성명(영문)", { placeholder: "HONG GIL DONG" }), field("birthDate", "생년월일", { type: "date" }),
    field("gender", "성별", { type: "select", options: ["남", "여", "기타"] }), field("nationality", "국적"),
    field("mobile", "전화번호 / 휴대폰", { type: "tel" }), field("email", "이메일", { type: "email" }),
    field("postalCode", "우편번호"), field("roadAddress", "도로명 주소", { wide: true }),
    field("lotAddress", "지번 주소", { wide: true }), field("veteransStatus", "보훈 여부"), field("disabilityStatus", "장애 여부"),
  ] },
  { value: "MILITARY", label: "병역", hint: "복무 여부와 군 복무 정보", singular: true, fields: [
    field("serviceStatus", "군필 여부", { type: "select", options: ["군필", "미필", "면제", "해당 없음"] }),
    field("branch", "군별"), field("specialty", "병과"), field("rank", "계급"), field("dischargeReason", "전역 사유"),
    field("startDate", "복무 시작일", { type: "date" }), field("endDate", "복무 종료일", { type: "date" }),
  ] },
  { value: "EDUCATION", label: "학력", hint: "학교별 학적·전공·성적 정보", singular: false, fields: [
    field("schoolType", "학교 구분", { type: "select", options: ["고등학교", "전문대학", "대학교", "대학원", "기타"] }),
    field("schoolName", "학교명"), field("location", "소재지"), field("majorCategory", "계열"), field("major", "전공"),
    field("attendanceType", "주·야간", { type: "select", options: ["주간", "야간", "해당 없음"] }),
    field("admissionDate", "입학일", { type: "date" }), field("graduationDate", "졸업일", { type: "date" }),
    field("graduationStatus", "졸업 상태", { type: "select", options: ["졸업", "졸업 예정", "재학", "휴학", "중퇴", "수료"] }),
    field("gpa", "평균 학점", { type: "number", placeholder: "3.60" }), field("gpaScale", "기준 학점", { type: "number", placeholder: "4.50" }),
    field("credits", "총 이수학점", { type: "number" }), field("transfer", "편입 여부"), field("doubleMajor", "복수전공"), field("minor", "부전공"),
    field("gradeSummary", "학년별 성적 요약", { type: "textarea", wide: true }), field("details", "학력 상세 메모", { type: "textarea", wide: true }),
  ] },
  { value: "LANGUAGE", label: "외국어", hint: "어학 시험과 회화 능력", singular: false, fields: [
    field("language", "언어"), field("testName", "시험명"), field("score", "점수·등급"), field("registrationNumber", "등록번호"),
    field("testDate", "시험일", { type: "date" }), field("acquiredDate", "취득일", { type: "date" }), field("speakingLevel", "회화 능력"), field("issuer", "주최기관"),
  ] },
  { value: "CERTIFICATION", label: "자격증", hint: "자격명·발행기관·자격번호", singular: false, fields: [
    field("name", "자격증명"), field("grade", "등급", { placeholder: "예: 1급, 기사, Level 2" }), field("issuer", "발행기관"),
    field("acquiredDate", "취득일", { type: "date" }), field("registrationNumber", "등록번호"),
  ] },
  { value: "CAREER", label: "경력", hint: "회사별 근무조건·담당업무·퇴사 사유", singular: false, fields: [
    field("company", "회사명"), field("department", "부서명"), field("employmentType", "고용 형태", { type: "select", options: ["정규직", "계약직", "인턴", "프리랜서", "기타"] }),
    field("jobTitle", "직무"), field("position", "직급"), field("annualSalary", "직전 연봉", { placeholder: "예: 3,600만원" }),
    field("location", "근무지", { wide: true }), field("startDate", "입사일", { type: "date" }), field("endDate", "퇴사일", { type: "date" }),
    field("leaveReason", "퇴사 사유", { type: "textarea", wide: true }), field("projects", "담당 프로젝트", { type: "textarea", wide: true }),
    field("achievements", "주요 업무 및 성과", { type: "textarea", wide: true }), field("summary", "경력 요약", { type: "textarea", wide: true }),
  ] },
  { value: "AWARD", label: "수상", hint: "수상명·기관·역할과 성과", singular: false, fields: [
    field("name", "대회·수상명"), field("prize", "수상 등급"), field("issuer", "수여기관"), field("awardDate", "수상일", { type: "date" }),
    field("role", "담당 역할", { wide: true }), field("summary", "수상 요약", { type: "textarea", wide: true }), field("details", "주요 활동 및 성과", { type: "textarea", wide: true }),
  ] },
  { value: "ACTIVITY", label: "활동·교육", hint: "교육, 대외활동과 이수 내역", singular: false, fields: [
    field("activityType", "구분", { type: "select", options: ["교육", "대외활동", "교내활동", "봉사", "경진대회", "기타"] }),
    field("name", "활동·교육명"), field("organization", "기관명"), field("role", "역할"), field("startDate", "시작일", { type: "date" }),
    field("endDate", "종료일", { type: "date" }), field("hours", "이수 시간", { placeholder: "예: 240시간" }),
    field("summary", "활동 요약", { type: "textarea", wide: true }), field("details", "상세 활동 내역", { type: "textarea", wide: true }),
  ] },
  { value: "SKILL", label: "기술", hint: "프로그램·기술별 활용 수준과 경험", singular: false, fields: [
    field("skillCategory", "기술 구분", { placeholder: "예: DBMS, 언어, 프레임워크" }), field("name", "프로그램·기술명"),
    field("level", "활용 수준"), field("duration", "사용 기간"), field("notes", "활용 경험·비고", { type: "textarea", wide: true }),
  ] },
  { value: "PROJECT", label: "프로젝트", hint: "프로젝트별 역할·기술·성과와 링크", singular: false, fields: [
    field("name", "프로젝트명"), field("role", "역할"), field("startDate", "시작일", { type: "date" }), field("endDate", "종료일", { type: "date" }),
    field("techStack", "기술 스택", { wide: true }), field("links", "관련 링크", { placeholder: "GitHub, 서비스 URL 등", wide: true }),
    field("summary", "프로젝트 요약", { type: "textarea", wide: true }), field("achievements", "담당 업무 및 성과", { type: "textarea", wide: true }),
    field("details", "상세 메모", { type: "textarea", wide: true }),
  ] },
  { value: "STORY", label: "자소서 소재", hint: "STAR 구조로 재활용할 경험 정리", singular: false, fields: [
    field("title", "소재 제목"), field("theme", "역량·주제", { placeholder: "예: 도전, 협업, 집요함" }), field("keywords", "키워드", { wide: true }),
    field("situation", "상황 (Situation)", { type: "textarea", wide: true }), field("task", "과제 (Task)", { type: "textarea", wide: true }),
    field("action", "행동 (Action)", { type: "textarea", wide: true }), field("result", "결과 (Result)", { type: "textarea", wide: true }),
    field("lesson", "배운 점·재활용 포인트", { type: "textarea", wide: true }),
  ] },
];

const categoryByValue = (value: ProfileCategory) => CATEGORIES.find((category) => category.value === value)!;

export default function CareerProfilePage() {
  const queryClient = useQueryClient();
  const [activeSection, setActiveSection] = useState<"ALL" | ProfileCategory>("ALL");
  const [search, setSearch] = useState("");
  const [addingCategory, setAddingCategory] = useState<ProfileCategory | null>(null);
  const [headerOffset, setHeaderOffset] = useState(0);
  const stickyBarRef = useRef<HTMLDivElement>(null);
  const items = useQuery({ queryKey: ["profile-items"], queryFn: () => api.get<ProfileItemResponse[]>("/api/v1/profile-items") });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["profile-items"] });
  const visibleItems = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase("ko-KR");
    return (items.data ?? []).filter((item) => !keyword || `${item.label}\n${Object.values(item.fields).join("\n")}\n${item.valueText}\n${item.details}`.toLocaleLowerCase("ko-KR").includes(keyword));
  }, [items.data, search]);
  const scrollTo = (category: "ALL" | ProfileCategory) => {
    setActiveSection(category);
    document.getElementById(category === "ALL" ? "profile-resume-top" : `profile-section-${category}`)
      ?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  // The app header above this page is also sticky; measure its height so this
  // page's own sticky tab bar docks just below it instead of covering it.
  useEffect(() => {
    const update = () => setHeaderOffset(document.querySelector("header")?.getBoundingClientRect().height ?? 0);
    update();
    window.addEventListener("resize", update);
    return () => window.removeEventListener("resize", update);
  }, []);

  // Scrollspy: highlight whichever section's top has passed underneath the
  // sticky header + tab bar, so the active tab tracks manual scrolling too.
  useEffect(() => {
    let frame = 0;
    const update = () => {
      frame = 0;
      const offset = headerOffset + (stickyBarRef.current?.getBoundingClientRect().height ?? 0) + 4;
      let current: "ALL" | ProfileCategory = "ALL";
      for (const category of CATEGORIES) {
        const el = document.getElementById(`profile-section-${category.value}`);
        if (el && el.getBoundingClientRect().top <= offset) current = category.value;
      }
      const atBottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2;
      if (atBottom) current = CATEGORIES[CATEGORIES.length - 1].value;
      setActiveSection((prev) => (prev === current ? prev : current));
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    update();
    return () => window.removeEventListener("scroll", onScroll);
  }, [headerOffset]);
  const resumeText = CATEGORIES.map((definition) => {
    const categoryItems = (items.data ?? []).filter((item) => item.category === definition.value);
    if (categoryItems.length === 0) return "";
    const entries = categoryItems.map((item) => definition.fields
      .filter((entry) => item.fields[entry.key])
      .map((entry) => `${entry.label}: ${item.fields[entry.key]}`)
      .join("\n"));
    return `[${definition.label}]\n${entries.join("\n\n")}`;
  }).filter(Boolean).join("\n\n");

  return <div id="profile-resume-top" className="scroll-mt-6 space-y-5">
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <h1 className="text-xl font-semibold">내 지원정보</h1>
        <p className="mt-1 text-sm text-gray-500">
          미리 작성해두면, 이력서 작성 시 복사·붙여넣기로 쉽게 완성할 수 있어요.
        </p>
      </div>
      <Button
        variant="ghost"
        disabled={!resumeText}
        onClick={() => navigator.clipboard.writeText(resumeText)}
        className="border-brand text-brand hover:bg-brand-light"
      >
        전체 정보 복사
      </Button>
    </div>
    <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-900">주민등록번호, 여권번호, 계좌번호, 비밀번호는 저장하지 마세요. 연락처·주소·등록번호가 포함된 항목은 민감 정보로 표시해 목록에서 가릴 수 있습니다.</div>
    <div
      ref={stickyBarRef}
      style={{ top: headerOffset }}
      className="sticky z-10 -mx-2 space-y-2 border-y border-gray-100 bg-white/95 px-2 py-3 backdrop-blur"
    >
      <div className="flex gap-2 overflow-x-auto pb-1">
        <button type="button" onClick={() => scrollTo("ALL")} className={`shrink-0 rounded-full px-3 py-1.5 text-sm transition-colors ${activeSection === "ALL" ? "bg-brand text-brand-foreground" : "border border-gray-200 bg-white text-gray-600 hover:bg-gray-50"}`}>전체</button>
        {CATEGORIES.map((item) => {
          const count = (items.data ?? []).filter((entry) => entry.category === item.value).length;
          return <button key={item.value} type="button" onClick={() => scrollTo(item.value)} className={`shrink-0 rounded-full px-3 py-1.5 text-sm transition-colors ${activeSection === item.value ? "bg-brand text-brand-foreground" : "border border-gray-200 bg-white text-gray-600 hover:bg-gray-50"}`}>{item.label}{count > 0 && <span className="ml-1 opacity-70">{count}</span>}</button>;
        })}
      </div>
      <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="전체 이력서에서 검색" className="w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand" />
    </div>
    {items.isLoading && <p className="py-12 text-center text-sm text-gray-400">지원정보를 불러오는 중…</p>}
    {!items.isLoading && search && visibleItems.length === 0 && <p className="rounded-lg border border-dashed border-gray-300 p-8 text-center text-sm text-gray-400">검색 결과가 없습니다.</p>}
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
      <div className="border-b border-gray-200 bg-brand-light px-5 py-4"><p className="text-xs font-medium uppercase tracking-widest text-brand">Career Profile</p><h2 className="mt-1 text-lg font-semibold text-brand-dark">이력서 정보</h2></div>
      {CATEGORIES.map((definition, index) => {
        const allCategoryItems = (items.data ?? []).filter((item) => item.category === definition.value);
        const categoryItems = visibleItems.filter((item) => item.category === definition.value);
        const canAdd = !definition.singular || allCategoryItems.length === 0;
        const adding = addingCategory === definition.value;
        return <section id={`profile-section-${definition.value}`} key={definition.value} className="scroll-mt-28 border-b border-gray-200 p-5 last:border-b-0">
          <div className="mb-4 flex items-start justify-between gap-3">
            <div><div className="flex items-center gap-2"><span className="text-xs font-semibold text-brand">{String(index + 1).padStart(2, "0")}</span><h2 className="text-lg font-semibold">{definition.label}</h2>{allCategoryItems.length > 0 && <Badge>{allCategoryItems.length}</Badge>}</div><p className="mt-1 text-xs text-gray-500">{definition.hint}</p></div>
            <Button
              variant="ghost"
              disabled={!canAdd}
              onClick={() => setAddingCategory(adding ? null : definition.value)}
              className={adding ? "" : "border-brand text-brand hover:bg-brand-light"}
            >
              {adding ? "닫기" : canAdd ? "+ 추가" : "등록 완료"}
            </Button>
          </div>
          {adding && <div className="mb-4"><ProfileItemForm key={definition.value} definition={definition} onCancel={() => setAddingCategory(null)} onSaved={() => { setAddingCategory(null); refresh(); }} /></div>}
          <div className="space-y-3">
            {categoryItems.map((item) => <ProfileItemCard key={`${item.id}:${item.version}`} item={item} onChanged={refresh} />)}
            {!search && allCategoryItems.length === 0 && <p className="rounded-lg border border-dashed border-gray-200 px-4 py-6 text-center text-sm text-gray-400">등록된 {definition.label} 정보가 없습니다.</p>}
            {search && categoryItems.length === 0 && allCategoryItems.length > 0 && <p className="text-sm text-gray-400">이 분류에는 검색 결과가 없습니다.</p>}
          </div>
        </section>;
      })}
    </div>
  </div>;
}

function ProfileItemCard({ item, onChanged }: { item: ProfileItemResponse; onChanged: () => void }) {
  const confirm = useConfirm();
  const [editing, setEditing] = useState(false);
  const [revealed, setRevealed] = useState(!item.sensitive);
  const remove = useMutation({ mutationFn: () => api.del(`/api/v1/profile-items/${item.id}`, item.version), onSuccess: onChanged });
  const definition = categoryByValue(item.category);
  const populated = definition.fields.filter((entry) => item.fields[entry.key]);
  const copyText = populated.map((entry) => `${entry.label}: ${item.fields[entry.key]}`).concat(item.valueText ? [`기존 핵심 내용: ${item.valueText}`] : [], item.details ? [`기존 상세 메모: ${item.details}`] : []).join("\n");
  if (editing) return <ProfileItemForm definition={definition} initial={item} onCancel={() => setEditing(false)} onSaved={() => { setEditing(false); onChanged(); }} />;

  return <article className="rounded-lg border border-gray-200 bg-white p-4">
    <div className="flex items-start justify-between gap-3">
      <div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold">{item.label}</h3>{item.sensitive && <Badge tone="amber">민감 정보</Badge>}</div>
      <div className="flex shrink-0 items-center gap-2 text-xs">
        {item.sensitive && <button type="button" onClick={() => setRevealed((value) => !value)} className="text-gray-500 hover:underline">{revealed ? "가리기" : "보기"}</button>}
        <button type="button" onClick={() => navigator.clipboard.writeText(copyText)} className="text-brand hover:underline">전체 복사</button>
        <button type="button" onClick={() => setEditing(true)} className="text-brand hover:underline">수정</button>
        <button
          type="button"
          onClick={async () => {
            if (await confirm({ message: `"${item.label}" 항목을 삭제할까요?`, tone: "danger", confirmLabel: "삭제" })) {
              remove.mutate();
            }
          }}
          className="text-gray-400 hover:text-red-600"
        >삭제</button>
      </div>
    </div>
    <dl className="mt-3 grid gap-x-6 gap-y-3 border-t border-gray-100 pt-3 sm:grid-cols-2">
      {populated.map((entry) => <div key={entry.key} className={entry.wide ? "sm:col-span-2" : ""}><dt className="text-xs text-gray-400">{entry.label}</dt><dd className="mt-0.5 whitespace-pre-wrap text-sm text-gray-700">{revealed ? item.fields[entry.key] : "••••••••"}</dd></div>)}
      {item.valueText && <div className="sm:col-span-2"><dt className="text-xs text-gray-400">기존 핵심 내용</dt><dd className="mt-0.5 whitespace-pre-wrap text-sm text-gray-700">{revealed ? item.valueText : "••••••••"}</dd></div>}
      {item.details && <div className="sm:col-span-2"><dt className="text-xs text-gray-400">기존 상세 메모</dt><dd className="mt-0.5 whitespace-pre-wrap text-sm text-gray-700">{revealed ? item.details : "••••••••"}</dd></div>}
    </dl>
    {remove.isError && <p className="mt-2 text-xs text-red-600">{remove.error.message}</p>}
  </article>;
}

function ProfileItemForm({ definition, initial, onCancel, onSaved }: { definition: CategoryDefinition; initial?: ProfileItemResponse; onCancel: () => void; onSaved: () => void }) {
  const [values, setValues] = useState<Record<string, string>>(initial?.fields ?? {});
  const [sensitive, setSensitive] = useState(initial?.sensitive ?? definition.value === "PERSONAL");
  const [legacyValue, setLegacyValue] = useState(initial?.valueText ?? "");
  const [legacyDetails, setLegacyDetails] = useState(initial?.details ?? "");
  const save = useMutation({
    mutationFn: () => initial
      ? api.patch<ProfileItemResponse>(`/api/v1/profile-items/${initial.id}`, { expectedVersion: initial.version, fields: values, valueText: legacyValue, details: legacyDetails, sensitive })
      : api.post<ProfileItemResponse>("/api/v1/profile-items", { category: definition.value, fields: values, sensitive }),
    onSuccess: onSaved,
  });
  const hasValue = Object.values(values).some((value) => value.trim());
  return <form className="rounded-lg border border-gray-300 bg-white p-4" onSubmit={(event) => { event.preventDefault(); if (hasValue) save.mutate(); }}>
    <div className="mb-4"><h3 className="font-semibold">{definition.label} {initial ? "수정" : "추가"}</h3><p className="mt-1 text-xs text-gray-500">필요한 항목만 입력해도 됩니다.</p></div>
    <div className="grid gap-3 sm:grid-cols-2">{definition.fields.map((entry) => <div key={entry.key} className={entry.wide ? "sm:col-span-2" : ""}><Field label={entry.label}><ProfileInput definition={entry} value={values[entry.key] ?? ""} onChange={(value) => setValues((current) => ({ ...current, [entry.key]: value }))} /></Field></div>)}</div>
    {(initial?.valueText || initial?.details) && <div className="mt-4 rounded-md bg-gray-50 p-3"><p className="mb-3 text-xs text-gray-500">이전 범용 양식의 내용입니다. 전용 칸으로 옮긴 뒤 비워도 됩니다.</p><div className="grid gap-3"><Field label="이전 핵심 내용"><textarea value={legacyValue} onChange={(event) => setLegacyValue(event.target.value)} rows={3} className={inputClass} /></Field><Field label="이전 상세 메모"><textarea value={legacyDetails} onChange={(event) => setLegacyDetails(event.target.value)} rows={5} className={inputClass} /></Field></div></div>}
    <label className="mt-3 flex items-center gap-2 text-sm text-gray-600"><input type="checkbox" checked={sensitive} onChange={(event) => setSensitive(event.target.checked)} />목록에서 모든 값을 기본적으로 가리기</label>
    <div className="mt-3 flex items-center gap-2"><Button type="submit" variant="brand" disabled={save.isPending || !hasValue}>{save.isPending ? "저장 중…" : "저장"}</Button><Button type="button" variant="ghost" onClick={onCancel}>취소</Button>{save.isError && <span className="text-xs text-red-600">{save.error.message}</span>}</div>
  </form>;
}

function ProfileInput({ definition, value, onChange }: { definition: FieldDefinition; value: string; onChange: (value: string) => void }) {
  if (definition.type === "textarea") return <textarea value={value} onChange={(event) => onChange(event.target.value)} maxLength={20000} rows={5} placeholder={definition.placeholder} className={inputClass} />;
  if (definition.type === "select") return <select value={value} onChange={(event) => onChange(event.target.value)} className={inputClass}><option value="">선택</option>{definition.options?.map((option) => <option key={option} value={option}>{option}</option>)}</select>;
  return <input type={definition.type ?? "text"} value={value} onChange={(event) => onChange(event.target.value)} maxLength={20000} step={definition.type === "number" ? "0.01" : undefined} placeholder={definition.placeholder} className={inputClass} />;
}
