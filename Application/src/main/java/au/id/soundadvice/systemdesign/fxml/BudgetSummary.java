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
package au.id.soundadvice.systemdesign.fxml;

import au.id.soundadvice.systemdesign.budget.Budget;
import au.id.soundadvice.systemdesign.budget.Range;
import au.id.soundadvice.systemdesign.physical.Item;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class BudgetSummary {

    @Override
    public String toString() {
        if (key.isPresent()) {
            if (item.isPresent()) {
                return item.get() + ": " + amount + " (" + key.get().getUnit() + ')';
            } else {
                return key.get().getName() + ": " + amount + " (" + key.get().getUnit() + ')';
            }
        } else {
            return "Difference: " + amount;
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.key);
        hash = 59 * hash + Objects.hashCode(this.item);
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
        final BudgetSummary other = (BudgetSummary) obj;
        if (!Objects.equals(this.key, other.key)) {
            return false;
        }
        if (!Objects.equals(this.item, other.item)) {
            return false;
        }
        if (!Objects.equals(this.amount, other.amount)) {
            return false;
        }
        return true;
    }

    public static final BudgetSummary ZERO = new BudgetSummary(
            Optional.empty(), Optional.empty(), Range.ZERO);

    boolean hasKey() {
        return key.isPresent();
    }

    public Budget.Key getKey() {
        return key.get();
    }

    public Optional<Item> getItem() {
        return item;
    }

    public Range getAmount() {
        return amount;
    }

    public BudgetSummary(Budget.Key key, Optional<Item> item, Range amount) {
        this.key = Optional.of(key);
        this.item = item;
        this.amount = amount;
    }

    public BudgetSummary(Optional<Budget.Key> key, Optional<Item> item, Range amount) {
        this.key = key;
        this.item = item;
        this.amount = amount;
    }

    @CheckReturnValue
    public BudgetSummary add(BudgetSummary other) {
        if (key.isPresent()) {
            if (key.equals(other.key) && item.equals(other.item)) {
                return new BudgetSummary(key, item, amount.add(other.amount));
            } else {
                throw new IllegalArgumentException();
            }
        } else {
            return other;
        }
    }

    // Key will be empty for the ZERO value only
    private final Optional<Budget.Key> key;
    private final Optional<Item> item;
    private final Range amount;

    @CheckReturnValue
    BudgetSummary setAmount(Range amount) {
        return new BudgetSummary(key, item, amount);
    }
}
