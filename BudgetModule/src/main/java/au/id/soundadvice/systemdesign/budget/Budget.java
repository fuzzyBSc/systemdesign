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
package au.id.soundadvice.systemdesign.budget;

import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.BaselinePair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.physical.Identity;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.moduleapi.entity.UniqueConstraint;

/**
 * A flow represents the transfer of information, energy and/or materials from
 * one function to another.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum Budget implements Table {
    budget;

    @Override
    public String getTableName() {
        return name();
    }

    @Override
    public Stream<UniqueConstraint> getUniqueConstraints() {
        return Stream.of(record -> {
            Optional<String> trace = record.getTrace();
            if (trace.isPresent()) {
                // Only one child budget per parent budget
                return trace;
            } else {
                // No additional unique constraint
                return record.getIdentifier();
            }
        });
    }

    @Override
    public Record merge(BaselinePair context, String now, Record left, Record right) {
        return Record.newerOf(left, right);
    }

    @Override
    public Stream<Problem> getTraceProblems(BaselinePair context, Record traceParent, Stream<Record> traceChildren) {
        Optional<Record> systemOfInterest = Identity.getSystemOfInterest(context);
        Optional<Record> parentAllocation = BudgetAllocation.getForItemAndBudget(
                context.getParent(), traceParent, systemOfInterest.get());
        Range parentAmount = parentAllocation.map(allocation -> BudgetAllocation.budgetAllocation.getAmount(allocation))
                .orElse(Range.ZERO);
        return traceChildren.flatMap(
                childBudget -> {
                    return Stream.concat(
                            getNameMismatchProblems(traceParent, childBudget),
                            getBudgetTotalProblems(context, parentAmount, childBudget)
                    );
                });
    }

    private Stream<Problem> getNameMismatchProblems(Record parentBudget, Record childBudget) {
        if (parentBudget.getLongName().equals(childBudget.getLongName())) {
            return Stream.empty();
        } else {
            return Stream.of(Problem.flowProblem(
                    "Budget name mismatch\n"
                    + "Parent = " + parentBudget.getLongName() + "\n"
                    + "Child = " + childBudget.getLongName(),
                    Optional.of((baselines, now) -> baselines.setChild(baselines.getChild().add(childBudget.asBuilder().setLongName(parentBudget.getLongName()).build(now)))),
                    Optional.of((baselines, now) -> baselines.setParent(baselines.getParent().add(parentBudget.asBuilder().setLongName(childBudget.getLongName()).build(now))))));
        }
    }

    @Override
    public Stream<Problem> getUntracedParentProblems(BaselinePair context, Stream<Record> untracedParents) {
        Optional<Record> systemOfInterest = Identity.getSystemOfInterest(context);
        if (systemOfInterest.isPresent()) {
            return untracedParents
                    .filter(parentBudget -> {
                        return !BudgetAllocation.getForItemAndBudget(context.getParent(), parentBudget, systemOfInterest.get())
                                .map(allocation -> BudgetAllocation.budgetAllocation.getAmount(allocation))
                                .orElse(Range.ZERO)
                                .isExactZero();
                    })
                    .map(parentBudget -> {
                        return Problem.onLoadAutofixProblem(
                                (baselines, now) -> add(baselines, now, getKey(parentBudget)).getKey());
                    });
        }
        return Stream.empty();
    }

    @Override
    public Stream<Problem> getUntracedChildProblems(BaselinePair context, Stream<Record> untracedChildren) {
        Range parentAmount = Range.ZERO;
        return untracedChildren.flatMap(
                childBudget -> getBudgetTotalProblems(context, parentAmount, childBudget));
    }

    public Stream<Problem> getBudgetTotalProblems(BaselinePair context, Range parentAmount, Record childBudget) {
        Range childAmount = Budget.budget.getTotal(context.getChild(), childBudget);
        if (parentAmount.equals(childAmount)) {
            return Stream.empty();
        } else {
            return Stream.of(Problem.flowProblem(
                    childBudget.getLongName() + " mismatch\nParent = " + parentAmount + "\nChild = " + childAmount,
                    Optional.empty(),
                    Optional.of((baselines, now)
                            -> BudgetAllocation.budgetAllocation.setParentAmount(baselines, now, childBudget, childAmount))));
        }
    }

    public Range getTotal(Baseline baseline, Record budget) {
        return BudgetAllocation.sumAmounts(BudgetAllocation.findForBudget(baseline, budget));
    }

    public static final class Key implements Comparable<Key> {

        @Override
        public String toString() {
            if (unit.isEmpty()) {
                return name;
            } else {
                return name + " (" + unit + ')';
            }
        }

        static Key valueOf(String input) {
            int lastParenOpen = input.lastIndexOf('(');
            if (lastParenOpen < 0) {
                return new Key(input, "");
            } else {
                String name = input.substring(0, lastParenOpen);
                String unit = input.substring(lastParenOpen + 1);
                if (unit.endsWith(")")) {
                    unit = unit.substring(0, unit.length() - 1);
                }
                return new Key(name, unit);
            }
        }

        public String getName() {
            return name;
        }

        public String getUnit() {
            return unit;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 47 * hash + Objects.hashCode(this.name);
            hash = 47 * hash + Objects.hashCode(this.unit);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Key other = (Key) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.unit, other.unit)) {
                return false;
            }
            return true;
        }

        public Key(String name, String unit) {
            this.name = safeChars(name);
            this.unit = safeChars(unit);
        }

        private static String safeChars(String value) {
            return value.replaceAll("[()]", "").trim();
        }

        private final String name;
        private final String unit;

        @Override
        public int compareTo(Key other) {
            {
                int result = name.compareTo(other.name);
                if (result != 0) {
                    return result;
                }
            }
            {
                int result = unit.compareTo(other.unit);
                if (result != 0) {
                    return result;
                }
            }
            return 0;
        }

        @CheckReturnValue
        public Key setName(String name) {
            return new Key(name, unit);
        }

        @CheckReturnValue
        public Key setUnit(String unit) {
            return new Key(name, unit);
        }
    }

    public static Stream<Record> find(Baseline baseline, Key key) {
        return find(baseline).filter(budget -> key.equals(Budget.budget.getKey(budget)));
    }

    public static Stream<Record> find(Baseline baseline) {
        return baseline.findByType(Budget.budget);
    }

    @CheckReturnValue
    public Pair<Baseline, Record> setKey(Baseline baseline, String now, Record budget, Key key) {
        String value = key.toString();
        if (budget.getLongName().equals(value)) {
            return new Pair<>(baseline, budget);
        } else {
            Record newValue = budget.asBuilder()
                    .setLongName(value)
                    .build(now);
            return new Pair<>(baseline.add(newValue), newValue);
        }
    }

    @CheckReturnValue
    public static Pair<BaselinePair, Record> add(BaselinePair baselines, String now, Key key) {
        Optional<Record> existingChild = find(baselines.getChild(), key).findAny();
        if (existingChild.isPresent()) {
            return new Pair<>(baselines, existingChild.get());
        } else {
            Optional<Record> existingParent = find(baselines.getParent(), key).findAny();
            Record newValue = Record.create(budget)
                    .setTrace(existingParent)
                    .setLongName(key.toString())
                    .build(now);
            baselines = baselines.setChild(baselines.getChild().add(newValue));
            return baselines.and(newValue);
        }
    }

    @CheckReturnValue
    public static Baseline remove(Baseline baseline, Key key) {
        Optional<Record> existing = find(baseline, key).findAny();
        if (existing.isPresent()) {
            return baseline.remove(existing.get().getIdentifier());
        } else {
            return baseline;
        }
    }

    public Key getKey(Record budget) {
        return Key.valueOf(budget.getLongName());
    }
}
