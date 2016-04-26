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
package au.id.soundadvice.systemdesign.logical;

import au.id.soundadvice.systemdesign.logical.beans.FlowTypeBean;
import au.id.soundadvice.systemdesign.moduleapi.relation.Reference;
import au.id.soundadvice.systemdesign.moduleapi.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

/**
 * A flow represents the transfer of information, energy and/or materials from
 * one function to another.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FlowType implements Relation {

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 23 * hash + Objects.hashCode(this.identifier);
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
        final FlowType other = (FlowType) obj;
        if (!Objects.equals(this.identifier, other.identifier)) {
            return false;
        }
        if (!Objects.equals(this.trace, other.trace)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return true;
    }

    /**
     * A distinct combination of information, energy and materials
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<FlowType> find(Relations baseline) {
        return baseline.findByClass(FlowType.class);
    }

    public static Optional<FlowType> find(Relations baseline, String name) {
        return find(baseline)
                .filter(type -> name.equals(type.name))
                .findAny();
    }

    public static FlowType create(Optional<FlowType> trace, String name) {
        return new FlowType(UUID.randomUUID().toString(), trace.map(FlowType::getIdentifier), name);
    }

    @CheckReturnValue
    public static Pair<Relations, FlowType> add(
            Relations baseline, Optional<FlowType> trace, String name) {
        Optional<FlowType> existing = find(baseline, name);
        if (existing.isPresent()) {
            return new Pair<>(baseline, existing.get());
        } else {
            return addUnchecked(baseline, trace, name);
        }
    }

    @CheckReturnValue
    public static Pair<Relations, FlowType> addUnchecked(
            Relations baseline, Optional<FlowType> trace, String name) {
        FlowType flowType = create(trace, name);
        return new Pair<>(baseline.add(flowType), flowType);
    }

    @CheckReturnValue
    public static Relations remove(Relations baseline, String name) {
        Optional<FlowType> existing = find(baseline, name);
        if (existing.isPresent()) {
            return existing.get().removeFrom(baseline);
        } else {
            return baseline;
        }
    }

    @CheckReturnValue
    public Relations removeFrom(Relations baseline) {
        return baseline.remove(identifier);
    }

    @CheckReturnValue
    public Pair<Relations, FlowType> setTrace(
            Relations baseline, Optional<FlowType> traceobj) {
        Optional<String> traceIdentifier = traceobj.map(FlowType::getIdentifier);
        if (traceIdentifier.equals(this.trace)) {
            return new Pair<>(baseline, this);
        } else {
            FlowType replacement = new FlowType(identifier, traceIdentifier, name);
            return new Pair<>(baseline.add(replacement), replacement);
        }
    }

    @CheckReturnValue
    public Pair<Relations, FlowType> setName(Relations baseline, String name) {
        if (name.equals(this.name)) {
            return new Pair<>(baseline, this);
        } else {
            FlowType replacement = new FlowType(identifier, trace, name);
            return new Pair<>(baseline.add(replacement), replacement);
        }
    }

    public boolean isTraced() {
        return trace.isPresent();
    }

    public Optional<FlowType> getTrace(Relations functional) {
        return trace.flatMap(traceUUID -> functional.get(traceUUID, FlowType.class));
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    public String getName() {
        return name;
    }

    public FlowType(FlowTypeBean bean) {
        this(bean.getIdentifier(), Optional.ofNullable(bean.getTrace()), bean.getName());
    }

    private FlowType(String identifier, Optional<String> trace, String name) {
        this.identifier = identifier;
        this.trace = trace;
        this.name = name;
    }

    private final String identifier;
    private final Optional<String> trace;
    private final String name;

    public FlowTypeBean toBean() {
        return new FlowTypeBean(identifier, trace, name);
    }
    private static final ReferenceFinder<FlowType> FINDER
            = new ReferenceFinder<>(FlowType.class);

    @Override
    public Stream<Reference> getReferences() {
        return FINDER.getReferences(this);
    }

    public Stream<Flow> getFlows(Relations baseline) {
        return baseline.findReverse(identifier, Flow.class);
    }
}
