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
package au.id.soundadvice.systemdesign.model;

import au.id.soundadvice.systemdesign.beans.BeanFactory;
import au.id.soundadvice.systemdesign.beans.BudgetBean;
import au.id.soundadvice.systemdesign.model.Baseline.BaselineAnd;
import au.id.soundadvice.systemdesign.relation.Reference;
import au.id.soundadvice.systemdesign.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.relation.Relation;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 * A flow represents the transfer of information, energy and/or materials from
 * one function to another.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Budget implements BeanFactory<Baseline, BudgetBean>, Relation {

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + Objects.hashCode(this.uuid);
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
        final Budget other = (Budget) obj;
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.key, other.key)) {
            return false;
        }
        return true;
    }

    public static final class Key implements Comparable<Key> {

        @Override
        public String toString() {
            return name + '(' + unit + ')';
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
            this.name = name;
            this.unit = unit;
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

    @Override
    public String toString() {
        return key.toString();
    }

    public static Stream<Budget> find(Baseline baseline, Key key) {
        return find(baseline).filter(budget -> key.equals(budget.key));
    }

    public static Stream<Budget> find(Baseline baseline) {
        return baseline.getStore().getByClass(Budget.class);
    }

    public Stream<BudgetAllocation> findAllocations(Baseline baseline) {
        return baseline.getStore().getReverse(uuid, BudgetAllocation.class);
    }

    public Stream<BudgetAllocation> findAllocations(Baseline baseline, Item item) {
        return findAllocations(baseline).filter(
                allocation -> allocation.getItem().getUuid().equals(item.getUuid()));
    }

    @CheckReturnValue
    public BaselineAnd<Budget> setKey(Baseline baseline, Key key) {
        if (this.key.equals(key)) {
            return baseline.and(this);
        } else {
            Budget newValue = new Budget(uuid, key);
            return baseline.add(newValue).and(newValue);
        }
    }

    public static Budget create(Key key) {
        return new Budget(UUID.randomUUID(), key);
    }

    @CheckReturnValue
    public static BaselineAnd<Budget> add(Baseline baseline, Key key) {
        Optional<Budget> existing = find(baseline, key).findAny();
        if (existing.isPresent()) {
            return baseline.and(existing.get());
        } else {
            Budget newValue = create(key);
            return baseline.add(newValue).and(newValue);
        }
    }

    public static UndoState removeFromAllocated(UndoState state, Key key) {
        Baseline functional = state.getFunctional();
        Baseline allocated = state.getAllocated();
        Optional<Item> system = state.getSystemOfInterest();
        if (system.isPresent()) {
            final Baseline preUpdateFunctional = functional;
            Iterator<BudgetAllocation> it = Budget.find(preUpdateFunctional, key)
                    .flatMap(budget -> budget.findAllocations(preUpdateFunctional, system.get()))
                    .iterator();
            while (it.hasNext()) {
                BudgetAllocation parentAllocation = it.next();
                functional = parentAllocation.removeFrom(functional);
            }
        }
        Iterator<Budget> it = Budget.find(allocated, key).iterator();
        while (it.hasNext()) {
            Budget childBudget = it.next();
            allocated = childBudget.removeFrom(allocated);
        }
        return state.setFunctional(functional).setAllocated(allocated);
    }

    @CheckReturnValue
    public Baseline removeFrom(Baseline baseline) {
        return baseline.remove(uuid);
    }

    public BaselineAnd<Budget> addTo(Baseline baseline) {
        Optional<Budget> existing = find(baseline, key).findAny();
        if (existing.isPresent()) {
            return baseline.and(existing.get());
        } else {
            return baseline.add(this).and(this);
        }
    }

    public BaselineAnd<Budget> setUUID(Baseline baseline, UUID uuid) {
        if (this.uuid.equals(uuid)) {
            return baseline.and(this);
        } else {
            // Create the new entry
            Budget newValue = new Budget(uuid, key);
            baseline = baseline.add(newValue);
            // Redirect existing allocations to this budget
            Iterator<BudgetAllocation> it = this.findAllocations(baseline).iterator();
            while (it.hasNext()) {
                BudgetAllocation allocation = it.next();
                baseline = allocation.setBudget(baseline, newValue).getBaseline();
            }
            return baseline.remove(this.uuid).and(newValue);
        }
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public Key getKey() {
        return key;
    }

    public Budget(BudgetBean bean) {
        this(bean.getUuid(), new Key(bean.getName(), bean.getUnit()));
    }

    private Budget(UUID uuid, Key key) {
        this.uuid = uuid;
        this.key = key;
    }

    private final UUID uuid;
    private final Key key;

    @Override
    public BudgetBean toBean(Baseline baseline) {
        String description = key.toString();
        return new BudgetBean(uuid, key.getName(), key.getUnit(), description);
    }
    private static final ReferenceFinder<Budget> finder
            = new ReferenceFinder<>(Budget.class);

    @Override
    public Stream<Reference> getReferences() {
        return finder.getReferences(this);
    }
}
