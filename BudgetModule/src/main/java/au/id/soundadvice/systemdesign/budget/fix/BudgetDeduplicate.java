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
package au.id.soundadvice.systemdesign.budget.fix;

import au.id.soundadvice.systemdesign.budget.Budget;
import au.id.soundadvice.systemdesign.budget.BudgetAllocation;
import au.id.soundadvice.systemdesign.budget.Range;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.physical.Identity;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class BudgetDeduplicate {

    public static UndoState fix(UndoState state) {
        Relations functional = state.getFunctional();
        Relations allocated = state.getAllocated();
        functional = combineDuplicateBudgets(functional);
        allocated = combineDuplicateBudgets(allocated);
        functional = sumDuplicateAllocations(functional);
        allocated = sumDuplicateAllocations(allocated);

        allocated = removeExternalAllocations(allocated);

        state = state.setFunctional(functional).setAllocated(allocated);
        state = flowDownBudgets(state);

        return state;
    }

    private static Relations combineDuplicateBudgets(Relations baseline) {
        Map<Budget.Key, List<Budget>> budgets = Budget.find(baseline)
                .collect(Collectors.groupingBy(Budget::getKey));
        for (Map.Entry<Budget.Key, List<Budget>> entry : budgets.entrySet()) {
            List<Budget> list = entry.getValue();
            Budget canonical = list.get(0);
            for (int ii = 1; ii < list.size(); ++ii) {
                Budget duplicate = list.get(ii);
                Iterator<BudgetAllocation> it
                        = duplicate.findAllocations(baseline).iterator();
                // Preserve referential integritys
                while (it.hasNext()) {
                    BudgetAllocation allocation = it.next();
                    baseline = allocation.setBudget(baseline, canonical).getKey();
                }
                baseline = duplicate.removeFrom(baseline);
            }
        }
        return baseline;
    }

    private static Relations sumDuplicateAllocations(Relations baseline) {
        final Relations originalBaseline = baseline;
        Map<Budget, Map<Item, List<BudgetAllocation>>> values
                = BudgetAllocation.find(baseline).collect(
                Collectors.groupingBy(
                        allocation -> allocation.getBudget(originalBaseline),
                        Collectors.groupingBy(
                                allocation -> allocation.getItem(originalBaseline)
                        )));
        for (Map.Entry<Budget, Map<Item, List<BudgetAllocation>>> byBudget : values.entrySet()) {
            Budget budget = byBudget.getKey();
            for (Map.Entry<Item, List<BudgetAllocation>> byItem : byBudget.getValue().entrySet()) {
                Item item = byItem.getKey();
                List<BudgetAllocation> list = byItem.getValue();

                if (list.size() > 1) {
                    // Duplicate detected: Collapse
                    Range sum = BudgetAllocation.sumAmounts(list.stream());
                    for (int ii = 1; ii < list.size(); ++ii) {
                        // Actual removal
                        baseline = list.get(ii).removeFrom(baseline);
                    }
                    // Apply sum back to canonical entry
                    baseline = BudgetAllocation.setAmount(baseline, item, budget, sum).getKey();
                }
            }
        }
        return baseline;
    }

    private static UndoState flowDownBudgets(UndoState state) {
        Optional<Item> system = Identity.getSystemOfInterest(state);
        if (!system.isPresent()) {
            return state;
        }
        // Flow down UUID from parent budget to child budget
        Relations functional = state.getFunctional();
        Relations allocated = state.getAllocated();
        Iterator<Budget> parentIt = BudgetAllocation.find(functional, system.get())
                .map(allocation -> allocation.getBudget(functional)).iterator();
        Map<Budget.Key, Budget> childBudgets
                = Budget.find(allocated)
                .collect(Collectors.toMap(Budget::getKey, Function.identity()));
        while (parentIt.hasNext()) {
            Budget parentBudget = parentIt.next();
            Optional<Budget> optionalChildBudget = Optional.ofNullable(
                    childBudgets.get(parentBudget.getKey()));
            if (optionalChildBudget.isPresent()) {
                Budget childBudget = optionalChildBudget.get();
                if (!childBudget.getUuid().equals(parentBudget.getUuid())) {
                    allocated = childBudget.setUUID(allocated, parentBudget.getUuid()).getKey();
                }
            } else {
                // Flow budget down to child
                allocated = parentBudget.addTo(allocated).getKey();
            }
        }
        return state.setAllocated(allocated);
    }

    private static Relations removeExternalAllocations(Relations allocated) {
        Iterator<BudgetAllocation> it = Item.find(allocated)
                .filter(Item::isExternal)
                .flatMap(item -> {
                    return BudgetAllocation.find(allocated, item);
                })
                .iterator();
        Relations result = allocated;
        while (it.hasNext()) {
            BudgetAllocation allocation = it.next();
            result = allocation.removeFrom(result);
        }
        return result;
    }
}
