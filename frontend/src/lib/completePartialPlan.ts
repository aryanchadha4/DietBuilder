import { api, DietPlan } from "./api";

export type CompletePartialProgress = {
  onProgress?: (plan: DietPlan) => void;
  signal?: AbortSignal;
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

  while ((next.days?.length ?? 0) < (next.totalDays ?? 0)) {
    if (signal?.aborted) {
      throw new DOMException("Aborted", "AbortError");
    }
    const prevLen = next.days?.length ?? 0;
    const total = next.totalDays ?? 0;
    if (!next.id || prevLen >= total) break;

    const opts =
      batchSize != null && batchSize > 0
        ? { batchSize, signal }
        : { signal };

    next = await api.dietPlans.completeRemainingDays(next.id, opts);

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
