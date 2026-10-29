import { computed, ref, watch } from 'vue';

/** Selection affects only the overview; the case query and personal progress stay unchanged. */
export default function useSinglePlanOverview<T extends { id: string }>(
  plans: () => readonly T[],
  project: () => string
) {
  const selectedPlanId = ref<string>();
  watch(
    [project, plans],
    ([projectId, available], previous) => {
      if (previous && previous[0] !== projectId) selectedPlanId.value = undefined;
      if (!available.some((plan) => plan.id === selectedPlanId.value)) selectedPlanId.value = available[0]?.id;
    },
    { immediate: true }
  );
  const selectedPlan = computed(() => plans().find((plan) => plan.id === selectedPlanId.value));
  return { selectedPlanId, selectedPlan };
}
