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

import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public interface Table {

    public String getTableName();

    /**
     * Get a sequence of unique constraints for a given record
     *
     * @return An object encapsulating the primary key
     */
    public Stream<UniqueConstraint> getUniqueConstraints();

    public Record merge(WhyHowPair<Baseline> context, String now, Record left, Record right);

    public Stream<Problem> getTraceProblems(
            WhyHowPair<Baseline> context, Record traceParent, Stream<Record> traceChildren);

    public Stream<Problem> getUntracedParentProblems(
            WhyHowPair<Baseline> context, Stream<Record> untracedParents);

    public Stream<Problem> getUntracedChildProblems(
            WhyHowPair<Baseline> context, Stream<Record> untracedChildren);

    public Comparator<Record> getNaturalOrdering();

    public class Default implements Table {

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 71 * hash + Objects.hashCode(this.name);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Default other = (Default) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return true;
        }

        public Default(String name) {
            this.name = name;
        }

        private final String name;

        @Override
        public String getTableName() {
            return name;
        }

        @Override
        public Stream<Problem> getTraceProblems(WhyHowPair<Baseline> context, Record traceParent, Stream<Record> traceChild) {
            return Stream.empty();
        }

        @Override
        public Stream<Problem> getUntracedParentProblems(WhyHowPair<Baseline> context, Stream<Record> traceParent) {
            return Stream.empty();
        }

        @Override
        public Stream<Problem> getUntracedChildProblems(WhyHowPair<Baseline> context, Stream<Record> traceChild) {
            return Stream.empty();
        }

        @Override
        public Stream<UniqueConstraint> getUniqueConstraints() {
            return Stream.empty();
        }

        @Override
        public Record merge(WhyHowPair<Baseline> baselines, String now, Record left, Record right) {
            return Record.newerOf(left, right);
        }

        @Override
        public Comparator<Record> getNaturalOrdering() {
            return (a, b) -> a.getShortName().compareTo(b.getShortName());
        }
    }
}
