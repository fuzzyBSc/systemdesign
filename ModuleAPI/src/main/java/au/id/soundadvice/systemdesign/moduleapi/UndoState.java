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
package au.id.soundadvice.systemdesign.moduleapi;

import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import java.util.Objects;
import java.util.function.UnaryOperator;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class UndoState {

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + Objects.hashCode(this.functional);
        hash = 17 * hash + Objects.hashCode(this.allocated);
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
        final UndoState other = (UndoState) obj;
        if (!Objects.equals(this.functional, other.functional)) {
            return false;
        }
        if (!Objects.equals(this.allocated, other.allocated)) {
            return false;
        }
        return true;
    }

    public <T> Pair<UndoState, T> and(T relation) {
        return new Pair<>(this, relation);
    }

    public Relations getFunctional() {
        return functional;
    }

    public Relations getAllocated() {
        return allocated;
    }

    public UndoState(Relations functional, Relations allocated) {
        this.functional = functional;
        this.allocated = allocated;
    }
    private final Relations functional;
    private final Relations allocated;

    @CheckReturnValue
    public UndoState setFunctional(Relations value) {
        if (functional == value) {
            return this;
        } else {
            return new UndoState(value, allocated);
        }
    }

    @CheckReturnValue
    public UndoState setAllocated(Relations value) {
        if (allocated == value) {
            return this;
        } else {
            return new UndoState(functional, value);
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
    public UndoState updateFunctional(UnaryOperator<Relations> update) {
        return update(state -> state.setFunctional(
                update.apply(state.getFunctional())));
    }

    /**
     * Modify the allocated (ie child) baseline.
     *
     * @param update A function to update the baseline.
     * @return The updated state
     */
    @CheckReturnValue
    public UndoState updateAllocated(UnaryOperator<Relations> update) {
        return update(state -> state.setAllocated(
                update.apply(state.getAllocated())));
    }

    /**
     * Modify both baselines
     *
     * @param update A function to update the baseline.
     * @return The updated state
     */
    @CheckReturnValue
    public UndoState update(UnaryOperator<UndoState> update) {
        return update.apply(this);
    }
}
