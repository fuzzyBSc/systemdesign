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

import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.relation.Relation;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.CheckReturnValue;

/**
 * A pair class identifying the scope of a given connection.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <T> The contained type
 */
public class RelationPair<T extends Relation> {

    @Override
    public String toString() {
        switch (direction) {
            case None:
                return left + " -- " + right;
            case Normal:
                return left + " -> " + right;
            case Reverse:
                return right + " -> " + left;
            case Both:
                return left + " <-> " + right;
            default:
                throw new AssertionError(direction.name());
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.left);
        hash = 83 * hash + Objects.hashCode(this.right);
        hash = 83 * hash + Objects.hashCode(this.direction);
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
        final RelationPair<?> other = (RelationPair<?>) obj;
        if (!Objects.equals(this.left, other.left)) {
            return false;
        }
        if (!Objects.equals(this.right, other.right)) {
            return false;
        }
        if (!Objects.equals(this.direction, other.direction)) {
            return false;
        }
        return true;
    }

    public T getLeft() {
        return left;
    }

    public T getRight() {
        return right;
    }

    public Direction getDirection() {
        return direction;
    }

    public Direction getDirectionFrom(T from) {
        if (from.equals(left)) {
            // The from orientation is already our left
            return direction;
        } else if (from.equals(right)) {
            // The from orientation is reversed
            return direction.reverse();
        } else {
            throw new IllegalArgumentException(from + " is not in this scope");
        }
    }

    public static <T extends Relation> Optional<RelationPair<T>> resolve(
            Baseline baseline, UUIDPair scope, Class<T> type) {
        Optional<T> left = baseline.get(scope.getLeft(), type);
        Optional<T> right = baseline.get(scope.getRight(), type);
        if (left.isPresent() && right.isPresent()) {
            return Optional.of(new RelationPair<>(left.get(), right.get(), scope.getDirection()));
        } else {
            return Optional.empty();
        }
    }

    public RelationPair(T left, T right) {
        this(left, right, Direction.None);
    }

    public RelationPair(T left, T right, Direction direction) {
        // Normalise ordering
        if (left.getUuid().compareTo(right.getUuid()) < 0) {
            this.left = left;
            this.right = right;
            this.direction = direction;
        } else {
            // swap
            this.right = left;
            this.left = right;
            this.direction = direction.reverse();
        }
    }

    private final T left;
    private final T right;
    private final Direction direction;

    @CheckReturnValue
    public RelationPair<T> setDirection(Direction value) {
        if (direction == value) {
            return this;
        } else {
            return new RelationPair<>(left, right, value);
        }
    }

    @CheckReturnValue
    public RelationPair<T> setDirectionFrom(T from, Direction value) {
        if (from.equals(left)) {
            // The from orientation is already our left
            return setDirection(value);
        } else if (from.equals(right)) {
            // The from orientation is reversed
            return setDirection(value.reverse());
        } else {
            throw new IllegalArgumentException(from + " is not in this scope");
        }
    }

    public T otherEnd(T t) throws IllegalArgumentException {
        if (t.equals(left)) {
            return right;
        } else if (t.equals(right)) {
            return left;
        } else {
            throw new IllegalArgumentException(t + " is not in this scope");
        }
    }

    public boolean hasEnd(T t) {
        return t.equals(left) || t.equals(right);
    }
}
