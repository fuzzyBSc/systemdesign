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

import au.id.soundadvice.systemdesign.moduleapi.entity.ConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.entity.Direction;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 * A pair class identifying the scope of a given connection.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class RecordConnectionScope {

    @Override
    public String toString() {
        switch (scope.getDirection()) {
            case None:
                return left + " -- " + right;
            case Forward:
                return left + " -> " + right;
            case Reverse:
                return right + " -> " + left;
            case Both:
                return left + " <-> " + right;
            default:
                throw new AssertionError(scope.getDirection().name());
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.scope);
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
        final RecordConnectionScope other = (RecordConnectionScope) obj;
        if (!Objects.equals(this.left, other.left)) {
            return false;
        }
        if (!Objects.equals(this.right, other.right)) {
            return false;
        }
        if (!Objects.equals(this.scope, other.scope)) {
            return false;
        }
        return true;
    }

    public boolean contains(RecordConnectionScope other) {
        return scope.contains(other.scope);
    }

    public Record getLeft() {
        return left;
    }

    public Record getRight() {
        return right;
    }

    public Direction getDirection() {
        return scope.getDirection();
    }

    public Direction getDirectionFrom(Record from) {
        return scope.getDirectionFrom(from.getIdentifier());
    }

    public static Optional<RecordConnectionScope> resolve(
            Baseline baseline, ConnectionScope scope, Table type) {
        Optional<Record> left = baseline.get(scope.getLeft(), type);
        Optional<Record> right = baseline.get(scope.getRight(), type);
        if (left.isPresent() && right.isPresent()) {
            return Optional.of(new RecordConnectionScope(left.get(), right.get(), scope));
        } else {
            return Optional.empty();
        }
    }

    public static RecordConnectionScope resolve(Record left, Record right) {
        return resolve(left, right, Direction.None);
    }

    public static RecordConnectionScope resolve(
            Record left, Record right, Direction direction) {
        ConnectionScope scope = new ConnectionScope(left.getIdentifier(), right.getIdentifier(), direction);
        if (scope.getLeft().equals(left.getIdentifier())) {
            return new RecordConnectionScope(left, right, scope);
        } else {
            // Ordering has been reversed
            return new RecordConnectionScope(right, left, scope);
        }
    }

    public Optional<RecordConnectionScope> getTrace(BaselinePair baselines, Table type) {
        if (left.getTrace().isPresent() && right.getTrace().isPresent()) {
            ConnectionScope traceScope = new ConnectionScope(left.getTrace().get(), right.getTrace().get(), scope.getDirection());
            return resolve(baselines.getParent(), traceScope, type);
        }
        return Optional.empty();
    }

    public Stream<RecordConnectionScope> enumerateDirections(boolean includeNone) {
        Stream<RecordConnectionScope> result = scope.getDirection().stream()
                .map(dir -> this.setDirection(dir));
        if (includeNone) {
            result = Stream.concat(result, Stream.of(this.setDirection(Direction.None)));
        }
        return result;
    }

    public ConnectionScope getScope() {
        return scope;
    }

    private RecordConnectionScope(Record left, Record right, ConnectionScope scope) {
        this.left = left;
        this.right = right;
        this.scope = scope;
    }

    private final Record left;
    private final Record right;
    private final ConnectionScope scope;

    @CheckReturnValue
    public RecordConnectionScope setDirection(Direction value) {
        if (scope.getDirection() == value) {
            return this;
        } else {
            return new RecordConnectionScope(left, right, scope.setDirection(value));
        }
    }

    @CheckReturnValue
    public RecordConnectionScope setDirectionFrom(Record from, Direction value) {
        if (from.getIdentifier().equals(left.getIdentifier())) {
            // The from orientation is already our left
            return setDirection(value);
        } else if (from.getIdentifier().equals(right.getIdentifier())) {
            // The from orientation is reversed
            return setDirection(value.reverse());
        } else {
            throw new IllegalArgumentException(from + " is not in this scope");
        }
    }

    public Record otherEnd(Record t) throws IllegalArgumentException {
        if (t.getIdentifier().equals(left.getIdentifier())) {
            return right;
        } else if (t.getIdentifier().equals(right.getIdentifier())) {
            return left;
        } else {
            throw new IllegalArgumentException(t + " is not in this scope");
        }
    }

    public boolean hasEnd(Record t) {
        return t.getIdentifier().equals(left.getIdentifier())
                || t.getIdentifier().equals(right.getIdentifier());
    }

    public boolean isSelfConnection() {
        return scope.isSelfConnection();
    }

    public boolean hasSameEndsAs(RecordConnectionScope other) {
        return left.getIdentifier().equals(other.left.getIdentifier())
                && right.getIdentifier().equals(other.right.getIdentifier());
    }
}
