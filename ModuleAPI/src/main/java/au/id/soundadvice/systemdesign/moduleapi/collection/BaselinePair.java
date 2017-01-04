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
package au.id.soundadvice.systemdesign.moduleapi.collection;

import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import java.util.Objects;
import java.util.function.UnaryOperator;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class BaselinePair {

    public enum Selector {
        PARENT, CHILD
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + Objects.hashCode(this.parent);
        hash = 17 * hash + Objects.hashCode(this.child);
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
        final BaselinePair other = (BaselinePair) obj;
        if (!Objects.equals(this.parent, other.parent)) {
            return false;
        }
        if (!Objects.equals(this.child, other.child)) {
            return false;
        }
        return true;
    }

    public Pair<BaselinePair, Record> and(Record record) {
        return new Pair<>(this, record);
    }

    public Baseline get(Selector selector) {
        switch (selector) {
            case PARENT:
                return getParent();
            case CHILD:
                return getChild();
            default:
                throw new AssertionError(selector.name());
        }
    }

    public Baseline getParent() {
        return parent;
    }

    public Baseline getChild() {
        return child;
    }

    public BaselinePair(Baseline parent, Baseline child) {
        this.parent = parent;
        this.child = child;
    }
    private final Baseline parent;
    private final Baseline child;

    public BaselinePair set(Selector selector, Baseline value) {
        switch (selector) {
            case PARENT:
                return setParent(value);
            case CHILD:
                return setChild(value);
            default:
                throw new AssertionError(selector.name());
        }
    }

    @CheckReturnValue
    public BaselinePair setParent(Baseline value) {
        if (parent == value) {
            return this;
        } else {
            return new BaselinePair(value, child);
        }
    }

    @CheckReturnValue
    public BaselinePair setChild(Baseline value) {
        if (child == value) {
            return this;
        } else {
            return new BaselinePair(parent, value);
        }
    }

    /**
     * Modify the functional (ie parent) baseline. If no such baseline exists
     * this method is a no-op.
     *
     * @param update A function to update the baseline.
     * @return The updated state
     */
    @CheckReturnValue
    public BaselinePair updateParent(UnaryOperator<Baseline> update) {
        return update(state -> state.setParent(update.apply(state.getParent())));
    }

    /**
     * Modify the allocated (ie child) baseline.
     *
     * @param update A function to update the baseline.
     * @return The updated state
     */
    @CheckReturnValue
    public BaselinePair updateChild(UnaryOperator<Baseline> update) {
        return update(state -> state.setChild(update.apply(state.getChild())));
    }

    /**
     * Modify both baselines
     *
     * @param update A function to update the baseline.
     * @return The updated state
     */
    @CheckReturnValue
    public BaselinePair update(UnaryOperator<BaselinePair> update) {
        return update.apply(this);
    }
}
