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

import java.util.Objects;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class IDSegment implements Comparable<IDSegment> {

    @Override
    public String toString() {
        return segment;
    }

    public static final char sep = '.';
    public static final char notSep = '_';

    public IDSegment(String segment) {
        this.segment = segment.replace(sep, notSep);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + Objects.hashCode(this.segment);
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
        final IDSegment other = (IDSegment) obj;
        if (!Objects.equals(this.segment, other.segment)) {
            return false;
        }
        return true;
    }

    private final String segment;

    @Override
    public int compareTo(IDSegment other) {
        try {
            // Probably not a true partial ordering, but will do for now
            int leftInt = Integer.parseInt(segment);
            int rightInt = Integer.parseInt(other.segment);
            if (leftInt < rightInt) {
                return -1;
            } else if (leftInt == rightInt) {
                return 0;
            } else {
                return 1;
            }
        } catch (NumberFormatException ex) {
            return segment.compareTo(other.segment);
        }
    }
}
