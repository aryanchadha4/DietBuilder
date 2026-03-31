import { api, DietPlan } from "./api";

export type CompletePartialProgress = {
  onProgress?: (plan: DietPlan) => void;
  signal?: AbortSignal;
  /** Optional fallback target when API responses omit or underreport totalDays. */
  targetTotalDays?: number;
};

/**
 * Calls complete-days until `days.length >= totalDays`.
 * @param batchSize If positive, passes batchSize each request. If null/undefined, omits batch (append all remaining per call).
 */
export async function completePartialPlanUntilDone(
  initialPlan: DietPlan,
  batchSize: number | null | undefined,
  options?: CompletePartialProgress
): Promise<DietPlan> {
  let next = initialPlan;
  const signal = options?.signal;
  const fallbackTarget = options?.targetTotalDays ?? 0;

  while ((next.days?.length ?? 0) < Math.max(next.totalDays ?? 0, fallbackTarget)) {
    if (signal?.aborted) {
      throw new DOMException("Aborted", "AbortError");
    }
    const prevLen = next.days?.length ?? 0;
    const total = Math.max(next.totalDays ?? 0, fallbackTarget);
    if (!next.id || prevLen >= total) break;

    const opts =
      batchSize != null && batchSize > 0
        ? { batchSize, signal }
        : { signal };

    next = await api.dietPlans.completeRemainingDays(next.id, opts);
    if ((next.totalDays ?? 0) < total) {
      next = { ...next, totalDays: total };
    }

    if (signal?.aborted) {
      throw new DOMException("Aborted", "AbortError");
    }
    if ((next.days?.length ?? 0) <= prevLen) {
      throw new Error("NO_PROGRESS");
    }
    options?.onProgress?.(next);
  }

  return next;
}
