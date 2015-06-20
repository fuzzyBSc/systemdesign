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
import java.util.Objects;
import java.util.UUID;

/**
 * An ordered pair of UUID, useful for describing the scope of a connection
 * between other relations. There should be at most one Interface matching any
 * given InterfaceScope.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class UndirectedPair {

    @Override
    public String toString() {
        return delegate.getLeft() + ":" + delegate.getRight();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.delegate);
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
        final UndirectedPair other = (UndirectedPair) obj;
        if (!Objects.equals(this.delegate, other.delegate)) {
            return false;
        }
        return true;
    }

    public UUID getLeft() {
        return delegate.getLeft();
    }

    public UUID getRight() {
        return delegate.getRight();
    }

    public UndirectedPair(DirectedPair pair) {
        this.delegate = new DirectedPair(
                pair.getLeft(), pair.getRight(), Direction.Both);
    }

    public UndirectedPair(UUID left, UUID right) {
        this.delegate = new DirectedPair(left, right, Direction.Both);
    }

    private final DirectedPair delegate;

    public UUID otherEnd(UUID uuid) throws IllegalArgumentException {
        return delegate.otherEnd(uuid);
    }

    public boolean hasEnd(UUID uuid) {
        return delegate.hasEnd(uuid);
    }
}
