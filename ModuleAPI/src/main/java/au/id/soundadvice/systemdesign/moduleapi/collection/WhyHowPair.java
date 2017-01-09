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

import au.id.soundadvice.systemdesign.moduleapi.storage.VersionInfo;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class WhyHowPair<T> {

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
        final WhyHowPair other = (WhyHowPair) obj;
        if (!Objects.equals(this.parent, other.parent)) {
            return false;
        }
        if (!Objects.equals(this.child, other.child)) {
            return false;
        }
        return true;
    }

    public <R> Pair<WhyHowPair<T>, R> and(R record) {
        return new Pair<>(this, record);
    }

    public T get(Selector selector) {
        switch (selector) {
            case PARENT:
                return getParent();
            case CHILD:
                return getChild();
            default:
                throw new AssertionError(selector.name());
        }
    }

    public Stream<T> stream() {
        return Stream.of(parent, child);
    }

    public <R> WhyHowPair<R> map(Function<T, R> mapper) {
        return new WhyHowPair<>(mapper.apply(parent), mapper.apply(child));
    }

    public T getParent() {
        return parent;
    }

    public T getChild() {
        return child;
    }

    public WhyHowPair(T parent, T child) {
        this.parent = parent;
        this.child = child;
    }
    private final T parent;
    private final T child;

    public WhyHowPair set(Selector selector, T value) {
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
    public WhyHowPair setParent(T value) {
        if (parent == value) {
            return this;
        } else {
            return new WhyHowPair(value, child);
        }
    }

    @CheckReturnValue
    public WhyHowPair setChild(T value) {
        if (child == value) {
            return this;
        } else {
            return new WhyHowPair(parent, value);
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
    public WhyHowPair updateParent(UnaryOperator<T> update) {
        return update(state -> state.setParent(update.apply(state.getParent())));
    }

    /**
     * Modify the allocated (ie child) baseline.
     *
     * @param update A function to update the baseline.
     * @return The updated state
     */
    @CheckReturnValue
    public WhyHowPair updateChild(UnaryOperator<T> update) {
        return update(state -> state.setChild(update.apply(state.getChild())));
    }

    /**
     * Modify both baselines
     *
     * @param update A function to update the baseline.
     * @return The updated state
     */
    @CheckReturnValue
    public WhyHowPair update(UnaryOperator<WhyHowPair<T>> update) {
        return update.apply(this);
    }
}
