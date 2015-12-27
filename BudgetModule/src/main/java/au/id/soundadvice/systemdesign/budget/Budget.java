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

import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.budget.beans.BudgetBean;
import au.id.soundadvice.systemdesign.moduleapi.relation.Reference;
import au.id.soundadvice.systemdesign.moduleapi.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.physical.Identity;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

/**
 * A flow represents the transfer of information, energy and/or materials from
 * one function to another.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Budget implements Relation {

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

    public Optional<Budget> get(Relations baseline) {
        return baseline.get(uuid, Budget.class);
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

    public static Stream<Budget> find(Relations baseline, Key key) {
        return find(baseline).filter(budget -> key.equals(budget.key));
    }

    public static Stream<Budget> find(Relations baseline) {
        return baseline.findByClass(Budget.class);
    }

    public Stream<BudgetAllocation> findAllocations(Relations baseline) {
        return baseline.findReverse(uuid, BudgetAllocation.class);
    }

    public Stream<BudgetAllocation> findAllocations(Relations baseline, Item item) {
        return findAllocations(baseline).filter(
                allocation -> allocation.getItem().getUuid().equals(item.getUuid()));
    }

    @CheckReturnValue
    public Pair<Relations, Budget> setKey(Relations baseline, Key key) {
        if (this.key.equals(key)) {
            return new Pair<>(baseline, this);
        } else {
            Budget newValue = new Budget(uuid, key);
            return new Pair<>(baseline.add(newValue), newValue);
        }
    }

    public static Budget create(Key key) {
        return new Budget(UUID.randomUUID(), key);
    }

    @CheckReturnValue
    public static Pair<Relations, Budget> add(Relations baseline, Key key) {
        Optional<Budget> existing = find(baseline, key).findAny();
        if (existing.isPresent()) {
            return new Pair<>(baseline, existing.get());
        } else {
            Budget newValue = create(key);
            return new Pair<>(baseline.add(newValue), newValue);
        }
    }

    public static UndoState removeFromAllocated(UndoState state, Key key) {
        Relations functional = state.getFunctional();
        Relations allocated = state.getAllocated();
        Optional<Item> system = Identity.getSystemOfInterest(state);
        if (system.isPresent()) {
            final Relations preUpdateFunctional = functional;
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
    public Relations removeFrom(Relations baseline) {
        return baseline.remove(uuid);
    }

    public Pair<Relations, Budget> addTo(Relations baseline) {
        Optional<Budget> existing = find(baseline, key).findAny();
        if (existing.isPresent()) {
            return new Pair<>(baseline, existing.get());
        } else {
            return new Pair<>(baseline.add(this), this);
        }
    }

    public Pair<Relations, Budget> setUUID(Relations baseline, UUID uuid) {
        if (this.uuid.equals(uuid)) {
            return new Pair<>(baseline, this);
        } else {
            // Create the new entry
            Budget newValue = new Budget(uuid, key);
            baseline = baseline.add(newValue);
            // Redirect existing allocations to this budget
            Iterator<BudgetAllocation> it = this.findAllocations(baseline).iterator();
            while (it.hasNext()) {
                BudgetAllocation allocation = it.next();
                baseline = allocation.setBudget(baseline, newValue).getKey();
            }
            return new Pair<>(baseline.remove(this.uuid), newValue);
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

    public BudgetBean toBean() {
        String description = key.toString();
        return new BudgetBean(uuid, key.getName(), key.getUnit(), description);
    }
    private static final ReferenceFinder<Budget> FINDER
            = new ReferenceFinder<>(Budget.class);

    @Override
    public Stream<Reference> getReferences() {
        return FINDER.getReferences(this);
    }
}
