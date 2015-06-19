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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A set of Normal and Reverse directions, but as an enum for easy serialisation
 * etc.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum Direction {

    /**
     * No direction - the flow should be deleted.
     *//**
     * No direction - the flow should be deleted.
     */
    None,
    /**
     * Left to right across the interface.
     */
    Normal,
    /**
     * Right to left across the interface.
     */
    Reverse,
    /**
     * The flow is in both directions.
     */
    Both;

    public Set<Direction> asSet() {
        switch (this) {
            case None:
                return Collections.emptySet();
            case Normal:
            case Reverse:
                return EnumSet.of(this);
            case Both:
                return EnumSet.of(Normal, Reverse);
            default:
                throw new AssertionError(this.name());

        }
    }

    public Direction valueOf(Set<Direction> set) {
        if (set.contains(Both)) {
            return Both;
        } else if (set.contains(Normal)) {
            if (set.contains(Reverse)) {
                return Both;
            } else {
                return Normal;
            }
        } else {
            if (set.contains(Reverse)) {
                return Reverse;
            } else {
                return None;
            }
        }
    }

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

    public Direction add(Direction other) {
        switch (this) {
            case None:
                return other;
            case Both:
                return this;
            case Normal:
            case Reverse:
                if (other == this) {
                    return this;
                } else {
                    return Both;
                }
            default:
                throw new AssertionError(this.name());
        }
    }

    public Direction remove(Direction other) {
        switch (other) {
            case Both:
                return None;
            case None:
                return this;
            case Normal:
            case Reverse:
                if (other == this) {
                    return None;
                } else {
                    return this;
                }
            default:
                throw new AssertionError(this.name());
        }
    }
}
