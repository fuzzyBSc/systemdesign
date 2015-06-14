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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class IDPath implements Comparable<IDPath> {

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.dotted);
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
        final IDPath other = (IDPath) obj;
        if (!Objects.equals(this.dotted, other.dotted)) {
            return false;
        }
        return true;
    }

    public String getDotted() {
        return dotted;
    }

    public String getRelativeId() {
        int dotPos = dotted.indexOf(IDSegment.sep);
        if (dotPos >= 0) {
            return dotted.substring(dotPos + 1);
        } else {
            return dotted;
        }
    }

    @Override
    public String toString() {
        return dotted;
    }

    public List<IDSegment> getSegments() {
        return segments;
    }

    public static IDPath valueOf(String dotted) {
        if (dotted == null || dotted.isEmpty()) {
            return new IDPath(Collections.<IDSegment>emptyList(), "");
        } else {
            List<IDSegment> segments = new ArrayList<>();
            for (String text : dotted.split("\\.")) {
                segments.add(new IDSegment(text));
            }
            return new IDPath(segments, dotted);
        }
    }

    public static IDPath valueOf(List<IDSegment> segments) {
        segments = Collections.unmodifiableList(new ArrayList<>(segments));
        return new IDPath(segments, buildDotted(segments));
    }

    private static final IDPath empty = new IDPath(
            Collections.<IDSegment>emptyList(), "");

    public static IDPath empty() {
        return empty;
    }

    private static String buildDotted(List<IDSegment> segments) {
        StringBuilder builder = new StringBuilder();
        String sep = "";
        for (IDSegment segment : segments) {
            builder.append(sep);
            builder.append(segment);
            sep = ".";
        }
        return builder.toString();
    }

    private IDPath(List<IDSegment> unmodifiableSegments, String dotted) {
        this.segments = unmodifiableSegments;
        this.dotted = dotted;
    }

    public boolean hasParent() {
        return segments.size() > 1;
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    public IDPath getParent() {
        if (hasParent()) {
            List<IDSegment> parentSegments = segments.subList(0, segments.size() - 1);
            int lastDot = dotted.lastIndexOf('.');
            return new IDPath(parentSegments, dotted.substring(0, lastDot));
        } else {
            return empty;
        }
    }

    public IDPath getChild(IDSegment childSegment) {
        if (segments.isEmpty()) {
            return new IDPath(
                    Collections.singletonList(childSegment),
                    childSegment.toString());
        } else {
            List<IDSegment> childSegments = new ArrayList<>(segments.size() + 1);
            childSegments.addAll(segments);
            childSegments.add(childSegment);
            return new IDPath(childSegments, dotted + "." + childSegment);
        }
    }

    public IDPath getChild(IDPath childSegments) {
        if (childSegments.isEmpty()) {
            return this;
        } else if (this.isEmpty()) {
            return childSegments;
        } else {
            List<IDSegment> newSegments = new ArrayList<>(
                    segments.size() + childSegments.segments.size());
            newSegments.addAll(segments);
            newSegments.addAll(childSegments.segments);
            return new IDPath(newSegments, dotted + "." + childSegments.dotted);
        }
    }

    private final List<IDSegment> segments;
    private final String dotted;

    @Override
    public int compareTo(IDPath other) {
        return compare(this, other);
    }

    public static int compare(IDPath left, IDPath right) {
        Iterator<IDSegment> leftIt = left.segments.iterator();
        Iterator<IDSegment> rightIt = right.segments.iterator();
        for (;;) {
            if (leftIt.hasNext()) {
                if (rightIt.hasNext()) {
                    IDSegment leftSegment = leftIt.next();
                    IDSegment rightSegment = rightIt.next();
                    int result = leftSegment.compareTo(rightSegment);
                    if (result != 0) {
                        return result;
                    }
                } else {
                    // Right is shorter, so greater
                    return 1;
                }
            } else {
                if (rightIt.hasNext()) {
                    // Left is shorter, so less
                    return -1;
                } else {
                    // Equal
                    return 0;
                }
            }
        }
    }
}
