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
package au.id.soundadvice.systemdesign.moduleapi.entity;

import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 * An ordered pair of unique identifiers, useful for describing the scope of a
 * connection between other relations. There should be at most one Interface
 * matching any given ConnectionScope.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ConnectionScope {

    @Override
    public String toString() {
        switch (direction) {
            case None:
                return left + " -- " + right;
            case Forward:
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
        final ConnectionScope other = (ConnectionScope) obj;
        if (!Objects.equals(this.left, other.left)) {
            return false;
        }
        if (!Objects.equals(this.right, other.right)) {
            return false;
        }
        if (this.direction != other.direction) {
            return false;
        }
        return true;
    }

    public Stream<ConnectionScope> enumerateDirections(boolean includeNone) {
        Stream<ConnectionScope> result = direction.stream()
                .map(dir -> this.setDirection(dir));
        if (includeNone) {
            result = Stream.concat(result, Stream.of(this.setDirection(Direction.None)));
        }
        return result;
    }

    public RecordID getLeft() {
        return left;
    }

    public RecordID getRight() {
        return right;
    }

    public Direction getDirection() {
        return direction;
    }

    public Direction getDirectionFrom(RecordID from) {
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

    public ConnectionScope(Identifiable left, Identifiable right) {
        this(left.getIdentifier(), right.getIdentifier());
    }

    public ConnectionScope(RecordID left, RecordID right) {
        this(left, right, Direction.None);
    }

    public ConnectionScope(Identifiable left, Identifiable right, Direction direction) {
        this(left.getIdentifier(), right.getIdentifier(), direction);
    }

    public ConnectionScope(RecordID left, RecordID right, Direction direction) {
        if (left.compareTo(right) < 0) {
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

    private final RecordID left;
    private final RecordID right;
    private final Direction direction;

    @CheckReturnValue
    public ConnectionScope setDirection(Direction value) {
        if (direction == value) {
            return this;
        } else {
            return new ConnectionScope(left, right, value);
        }
    }

    @CheckReturnValue
    public ConnectionScope setDirectionFrom(RecordID from, Direction value) {
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

    public RecordID otherEnd(RecordID identifier) throws IllegalArgumentException {
        if (identifier.equals(left)) {
            return right;
        } else if (identifier.equals(right)) {
            return left;
        } else {
            throw new IllegalArgumentException(identifier + " is not in this scope");
        }
    }

    public boolean hasEnd(RecordID identifier) {
        return identifier.equals(left) || identifier.equals(right);
    }

    public Stream<RecordID> ends() {
        return Stream.of(left, right);
    }

    public boolean isSelfConnection() {
        return left.equals(right);
    }

    public boolean contains(ConnectionScope other) {
        return left.equals(other.left) && right.equals(other.right)
                && direction.contains(other.direction);
    }
}
