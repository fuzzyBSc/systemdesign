/*
 * This is free and unencumbered software released into the public domain.
 * 
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 * 
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * 
 * For more information, please refer to <http://unlicense.org/>
 */
package au.id.soundadvice.systemdesign.consistency.suggestions;

import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.consistency.DisabledSolution;
import au.id.soundadvice.systemdesign.consistency.Problem;
import au.id.soundadvice.systemdesign.consistency.ProblemFactory;
import au.id.soundadvice.systemdesign.consistency.Solution;
import au.id.soundadvice.systemdesign.consistency.SolutionFlow;
import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.model.Budget;
import au.id.soundadvice.systemdesign.model.BudgetAllocation;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.Range;
import au.id.soundadvice.systemdesign.model.UndoState;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class BudgetConsistency implements ProblemFactory {

    private class FlowBudgetKey implements Solution {

        public FlowBudgetKey(Budget budget, SolutionFlow direction) {
            this.budget = budget;
            this.direction = direction;
        }

        private final Budget budget;
        private final SolutionFlow direction;

        @Override
        public String getDescription() {
            return direction.getDescription();
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void solve(EditState edit) {
            edit.updateState(state -> {
                Baseline source = direction.getSource(state);
                Baseline target = direction.getTarget(state);
                Optional<Budget> sourceCurrent = source.get(budget);
                if (!sourceCurrent.isPresent()) {
                    return state;
                }
                Optional<Budget> targetCurrent = target.get(budget);
                if (!targetCurrent.isPresent()) {
                    return state;
                }
                return direction.updateTarget(state, baseline -> {
                    return targetCurrent.get().setKey(
                            baseline, sourceCurrent.get().getKey())
                            .getBaseline();
                });
            });
        }

    }

    public Stream<Problem> getBudgetKeyMismatch(UndoState state) {
        Optional<Item> optionalSystem = state.getSystemOfInterest();
        if (!optionalSystem.isPresent()) {
            return Stream.empty();
        }
        Item system = optionalSystem.get();

        Baseline functional = state.getFunctional();
        Baseline allocated = state.getAllocated();

        Map<UUID, Budget> childBudgets
                = Budget.find(allocated)
                .collect(Collectors.toMap(Budget::getUuid, Function.identity()));

        return system.findBudgetAllocations(functional)
                .map(allocation -> allocation.getBudget(functional))
                .flatMap(parentBudget -> {
                    Optional<Budget> optionalChildBudget = Optional.ofNullable(
                            childBudgets.get(parentBudget.getUuid()));
                    if (optionalChildBudget.isPresent()) {
                        Budget childBudget = optionalChildBudget.get();
                        if (!childBudget.getKey().equals(parentBudget.getKey())) {
                            String description
                            = "Budget identifier mismatch: "
                            + parentBudget.getKey() + " vs "
                            + childBudget.getKey();
                            return Stream.of(
                                    new Problem(description, Stream.of(
                                                    new FlowBudgetKey(childBudget, SolutionFlow.Up),
                                                    new FlowBudgetKey(parentBudget, SolutionFlow.Down))));
                        }
                    }
                    return Stream.empty();
                });
    }

    private class FlowBudgetUp implements Solution {

        public FlowBudgetUp(Budget budget) {
            this.budget = budget;
        }

        private final Budget budget;

        @Override
        public String getDescription() {
            return SolutionFlow.Up.getDescription();
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void solve(EditState edit) {
            edit.updateState(state -> {
                Optional<Item> system = state.getSystemOfInterest();
                if (!system.isPresent()) {
                    return state;
                }
                Baseline functional = state.getFunctional();
                Baseline allocated = state.getAllocated();
                Optional<Budget> sourceCurrent = allocated.get(budget);
                if (!sourceCurrent.isPresent()) {
                    return state;
                }
                Budget parentBudget;
                Optional<Budget> targetCurrent = functional.get(budget);
                if (targetCurrent.isPresent()) {
                    parentBudget = targetCurrent.get();
                } else {
                    Baseline.BaselineAnd<Budget> result
                            = sourceCurrent.get().addTo(functional);
                    functional = result.getBaseline();
                    parentBudget = result.getRelation();
                }

                Range sourceSum = BudgetAllocation.sumAmounts(
                        sourceCurrent.get().findAllocations(allocated));
                // Create the corresponding budget allocation
                functional = BudgetAllocation.setAmount(
                        functional, system.get(), parentBudget, sourceSum)
                        .getBaseline();
                return state.setFunctional(functional);
            });
        }
    }

    public Stream<Problem> budgetMismatches(UndoState state) {
        Optional<Item> optionalSystem = state.getSystemOfInterest();
        if (!optionalSystem.isPresent()) {
            return Stream.empty();
        }
        Item system = optionalSystem.get();

        Baseline functional = state.getFunctional();
        Baseline allocated = state.getAllocated();

        Map<Budget.Key, Range> parentSums
                = system.findBudgetAllocations(functional)
                .collect(Collectors.toMap(
                                allocation -> allocation.getBudget(functional).getKey(),
                                BudgetAllocation::getAmount));

        Map<Budget.Key, Range> childSums
                = BudgetAllocation.find(allocated)
                .collect(Collectors.groupingBy(
                                allocation -> allocation.getBudget(allocated).getKey(),
                                Collectors.reducing(
                                        Range.ZERO,
                                        BudgetAllocation::getAmount,
                                        Range::add)));

        // Parent budgets should already be present in the allocated baseline
        // so we only need to look at this level for keys
        return Budget.find(allocated)
                // Find all distinct keys at both levels
                .map(Budget::getKey)
                .distinct()
                .flatMap(key -> {
                    // Turn keys with mismatched sums into problems
                    Range parentSum = parentSums.getOrDefault(key, Range.ZERO);
                    Range childSum = childSums.getOrDefault(key, Range.ZERO);
                    Optional<Budget> childBudget = Budget.find(allocated, key).findAny();
                    if (!childBudget.isPresent() || parentSum.subtract(childSum).isExactZero()) {
                        return Stream.empty();
                    } else {
                        String description = key + " mismatch " + parentSum + " vs " + childSum;
                        return Stream.of(new Problem(description, Stream.of(
                                                DisabledSolution.FlowDown,
                                                new FlowBudgetUp(childBudget.get()))));
                    }
                });
    }

    @Override
    public Stream<Problem> getProblems(EditState edit) {
        UndoState state = edit.getState();
        return Stream.concat(
                getBudgetKeyMismatch(state),
                budgetMismatches(state));
    }

}
