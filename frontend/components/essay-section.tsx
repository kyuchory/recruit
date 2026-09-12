"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { EssayLimitType, EssayQuestionResponse, EssayRevisionResponse, EssayStatus } from "@/lib/types";
import { Badge, Button, Field, inputClass } from "@/components/ui";

const LIMIT_LABELS: Record<EssayLimitType, string> = {
  NONE: "제한 없음",
  CHARACTERS_WITH_SPACES: "공백 포함 글자",
  CHARACTERS_WITHOUT_SPACES: "공백 제외 글자",
  UTF8_BYTES: "UTF-8 바이트",
  KOREAN_2_BYTES: "한글 2바이트 환산",
};

type Counts = { characters: number; noSpaces: number; utf8: number; korean2: number };

function counts(text: string): Counts {
  const points = Array.from(text);
  return {
    characters: points.length,
    noSpaces: points.filter((character) => !/\s/u.test(character)).length,
    utf8: new TextEncoder().encode(text).length,
    korean2: points.reduce((total, character) => total + (character.codePointAt(0)! <= 0x7f ? 1 : 2), 0),
  };
}

function selectedCount(type: EssayLimitType, value: Counts): number {
  if (type === "CHARACTERS_WITHOUT_SPACES") return value.noSpaces;
  if (type === "UTF8_BYTES") return value.utf8;
  if (type === "KOREAN_2_BYTES") return value.korean2;
  return value.characters;
}

