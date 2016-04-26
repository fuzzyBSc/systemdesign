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

import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.budget.beans.BudgetAllocationBean;
import au.id.soundadvice.systemdesign.moduleapi.relation.Reference;
import au.id.soundadvice.systemdesign.moduleapi.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import javafx.util.Pair;

/**
 * A flow represents the transfer of information, energy and/or materials from
 * one function to another.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class BudgetAllocation implements Relation {

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + Objects.hashCode(this.identifier);
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
        final BudgetAllocation other = (BudgetAllocation) obj;
        if (!Objects.equals(this.identifier, other.identifier)) {
            return false;
        }
        if (!Objects.equals(this.item, other.item)) {
            return false;
        }
        if (!Objects.equals(this.budget, other.budget)) {
            return false;
        }
        if (!Objects.equals(this.amount, other.amount)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return amount.toString();
    }

    public static Stream<BudgetAllocation> find(Relations baseline) {
        return baseline.findByClass(BudgetAllocation.class);
    }

    public static Stream<BudgetAllocation> find(Relations baseline, Item item) {
        return baseline.findReverse(item.getIdentifier(), BudgetAllocation.class);
    }

    public static Range sumAmounts(Stream<BudgetAllocation> stream) {
        return stream
                .map(BudgetAllocation::getAmount)
                .reduce(Range.ZERO, Range::add);
    }

    public static BudgetAllocation create(
            Item item, Budget budget, Range amount) {
        return new BudgetAllocation(
                UUID.randomUUID().toString(),
                item.getIdentifier(), budget.getIdentifier(),
                amount);
    }

    @CheckReturnValue
    public static Pair<Relations, BudgetAllocation> setAmount(
            Relations baseline, Item item, Budget budget, Range amount) {
        Optional<BudgetAllocation> existing = budget.findAllocations(baseline, item).findAny();
        BudgetAllocation newValue;
        if (existing.isPresent()) {
            newValue = existing.get().setAmount(amount);
        } else {
            newValue = create(item, budget, amount);
        }
        return new Pair<>(baseline.add(newValue), newValue);
    }

    @CheckReturnValue
    public Relations removeFrom(Relations baseline) {
        return baseline.remove(identifier);
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    public Reference<BudgetAllocation, Budget> getBudget() {
        return budget;
    }

    public Budget getBudget(Relations baseline) {
        return budget.getTarget(baseline);
    }

    public Reference<BudgetAllocation, Item> getItem() {
        return item;
    }

    public Item getItem(Relations baseline) {
        return item.getTarget(baseline);
    }

    public Range getAmount() {
        return amount;
    }

    @CheckReturnValue
    private BudgetAllocation setAmount(Range amount) {
        if (this.amount.equals(amount)) {
            return this;
        } else {
            return new BudgetAllocation(
                    identifier, item.getKey(), budget.getKey(), amount);
        }
    }

    @CheckReturnValue
    public Pair<Relations, BudgetAllocation> setBudget(Relations baseline, Budget budget) {
        if (this.budget.getKey().equals(budget.getIdentifier())) {
            return new Pair<>(baseline, this);
        } else {
            BudgetAllocation newValue = new BudgetAllocation(
                    identifier, item.getKey(), budget.getIdentifier(), amount);
            return new Pair<>(baseline.add(newValue), newValue);
        }
    }

    public BudgetAllocation(BudgetAllocationBean bean) {
        this(bean.getIdentifier(),
                bean.getItem(),
                bean.getBudget(),
                Range.fromRange(bean.getMinimum(), bean.getMaximum()));
    }

    private BudgetAllocation(
            String uuid,
            String item, String budget,
            Range amount) {
        this.identifier = uuid;
        this.item = new Reference<>(this, item, Item.class);
        this.budget = new Reference<>(this, budget, Budget.class);
        this.amount = amount;
    }

    private final String identifier;
    private final Reference<BudgetAllocation, Item> item;
    private final Reference<BudgetAllocation, Budget> budget;
    private final Range amount;

    public BudgetAllocationBean toBean(Relations baseline) {
        Item itemTarget = item.getTarget(baseline);
        Budget budgetTarget = budget.getTarget(baseline);
        String description;
        BigDecimal value = amount.getValue();
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            description = itemTarget.getDisplayName() + " consumes " + amount.negate() + " " + budgetTarget.getKey();
        } else {
            description = itemTarget.getDisplayName() + " provides " + amount + " " + budgetTarget.getKey();
        }
        return new BudgetAllocationBean(identifier, item.getKey(), budget.getKey(), amount, description);
    }
    private static final ReferenceFinder<BudgetAllocation> FINDER
            = new ReferenceFinder<>(BudgetAllocation.class);

    @Override
    public Stream<Reference> getReferences() {
        return FINDER.getReferences(this);
    }
}
