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
package au.id.soundadvice.systemdesign.budget.entity;

import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.physical.entity.Identity;
import java.text.ParseException;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;
import javafx.util.Pair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.moduleapi.entity.UniqueConstraint;
import java.util.Comparator;

/**
 * A flow represents the transfer of information, energy and/or materials from
 * one function to another.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum BudgetAllocation implements Table {
    budgetAllocation;

    @Override
    public Comparator<Record> getNaturalOrdering() {
        return (a, b) -> a.getShortName().compareTo(b.getShortName());
    }

    public static Stream<Record> find(Baseline baseline) {
        return baseline.findByType(BudgetAllocation.budgetAllocation);
    }

    public static Stream<Record> findForItem(Baseline baseline, Record item) {
        return baseline.findReverse(item.getIdentifier(), BudgetAllocation.budgetAllocation);
    }

    public static Stream<Record> findForBudget(Baseline baseline, Record budget) {
        return baseline.findReverse(budget.getIdentifier(), BudgetAllocation.budgetAllocation);
    }

    public static Optional<Record> getForItemAndBudget(Baseline baseline, Record budget, Record item) {
        return findForBudget(baseline, budget)
                .filter(allocation -> allocation.getViewOf().get().equals(item.getIdentifier()))
                .findAny();
    }

    public static Range sumAmounts(Stream<Record> stream) {
        return stream
                .map(allocation -> budgetAllocation.getAmount(allocation))
                .reduce(Range.ZERO, Range::add);
    }

    @CheckReturnValue
    public static Pair<WhyHowPair<Baseline>, Record> setAmount(
            WhyHowPair<Baseline> baselines, String now, Record item, Record budget, Range amount) {
        Optional<Record> existing = getForItemAndBudget(baselines.getChild(), item, budget);
        if (existing.isPresent()) {
            Record newValue = existing.get().asBuilder()
                    .setDescription(amount.toString())
                    .build(now);
            return baselines
                    .setChild(baselines.getChild().add(newValue))
                    .and(newValue);
        } else {
            Optional<Record> systemOfInterest = Identity.getSystemOfInterest(baselines);
            Optional<Record> parentBudget = budget.getTrace().flatMap(
                    trace -> baselines.getParent().get(trace, Budget.budget));
            Optional<Record> parentAllocation;
            if (systemOfInterest.isPresent() && parentBudget.isPresent()) {
                parentAllocation = getForItemAndBudget(
                        baselines.getParent(), systemOfInterest.get(), parentBudget.get());
            } else {
                parentAllocation = Optional.empty();
            }
            Record newValue = Record.create(BudgetAllocation.budgetAllocation)
                    .setTrace(parentAllocation)
                    .setContainer(budget)
                    .setViewOf(item)
                    .setDescription(amount.toString())
                    .build(now);
            return baselines
                    .setChild(baselines.getChild().add(newValue))
                    .and(newValue);
        }
    }

    public Record getBudget(Baseline baseline, Record allocation) {
        return baseline.get(allocation.getContainer().get(), Budget.budget).get();
    }

    public Record getItem(Baseline baseline, Record allocation) {
        return baseline.get(allocation.getViewOf().get(), Budget.budget).get();
    }

    public Range getAmount(Record allocation) {
        try {
            return Range.valueOf(allocation.getDescription());
        } catch (ParseException ex) {
            return Range.ZERO;
        }
    }

    @CheckReturnValue
    private Record setAmount(Record allocation, String now, Range amount) {
        return allocation.asBuilder()
                .setDescription(amount.toString())
                .build(now);
    }

    @Override
    public String getTableName() {
        return name();
    }

    @Override
    public Stream<UniqueConstraint> getUniqueConstraints() {
        return Stream.of(record -> new Object[]{record.getContainer(), record.getViewOf()});
    }

    @Override
    public Record merge(WhyHowPair<Baseline> context, String now, Record left, Record right) {
        return setAmount(Record.newerOf(left, right), now, getAmount(left).add(getAmount(right)));
    }

    @Override
    public Stream<Problem> getTraceProblems(WhyHowPair<Baseline> context, Record traceParent, Stream<Record> traceChildren) {
        return Stream.empty();
    }

    WhyHowPair<Baseline> setParentAmount(WhyHowPair<Baseline> baselines, String now, Record childBudget, Range amount) {
        Optional<Record> systemOfInterest = Identity.getSystemOfInterest(baselines);
        if (!systemOfInterest.isPresent()) {
            return baselines;
        }
        Optional<Record> parentBudget = childBudget.getTrace().flatMap(
                trace -> baselines.getParent().get(trace, Budget.budget));
        WhyHowPair<Baseline> result = baselines;
        if (!parentBudget.isPresent()) {
            // We need to create the parent budget
            WhyHowPair<Baseline> fakePair = new WhyHowPair<Baseline>(baselines.getParent(), baselines.getParent());
            Pair<WhyHowPair<Baseline>, Record> tmp = Budget.add(fakePair, now, Budget.budget.getKey(childBudget));
            result = new WhyHowPair<Baseline>(fakePair.getChild(), result.getChild());
            parentBudget = Optional.of(tmp.getValue());
        }
        return setAmount(
                result, now,
                systemOfInterest.get(),
                parentBudget.get(),
                amount)
                .getKey();
    }

    @Override
    public Stream<Problem> getUntracedParentProblems(WhyHowPair<Baseline> context, Stream<Record> untracedParents) {
        // Amount mismatches are already handled by Budget
        return Stream.empty();
    }

    @Override
    public Stream<Problem> getUntracedChildProblems(WhyHowPair<Baseline> context, Stream<Record> untracedChildren) {
        return untracedChildren
                .map(childAllocation -> Problem.onLoadAutofixProblem(
                        (baselines, now) -> fixChildTrace(baselines, now, childAllocation)));
    }

    private WhyHowPair<Baseline> fixChildTrace(WhyHowPair<Baseline> baselines, String now, Record record) {
        Optional<Record> allocation = baselines.getChild().get(record);
        if (allocation.isPresent()) {
            Optional<Record> systemOfInterest = Identity.getSystemOfInterest(baselines);
            Record childBudget = getBudget(baselines.getChild(), allocation.get());
            Optional<Record> parentBudget = childBudget.getTrace()
                    .flatMap(trace -> baselines.getParent().get(trace, Budget.budget));
            if (systemOfInterest.isPresent() && parentBudget.isPresent()) {
                Optional<Record> parentAllocation = getForItemAndBudget(
                        baselines.getParent(), systemOfInterest.get(), parentBudget.get());
                Record updated = allocation.get().asBuilder()
                        .setTrace(parentAllocation)
                        .build(now);
                return baselines.setChild(baselines.getChild().add(updated));
            }
        }
        return baselines;
    }
}
