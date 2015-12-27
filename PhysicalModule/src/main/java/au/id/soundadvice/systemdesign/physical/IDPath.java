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
package au.id.soundadvice.systemdesign.physical;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 * A hierarchical identifier suitable for items.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class IDPath implements Comparable<IDPath> {

    private static final char SEP = '.';
    private static final char NOT_SEP = '_';

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

    @Override
    public String toString() {
        return dotted;
    }

    /**
     * Build an IDPath from a dotted representation
     *
     * @param dotted A string containing '.' separator for path segments
     * @return
     */
    public static IDPath valueOfDotted(String dotted) {
        if (dotted == null || dotted.isEmpty()) {
            return EMPTY;
        } else {
            List<String> segments = Collections.unmodifiableList(
                    Arrays.asList(dotted.split("\\.")));
            return new IDPath(segments, dotted);
        }
    }

    /**
     * Build an IDPath from a single path segment. All '.' characters will be
     * replaced.
     *
     * @param segment A segment of the path
     * @return
     */
    public static IDPath valueOfSegment(String segment) {
        return valueOf(Stream.of(segment));
    }

    /**
     * Build an IDPath from a series of path segments. '.' characters in each
     * segment will be replaced.
     *
     * @param stream A stream of path segments
     * @return
     */
    public static IDPath valueOf(Stream<String> stream) {
        List<String> segments = Collections.unmodifiableList(
                stream.map(segment -> segment.replace(SEP, NOT_SEP))
                .collect(Collectors.toList()));
        String dotted = segments.stream().collect(Collectors.joining("."));

        return new IDPath(segments, dotted);
    }

    private static final IDPath EMPTY = new IDPath(
            Collections.<String>emptyList(), "");

    /**
     * Return the empty path. This is the identity of a model's top level
     * context.
     *
     * @return
     */
    public static IDPath empty() {
        return EMPTY;
    }

    private IDPath(List<String> unmodifiableSegments, String dotted) {
        this.segments = unmodifiableSegments;
        this.dotted = dotted;
    }

    /**
     * Return true if this path has a non-empty parent path.
     *
     * @return
     */
    public boolean hasParent() {
        return segments.size() > 1;
    }

    /**
     * Return true if this path is the empty path.
     *
     * @return
     */
    public boolean isEmpty() {
        return segments.isEmpty();
    }

    /**
     * Return the parent of this path. The last segment of this path will be
     * returned. Repeated invocations of this function will eventually lead to
     * the empty path. The parent of the empty path is the empty path.
     *
     * @return
     */
    public IDPath getParent() {
        if (hasParent()) {
            List<String> parentSegments = segments.subList(0, segments.size() - 1);
            int lastDot = dotted.lastIndexOf('.');
            return new IDPath(parentSegments, dotted.substring(0, lastDot));
        } else {
            return EMPTY;
        }
    }

    /**
     * Return a direct child of this path. The specified segment has its '.'
     * characters replaced and is placed at the end of this path.
     *
     * @param segment The segment to add to this path
     * @return
     */
    @CheckReturnValue
    public IDPath resolveSegment(String segment) {
        return resolve(valueOfSegment(segment));
    }

    /**
     * Return a descendant of this path. The specified path is appended to the
     * current path.
     *
     * @param relativePath The path segments to add
     * @return
     */
    @CheckReturnValue
    public IDPath resolve(IDPath relativePath) {
        if (relativePath.isEmpty()) {
            return this;
        } else if (this.isEmpty()) {
            return relativePath;
        } else {
            List<String> newSegments = new ArrayList<>(
                    segments.size() + relativePath.segments.size());
            newSegments.addAll(segments);
            newSegments.addAll(relativePath.segments);
            return new IDPath(newSegments, dotted + "." + relativePath.dotted);
        }
    }

    private final List<String> segments;
    private final String dotted;

    @Override
    public int compareTo(IDPath other) {
        return compare(this, other);
    }

    public static int compare(IDPath left, IDPath right) {
        Iterator<String> leftIt = left.segments.iterator();
        Iterator<String> rightIt = right.segments.iterator();
        for (;;) {
            if (leftIt.hasNext()) {
                if (rightIt.hasNext()) {
                    String leftSegment = leftIt.next();
                    String rightSegment = rightIt.next();

                    // Probably not a true partial ordering, but will do for now
                    try {
                        int leftInt = Integer.parseInt(leftSegment);
                        int rightInt = Integer.parseInt(rightSegment);
                        if (leftInt < rightInt) {
                            return -1;
                        }
                        if (leftInt > rightInt) {
                            return 1;
                        }
                    } catch (NumberFormatException ex) {
                        int result = leftSegment.compareTo(rightSegment);
                        if (result != 0) {
                            return result;
                        }
                    }
                } else {
                    // Right is shorter, so greater
                    return 1;
                }
            } else if (rightIt.hasNext()) {
                // Left is shorter, so less
                return -1;
            } else {
                // Equal
                return 0;
            }
        }
    }
}
