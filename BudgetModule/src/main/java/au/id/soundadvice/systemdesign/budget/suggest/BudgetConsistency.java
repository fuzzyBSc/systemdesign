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
package au.id.soundadvice.systemdesign.budget.suggest;

import au.id.soundadvice.systemdesign.budget.Budget;
import au.id.soundadvice.systemdesign.budget.BudgetAllocation;
import au.id.soundadvice.systemdesign.budget.Range;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.physical.Identity;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.util.Pair;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class BudgetConsistency {

    public static UnaryOperator<UndoState> flowBudgetKeyDown(Budget budget) {
        return state -> {
            Relations allocated = state.getAllocated();
            Optional<Budget> functionalCurrent = budget.get(state.getFunctional());
            if (!functionalCurrent.isPresent()) {
                return state;
            }
            Optional<Budget> allocatedCurrent = budget.get(allocated);
            if (!allocatedCurrent.isPresent()) {
                return state;
            }
            return state.setAllocated(
                    allocatedCurrent.get().setKey(
                            allocated, functionalCurrent.get().getKey()).getKey());
        };
    }

    public static UnaryOperator<UndoState> flowBudgetKeyUp(Budget budget) {
        return state -> {
            Relations functional = state.getFunctional();
            Optional<Budget> allocatedCurrent = state.getAllocated().get(budget.getUuid(), Budget.class);
            if (!allocatedCurrent.isPresent()) {
                return state;
            }
            Optional<Budget> functionalCurrent = functional.get(budget.getUuid(), Budget.class);
            if (!functionalCurrent.isPresent()) {
                return state;
            }
            return state.setFunctional(
                    functionalCurrent.get().setKey(
                            functional, allocatedCurrent.get().getKey()).getKey());
        };
    }

    public static Stream<Problem> getBudgetKeyMismatch(UndoState state) {
        Optional<Item> optionalSystem = Identity.getSystemOfInterest(state);
        if (!optionalSystem.isPresent()) {
            return Stream.empty();
        }
        Item system = optionalSystem.get();

        Relations functional = state.getFunctional();
        Relations allocated = state.getAllocated();

        Map<UUID, Budget> childBudgets
                = Budget.find(allocated)
                .collect(Collectors.toMap(Budget::getUuid, Function.identity()));

        return BudgetAllocation.find(functional, system)
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
                                    new Problem(description,
                                            Optional.of(flowBudgetKeyDown(parentBudget)),
                                            Optional.of(flowBudgetKeyUp(childBudget))));
                        }
                    }
                    return Stream.empty();
                });
    }

    private static UnaryOperator<UndoState> flowBudgetUp(Budget budget) {
        return state -> {
            Optional<Item> system = Identity.getSystemOfInterest(state);
            if (!system.isPresent()) {
                return state;
            }
            Relations functional = state.getFunctional();
            Relations allocated = state.getAllocated();
            Optional<Budget> sourceCurrent = allocated.get(budget.getUuid(), Budget.class);
            if (!sourceCurrent.isPresent()) {
                return state;
            }
            Budget parentBudget;
            Optional<Budget> targetCurrent = functional.get(budget.getUuid(), Budget.class);
            if (targetCurrent.isPresent()) {
                parentBudget = targetCurrent.get();
            } else {
                Pair<Relations, Budget> result
                        = sourceCurrent.get().addTo(functional);
                functional = result.getKey();
                parentBudget = result.getValue();
            }

            Range sourceSum = BudgetAllocation.sumAmounts(
                    sourceCurrent.get().findAllocations(allocated));
            // Create the corresponding budget allocation
            functional = BudgetAllocation.setAmount(
                    functional, system.get(), parentBudget, sourceSum)
                    .getKey();
            return state.setFunctional(functional);
        };
    }

    public static Stream<Problem> budgetMismatches(UndoState state) {
        Optional<Item> optionalSystem = Identity.getSystemOfInterest(state);
        if (!optionalSystem.isPresent()) {
            return Stream.empty();
        }
        Item system = optionalSystem.get();

        Relations functional = state.getFunctional();
        Relations allocated = state.getAllocated();

        Map<Budget.Key, Range> parentSums
                = BudgetAllocation.find(functional, system)
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
                        return Stream.of(new Problem(description,
                                Optional.empty(),
                                Optional.of(flowBudgetUp(childBudget.get()))));
                    }

                });
    }

    public static Stream<Problem> getProblems(UndoState state) {
        return Stream.concat(
                getBudgetKeyMismatch(state),
                budgetMismatches(state));
    }

}
