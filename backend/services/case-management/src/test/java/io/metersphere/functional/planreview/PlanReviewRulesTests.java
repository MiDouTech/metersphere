package io.metersphere.functional.planreview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.metersphere.functional.planreview.PlanReviewPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanReviewRulesTests {
    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    @Test
    void initialPairDifferencePreservesOnlyContinuouslyRetainedMembers() {
        var difference = PlanReviewReconciliation.calculate(List.of("a", "b"), Set.of("b", "c"),
                Map.of("a", 1L, "b", 1L), Map.of("b", 1L, "c", 1L));
        assertEquals(Set.of("a"), difference.added());
        assertEquals(Set.of("c"), difference.removed());
        assertEquals(Set.of("b"), difference.retained());
        assertTrue(difference.reentered().isEmpty());
    }

    @Test
    void duplicateReferencesAcrossCollectionsDoNotCreateAnExit() {
        var before = PlanReviewReconciliation.calculate(List.of("a", "a", "b"), Set.of("a", "b"),
                Map.of("a", 1L, "b", 1L), Map.of("a", 1L, "b", 1L));
        var after = PlanReviewReconciliation.calculate(List.of("a", "b"), Set.of("a", "b"),
                Map.of("a", 1L, "b", 1L), Map.of("a", 1L, "b", 1L));
        assertTrue(before.empty());
        assertEquals(before, after);
    }

    @Test
    void frozenRemoveAndReaddCannotReuseOldPassedEpochEvenWhenFinalSetsMatch() {
        var diff = PlanReviewReconciliation.calculate(List.of("a", "b"), Set.of("a", "b"),
                Map.of("a", 2L, "b", 1L), Map.of("a", 1L, "b", 1L));
        assertFalse(diff.empty());
        assertEquals(Set.of("a"), diff.reentered());
        assertEquals(Set.of("b"), diff.retained());
    }

    @Test
    void differentIdWithSameNameIsNewAndOldIdExits() {
        var diff = PlanReviewReconciliation.calculate(List.of("a2"), Set.of("a"),
                Map.of("a2", 1L), Map.of("a", 5L));
        assertEquals(Set.of("a2"), diff.added());
        assertEquals(Set.of("a"), diff.removed());
        assertTrue(diff.retained().isEmpty());
    }

    @Test
    void emptyPlanRemovesAllCurrentMembersAndReturnedDiffCannotBeMutated() {
        var diff = PlanReviewReconciliation.calculate(List.of(), Set.of("a"), Map.of(), Map.of("a", 1L));
        assertEquals(Set.of("a"), diff.removed());
        assertThrows(UnsupportedOperationException.class, () -> diff.removed().clear());
    }

    @Test
    void missingOrRegressedEpochRejectsInsteadOfGuessingHistory() {
        assertThrows(IllegalArgumentException.class, () -> PlanReviewReconciliation.calculate(
                List.of("a"), Set.of("a"), Map.of(), Map.of("a", 1L)));
        assertThrows(IllegalArgumentException.class, () -> PlanReviewReconciliation.calculate(
                List.of("a"), Set.of("a"), Map.of("a", 1L), Map.of("a", 2L)));
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void everyMissingRequiredPermissionDeniesThatAction(Action action) {
        var all = EnumSet.allOf(Permission.class);
        assertTrue(allowed(action, new Access(all, true)));
        for (Permission permission : required(action)) {
            var missing = EnumSet.allOf(Permission.class);
            missing.remove(permission);
            assertFalse(allowed(action, new Access(missing, true)), action + " missing " + permission);
        }
    }

    @Test
    void m02CannotSubstituteForM08AndM08DoesNotRequireReviewUpdate() {
        Access pairingOnly = new Access(required(Action.PAIR), true);
        assertTrue(allowed(Action.PAIR, pairingOnly));
        assertFalse(allowed(Action.RETRY_SYNC, pairingOnly));
        Access recovery = new Access(required(Action.RETRY_SYNC), true);
        assertTrue(allowed(Action.RETRY_SYNC, recovery));
        assertFalse(allowed(Action.PAIR, recovery));
    }

    @Test
    void validSourceWriterDoesNotNeedReviewPermissionOrGrantButCannotBorrowServiceAuthority() {
        var sourceOnly = new Access(Set.of(Permission.PLAN_MEMBERS), false);
        assertTrue(canWriteSource(sourceOnly, State.UNDERWAY));
        assertFalse(canWriteSource(sourceOnly, State.COMPLETED));
        assertFalse(canWriteSource(new Access(EnumSet.of(Permission.REVIEW_UPDATE,
                Permission.REVIEW_MEMBERS), true), State.UNDERWAY));
    }

    @Test
    void emptyNewPairDoesNotRequireSourceMemberPermissionButInitialMembersDo() {
        var creator = new Access(required(Action.CREATE_PAIR), true);
        assertTrue(allowedCreatePair(creator, 0));
        assertFalse(allowedCreatePair(creator, 1));
        var withMembers = EnumSet.copyOf(required(Action.CREATE_PAIR));
        withMembers.add(Permission.PLAN_MEMBERS);
        assertTrue(allowedCreatePair(new Access(withMembers, true), 2));
    }

    @Test
    void obsoleteGrantDoesNotGateAuthorizedActions() {
        var permissions = EnumSet.allOf(Permission.class);
        var revoked = new Access(permissions, false);
        assertTrue(allowed(Action.PAIR, revoked));
        assertTrue(allowed(Action.RETRY_SYNC, revoked));
        assertTrue(allowed(Action.APPEND, revoked));
        assertTrue(canWriteSource(revoked, State.UNDERWAY));
        assertTrue(allowed(Action.UNLINK, revoked));
        assertTrue(allowed(Action.RETRY_NOTICE, revoked));
    }

    @Test
    void allBusinessStatePairsFreezeOrStopExceptEditablePair() {
        for (State plan : State.values()) for (State review : State.values()) {
            boolean expected = Set.of(State.PREPARED, State.UNDERWAY).contains(plan)
                    && Set.of(State.PREPARED, State.UNDERWAY).contains(review);
            assertEquals(expected, canSynchronize(plan, review, true, true), plan + " / " + review);
            assertFalse(canSynchronize(plan, review, false, true));
            assertEquals(expected,canSynchronize(plan, review, true, false));
        }
    }

    @Test
    void unknownHistoricalStatusCannotSilentlyBecomeEditableAndRestoreIsTwoSteps() {
        assertEquals(State.UNKNOWN, State.parse("PREPARE"));
        assertEquals(State.UNKNOWN, State.parse(null));
        assertEquals(State.COMPLETED, restoredState(State.ARCHIVED));
        assertEquals(State.UNDERWAY, restoredState(State.COMPLETED));
        assertThrows(IllegalArgumentException.class, () -> restoredState(State.UNKNOWN));
        assertThrows(IllegalArgumentException.class, () -> restoredState(State.DELETED));
        Access planOnly = new Access(required(Action.RESTORE_PLAN), false);
        assertTrue(allowed(Action.RESTORE_PLAN, planOnly));
        assertFalse(allowed(Action.RESTORE_REVIEW, planOnly));
        Access reviewOnly = new Access(required(Action.RESTORE_REVIEW), false);
        assertTrue(allowed(Action.RESTORE_REVIEW, reviewOnly));
        assertFalse(allowed(Action.RESTORE_PLAN, reviewOnly));
    }

    @Test
    void anyBoundRevisionChangeOrExactExpiryRejectsPreview() {
        Revision revision = new Revision("p", "plan", "review", "g1", 5, 9, 3);
        Preview preview = new Preview(revision, NOW.plus(PREVIEW_TTL));
        assertTrue(preview.valid(revision, NOW.plusSeconds(599)));
        assertFalse(preview.valid(revision, NOW.plusSeconds(600)));
        List<Revision> changed = List.of(
                new Revision("other", "plan", "review", "g1", 5, 9, 3),
                new Revision("p", "other", "review", "g1", 5, 9, 3),
                new Revision("p", "plan", "other", "g1", 5, 9, 3),
                new Revision("p", "plan", "review", "g2", 5, 9, 3),
                new Revision("p", "plan", "review", "g1", 6, 9, 3),
                new Revision("p", "plan", "review", "g1", 5, 10, 3));
        for (Revision current : changed) assertFalse(preview.valid(current, NOW));
        assertTrue(preview.valid(new Revision("p","plan","review","g1",5,9,4),NOW));
    }

    @Test
    void failureBackoffStartsAfterFailureAndExhaustionSurvivesSourceChanges() {
        var budget = PlanReviewRetryBudget.initial(NOW);
        assertTrue(budget.due(NOW));
        Instant finished = NOW.plusSeconds(25);
        for (int seconds : new int[] {10, 30, 60}) {
            budget = budget.failed(finished);
            assertEquals(finished.plusSeconds(seconds), budget.nextAttemptAt());
            assertFalse(budget.due(finished.plusSeconds(seconds - 1)));
            assertTrue(budget.due(finished.plusSeconds(seconds)));
            assertFalse(budget.late(finished.plusSeconds(seconds + 5)));
            assertTrue(budget.late(finished.plusSeconds(seconds + 6)));
            assertSame(budget, budget.sourceChanged());
            finished = budget.nextAttemptAt().plusSeconds(25);
        }
        budget = budget.failed(finished);
        assertTrue(budget.exhausted());
        assertEquals(4, budget.completedAttempts());
        assertNull(budget.nextAttemptAt());
        assertFalse(budget.due(finished.plusSeconds(9999)));
        assertSame(budget, budget.sourceChanged());
        var exhausted = budget;
        assertThrows(IllegalStateException.class, () -> exhausted.failed(NOW));
    }

    @Test
    void noticeRetriesCannotConsumeMemberBudgetOrResetItOnHealthRecovery() {
        var sync = PlanReviewRetryBudget.initial(NOW).failed(NOW);
        var notice = PlanReviewRetryBudget.initial(NOW);
        for (int i = 0; i < 4; i++) notice = notice.failed(NOW.plusSeconds(i * 90L));
        assertTrue(notice.exhausted());
        assertEquals(1, sync.completedAttempts());
        assertEquals(NOW.plusSeconds(10), sync.nextAttemptAt());
    }

    @Test
    void syntheticLargeSetChecksAlgorithmOnlyNotActualProjectSla() {
        var ids = new ArrayList<String>();
        for (int i = 0; i < 100_000; i++) ids.add("case-" + i);
        var diff = PlanReviewReconciliation.calculate(ids, Set.of(), Map.of(), Map.of());
        assertEquals(100_000, diff.added().size());
        assertTrue(diff.removed().isEmpty());
    }

    private Revision revision(long source, long target) {
        return new Revision("project", "plan", "review", "g1", source, target, 1);
    }

    @Test
    void opinionBeforeFirstBatchPausesAndKeepsEstablishedGeneration() {
        var before = revision(1, 1);
        var opinion = revision(1, 2);
        var approval = PlanReviewApproval.begin(before).observe(opinion);
        assertEquals(PlanReviewApproval.Status.RECONFIRM_REQUIRED, approval.status());
        assertEquals("g1", approval.approved().generation());
        assertEquals(0, approval.appliedBatches());
        assertFalse(approval.mayCommit(before));
        assertFalse(approval.mayCommit(opinion));
        assertThrows(IllegalStateException.class, () -> approval.committedBatch(before, opinion, false));
    }

    @Test
    void ownCommittedBatchAdvancesBarrierButExternalChangeKeepsSuccessfulProgress() {
        var initial = revision(2, 3);
        var afterBatch = revision(2, 4);
        var approval = PlanReviewApproval.begin(initial).committedBatch(initial, afterBatch, false);
        assertTrue(approval.mayCommit(afterBatch));
        assertEquals(1, approval.appliedBatches());
        var external = revision(3, 4);
        var paused = approval.observe(external);
        assertFalse(paused.mayCommit(afterBatch));
        assertEquals(1, paused.appliedBatches());
        assertEquals(PlanReviewApproval.Status.RECONFIRM_REQUIRED, paused.status());
        // Cancellation is no command: the persisted paused approval is left intact.
        var expired = new Preview(external, NOW);
        assertThrows(IllegalStateException.class, () -> paused.reconfirm(expired, external, NOW));
        var fresh = new Preview(external, NOW.plus(PREVIEW_TTL));
        var resumed = paused.reconfirm(fresh, external, NOW);
        assertEquals(1, resumed.appliedBatches());
        assertEquals("g1", resumed.approved().generation());
        assertTrue(resumed.mayCommit(external));
    }

    @Test
    void oldGenerationNeverRetargetsNewPairOrReactivatesAfterUnlink() {
        var initial = revision(1, 1);
        var other = new Revision("project", "plan", "review", "g2", 1, 1, 1);
        var old = PlanReviewApproval.begin(initial).observe(other);
        assertEquals(PlanReviewApproval.Status.ENDED, old.status());
        assertFalse(old.mayCommit(other));
        assertThrows(IllegalStateException.class,
                () -> old.reconfirm(new Preview(other, NOW.plus(PREVIEW_TTL)), other, NOW));
        assertEquals(PlanReviewApproval.Status.ENDED, old.observe(initial).status());
    }

    @Test
    void ordinaryOpinionAfterCompletedApprovalDoesNotDemandAnotherManualConfirmation() {
        var initial = revision(1, 1);
        var after = revision(1, 2);
        var complete = PlanReviewApproval.begin(initial).committedBatch(initial, after, true);
        assertEquals(PlanReviewApproval.Status.COMPLETE, complete.observe(revision(1, 3)).status());
    }

    @Test
    void sourceChangesStillInvalidateABatchButObsoleteGrantChangesDoNot() {
        var initial = revision(1, 1);
        var approval = PlanReviewApproval.begin(initial);
        assertThrows(IllegalStateException.class,
                () -> approval.committedBatch(initial, revision(2, 2), false));
        var grantChanged = new Revision("project", "plan", "review", "g1", 1, 2, 2);
        assertDoesNotThrow(() -> approval.committedBatch(initial, grantChanged, false));
    }

    @Test
    void sourceEpochRecordsFrozenEdgesAndIgnoresDuplicateCollectionReferences() {
        var first = PlanReviewSourceMembership.empty().changed(0, List.of("a", "a", "b"));
        assertEquals(1, first.version());
        assertSame(first, first.changed(1, List.of("a", "b")));
        var absent = first.changed(1, List.of("b"));
        var reentered = absent.changed(2, List.of("a", "b"));
        assertEquals(first.present(), reentered.present());
        assertEquals(2L, reentered.epochs().get("a"));
        assertEquals(1L, reentered.epochs().get("b"));
        assertEquals(3, reentered.version());
        assertThrows(IllegalStateException.class, () -> reentered.changed(1, List.of("a")));
    }

    @Test
    void repeatedReentriesNeverForgetEarlierEpochOrMutatePersistedSnapshot() {
        var current = PlanReviewSourceMembership.empty();
        for (int epoch = 1; epoch <= 5; epoch++) {
            current = current.changed(current.version(), List.of("a"));
            assertEquals((long) epoch, current.epochs().get("a"));
            current = current.changed(current.version(), List.of());
        }
        assertEquals(5L, current.epochs().get("a"));
        var persisted = current;
        assertThrows(UnsupportedOperationException.class, () -> persisted.epochs().clear());
    }

    @Test
    void settingsNeverAssumeRootOrSilentlyPickAnOperatorAsReviewer() {
        assertThrows(IllegalArgumentException.class, () -> new PlanReviewSettings("x", "root", List.of(), "SINGLE"));
        var settings = new PlanReviewSettings("  review  ", "root", List.of("user"), "SINGLE");
        assertEquals("review", settings.name());
        assertThrows(IllegalArgumentException.class, () -> settings.validateOptions(
                Set.of("valid-folder"), Set.of("user"), Set.of("SINGLE")));
        assertThrows(IllegalArgumentException.class, () -> settings.validateOptions(
                Set.of("root"), Set.of("other-user"), Set.of("SINGLE")));
        assertDoesNotThrow(() -> settings.validateOptions(Set.of("root"), Set.of("user"), Set.of("SINGLE")));
    }

    @Test
    void defaultReviewNameKeepsMaximumLengthWithoutSplittingAnEmoji() {
        assertEquals("plan-评审", PlanReviewSettings.defaultName(" plan "));
        String longName = "a".repeat(251) + "😀" + "tail";
        String name = PlanReviewSettings.defaultName(longName);
        assertEquals("a".repeat(251) + "-评审", name);
        assertTrue(name.length() <= 255);
        assertEquals(255, PlanReviewSettings.defaultName("a".repeat(300)).length());
        assertThrows(IllegalArgumentException.class,
                () -> new PlanReviewSettings("a".repeat(256), "root", List.of("user"), "SINGLE"));
    }
}
