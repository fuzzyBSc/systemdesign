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
package au.id.soundadvice.systemdesign.beans;

import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 * A set of Normal and Reverse directions, but as an enum for easy serialisation
 * etc.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum Direction {

    /**
     * No direction. A flow with no direction should be deleted. An interface
     * should never have a direction.
     */
    None(0),
    /**
     * Left to right across the interface.
     */
    Normal(1),
    /**
     * Right to left across the interface.
     */
    Reverse(2),
    /**
     * The flow is in both directions.
     */
    Both(3);

    private Direction(int mask) {
        this.mask = mask;
    }

    private final int mask;

    public Stream<Direction> stream() {
        switch (this) {
            case None:
                return Stream.empty();
            case Normal:
            case Reverse:
                return Stream.of(this);
            case Both:
                return Stream.of(Normal, Reverse);
            default:
                throw new AssertionError(this.name());

        }
    }

    private static Direction fromMask(int value) {
        switch (value) {
            case 0:
                return None;
            case 1:
                return Normal;
            case 2:
                return Reverse;
            case 3:
                return Both;
            default:
                throw new AssertionError(Integer.toString(value));
        }
    }

    @CheckReturnValue
    public Direction reverse() {
        switch (this) {
            case Normal:
                return Reverse;
            case Reverse:
                return Normal;
            case None:
            case Both:
                return this;
            default:
                throw new AssertionError(this.name());
        }
    }

    @CheckReturnValue
    public Direction add(Direction other) {
        return fromMask(this.mask | other.mask);
    }

    @CheckReturnValue
    public Direction remove(Direction other) {
        return fromMask(this.mask & ~other.mask);
    }

    public boolean contains(Direction direction) {
        return (direction.mask & ~this.mask) == 0;
    }
}