export function EssaySection({ applicationId }: { applicationId: string }) {
  const queryClient = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [questionText, setQuestionText] = useState("");
  const [limitType, setLimitType] = useState<EssayLimitType>("CHARACTERS_WITH_SPACES");
  const [limitValue, setLimitValue] = useState("1000");
  const questions = useQuery({
    queryKey: ["essay-questions", applicationId],
    queryFn: () => api.get<EssayQuestionResponse[]>(`/api/v1/applications/${applicationId}/essay-questions`),
  });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ["essay-questions", applicationId] });
    queryClient.invalidateQueries({ queryKey: ["essay-progress"] });
  };
  const create = useMutation({
    mutationFn: () => api.post<EssayQuestionResponse>(`/api/v1/applications/${applicationId}/essay-questions`, {
      questionText: questionText.trim(),
      limitType,
      limitValue: limitType === "NONE" ? undefined : Number(limitValue),
    }),
    onSuccess: () => {
      setQuestionText("");
      setAdding(false);
      refresh();
    },
  });
  const completed = (questions.data ?? []).filter((question) => question.status === "COMPLETED").length;

  return (
    <section>
      <div className="mb-2 flex items-center justify-between">
        <div>
          <h2 className="text-sm font-semibold text-gray-700">자기소개서</h2>
          <p className="mt-0.5 text-xs text-gray-500">
            {(questions.data ?? []).length}개 문항 · {completed}개 완료
          </p>
        </div>
        <Button variant="ghost" onClick={() => setAdding((value) => !value)}>
          {adding ? "문항 추가 닫기" : "+ 문항 추가"}
        </Button>
      </div>

      {adding && (
        <div className="mb-3 rounded-lg border border-dashed border-gray-300 bg-white p-4">
          <Field label="질문">
            <textarea
              value={questionText}
              onChange={(event) => setQuestionText(event.target.value)}
              placeholder="지원 동기와 입사 후 포부를 작성해주세요."
              maxLength={5000}
              rows={3}
              className={inputClass}
            />
          </Field>
          <div className="mt-3 grid gap-2 sm:grid-cols-2">
            <Field label="제한 기준">
              <select value={limitType} onChange={(event) => setLimitType(event.target.value as EssayLimitType)} className={inputClass}>
                {Object.entries(LIMIT_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </Field>
            {limitType !== "NONE" && (
              <Field label="제한 수">
                <input type="number" min={1} max={1000000} value={limitValue} onChange={(event) => setLimitValue(event.target.value)} className={inputClass} />
              </Field>
            )}
          </div>
          <div className="mt-3 flex items-center gap-2">
            <Button onClick={() => create.mutate()} disabled={create.isPending || !questionText.trim() || (limitType !== "NONE" && Number(limitValue) < 1)}>
              {create.isPending ? "추가 중…" : `${(questions.data?.length ?? 0) + 1}번 문항 추가`}
            </Button>
            {create.isError && <span className="text-xs text-red-600">{create.error.message}</span>}
          </div>
        </div>
      )}

      <div className="space-y-3">
        {(questions.data ?? []).map((question, index) => (
          <EssayQuestionCard key={question.id} number={index + 1} question={question} onChanged={refresh} />
        ))}
        {questions.isLoading && <p className="text-sm text-gray-400">자기소개서를 불러오는 중…</p>}
        {!questions.isLoading && (questions.data ?? []).length === 0 && !adding && (
          <p className="rounded-lg border border-dashed border-gray-300 p-6 text-center text-sm text-gray-400">
            아직 자기소개서 문항이 없습니다. 문항을 추가해 답변 이력을 관리해보세요.
          </p>
        )}
      </div>
    </section>
  );
}

function EssayQuestionCard({ number, question, onChanged }: {
  number: number;
  question: EssayQuestionResponse;
  onChanged: () => void;
}) {
  const queryClient = useQueryClient();
  const [questionText, setQuestionText] = useState(question.questionText);
  const [answerText, setAnswerText] = useState(question.answerText);
  const [limitType, setLimitType] = useState<EssayLimitType>(question.limitType);
  const [limitValue, setLimitValue] = useState(question.limitValue?.toString() ?? "");
  const [status, setStatus] = useState<EssayStatus>(question.status);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [revisionLabel, setRevisionLabel] = useState("");
  const currentCounts = useMemo(() => counts(answerText), [answerText]);
  const used = selectedCount(limitType, currentCounts);
  const limit = limitType === "NONE" ? null : Number(limitValue);
  const overLimit = limit !== null && limit > 0 && used > limit;
  const dirty = questionText !== question.questionText || answerText !== question.answerText
    || limitType !== question.limitType || (limitType === "NONE" ? question.limitValue !== null : limit !== question.limitValue)
    || status !== question.status;

  const applySaved = (saved: EssayQuestionResponse) => {
    setQuestionText(saved.questionText);
    setAnswerText(saved.answerText);
    setLimitType(saved.limitType);
    setLimitValue(saved.limitValue?.toString() ?? "");
    setStatus(saved.status);
  };

  const persist = () => api.patch<EssayQuestionResponse>(`/api/v1/essay-questions/${question.id}`, {
    expectedVersion: question.version,
    questionText: questionText.trim(),
    answerText,
    limitType,
    limitValue: limitType === "NONE" ? undefined : limit,
    status,
  });
  const save = useMutation({
    mutationFn: persist,
    onSuccess: (saved) => {
      applySaved(saved);
      onChanged();
    },
  });
  const revisions = useQuery({
    queryKey: ["essay-revisions", question.id],
    enabled: historyOpen,
    queryFn: () => api.get<EssayRevisionResponse[]>(`/api/v1/essay-questions/${question.id}/revisions`),
  });
  const snapshot = useMutation({
    mutationFn: async () => {
      const saved = await persist();
      const revision = await api.post<EssayRevisionResponse>(`/api/v1/essay-questions/${question.id}/revisions`, {
        expectedVersion: saved.version,
        label: revisionLabel.trim() || undefined,
      });
      return { revision, saved };
    },
    onSuccess: ({ saved }) => {
      applySaved(saved);
      setRevisionLabel("");
      setHistoryOpen(true);
      onChanged();
      queryClient.invalidateQueries({ queryKey: ["essay-revisions", question.id] });
    },
    onError: onChanged,
  });
  const remove = useMutation({
    mutationFn: () => api.del(`/api/v1/essay-questions/${question.id}`, question.version),
    onSuccess: onChanged,
  });
  const restore = useMutation({
    mutationFn: (revisionId: string) => api.post<EssayQuestionResponse>(
      `/api/v1/essay-questions/${question.id}/revisions/${revisionId}/restore`,
      { expectedVersion: question.version },
    ),
    onSuccess: (saved) => {
      applySaved(saved);
      onChanged();
    },
  });
  const error = save.error ?? snapshot.error ?? remove.error ?? restore.error;

  return (
    <article className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <h3 className="font-semibold">{number}번 문항</h3>
          <Badge tone={status === "COMPLETED" ? "green" : "gray"}>{status === "COMPLETED" ? "완료" : "작성 중"}</Badge>
          {dirty && <span className="text-xs text-amber-600">저장하지 않은 변경</span>}
        </div>
        <button
          type="button"
          className="text-xs text-gray-400 hover:text-red-600"
          onClick={() => window.confirm(`${number}번 문항과 모든 이력을 삭제할까요?`) && remove.mutate()}
        >삭제</button>
      </div>

      <textarea value={questionText} onChange={(event) => setQuestionText(event.target.value)} maxLength={5000} rows={2} className={`${inputClass} mt-3`} />
      <div className="mt-2 flex flex-col gap-2 sm:flex-row">
        <select value={limitType} onChange={(event) => setLimitType(event.target.value as EssayLimitType)} className={inputClass}>
          {Object.entries(LIMIT_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
        {limitType !== "NONE" && (
          <input type="number" min={1} max={1000000} value={limitValue} onChange={(event) => setLimitValue(event.target.value)} className={inputClass} aria-label="제한 수" />
        )}
      </div>

      <textarea
        value={answerText}
        onChange={(event) => setAnswerText(event.target.value)}
        maxLength={100000}
        rows={12}
        placeholder="자기소개서 답변을 작성하세요."
        className={`${inputClass} mt-3 min-h-64 resize-y leading-6`}
      />
      <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs">
        {limit !== null && limit > 0 && (
          <span className={overLimit ? "font-semibold text-red-600" : "font-semibold text-blue-600"}>
            {LIMIT_LABELS[limitType]} {used.toLocaleString()} / {limit.toLocaleString()}{overLimit ? ` · ${used - limit} 초과` : ""}
          </span>
        )}
        <span className="text-gray-500">공백 포함 {currentCounts.characters.toLocaleString()}자</span>
        <span className="text-gray-500">공백 제외 {currentCounts.noSpaces.toLocaleString()}자</span>
        <span className="text-gray-500">UTF-8 {currentCounts.utf8.toLocaleString()} bytes</span>
        <span className="text-gray-500">한글 2바이트 {currentCounts.korean2.toLocaleString()} bytes</span>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <Button onClick={() => save.mutate()} disabled={save.isPending || !dirty || !questionText.trim() || (limitType !== "NONE" && (!limit || limit < 1))}>
          {save.isPending ? "저장 중…" : "초안 저장"}
        </Button>
        <label className="flex items-center gap-1.5 text-sm text-gray-600">
          <input type="checkbox" checked={status === "COMPLETED"} onChange={(event) => setStatus(event.target.checked ? "COMPLETED" : "DRAFT")} />
          작성 완료
        </label>
        <input value={revisionLabel} onChange={(event) => setRevisionLabel(event.target.value)} maxLength={100} placeholder="버전 이름 (선택)" className="rounded-md border border-gray-300 px-2.5 py-1.5 text-sm" />
        <Button variant="ghost" onClick={() => snapshot.mutate()} disabled={snapshot.isPending || !questionText.trim() || (limitType !== "NONE" && (!limit || limit < 1))}>
          {snapshot.isPending ? "저장 중…" : "현재 내용 버전 저장"}
        </Button>
        <button type="button" onClick={() => setHistoryOpen((value) => !value)} className="text-sm text-blue-600 hover:underline">
          이력 {question.revisionCount}개 {historyOpen ? "접기" : "보기"}
        </button>
      </div>
      {error && <p className="mt-2 text-xs text-red-600">{error.message}</p>}

      {historyOpen && (
        <div className="mt-4 space-y-2 border-t border-gray-100 pt-3">
          {revisions.isLoading && <p className="text-xs text-gray-400">이력을 불러오는 중…</p>}
          {(revisions.data ?? []).map((revision) => (
            <details key={revision.id} className="rounded-md bg-gray-50 p-3 text-sm">
              <summary className="cursor-pointer font-medium">
                v{revision.revisionNo} {revision.label ?? "저장본"}
                <span className="ml-2 font-normal text-gray-400">{new Date(revision.createdAt).toLocaleString("ko-KR")}</span>
              </summary>
              <p className="mt-2 whitespace-pre-wrap text-xs font-medium text-gray-600">{revision.questionText}</p>
              <p className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap rounded bg-white p-2 text-gray-700">{revision.answerText || "(빈 답변)"}</p>
              <div className="mt-2 flex items-center justify-between text-xs text-gray-500">
                <span>공백 포함 {revision.characterCount}자 · UTF-8 {revision.utf8ByteCount} bytes</span>
                <button
                  type="button"
                  className="text-blue-600 hover:underline"
                  onClick={() => window.confirm("이 버전의 답변을 현재 초안으로 복원할까요?") && restore.mutate(revision.id)}
                >답변 복원</button>
              </div>
            </details>
          ))}
          {!revisions.isLoading && (revisions.data ?? []).length === 0 && <p className="text-xs text-gray-400">저장한 버전이 없습니다.</p>}
        </div>
      )}
    </article>
  );
}
