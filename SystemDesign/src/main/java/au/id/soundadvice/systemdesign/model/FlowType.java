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

import au.id.soundadvice.systemdesign.beans.BeanFactory;
import au.id.soundadvice.systemdesign.beans.FlowTypeBean;
import au.id.soundadvice.systemdesign.model.Baseline.BaselineAnd;
import au.id.soundadvice.systemdesign.relation.Reference;
import au.id.soundadvice.systemdesign.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.relation.Relation;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 * A flow represents the transfer of information, energy and/or materials from
 * one function to another.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FlowType implements BeanFactory<Baseline, FlowTypeBean>, Relation {

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 23 * hash + Objects.hashCode(this.uuid);
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
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return true;
    }

    public static Optional<FlowType> find(Baseline baseline, String name) {
        return baseline.getFlowTypes()
                .filter(type -> name.equals(type.name))
                .findAny();
    }

    public static FlowType create(String name) {
        return new FlowType(UUID.randomUUID(), name);
    }

    @CheckReturnValue
    public static BaselineAnd<FlowType> add(Baseline baseline, String name) {
        Optional<FlowType> existing = find(baseline, name);
        if (existing.isPresent()) {
            return baseline.and(existing.get());
        } else {
            FlowType flowType = create(name);
            return baseline.add(flowType).and(flowType);
        }
    }

    @CheckReturnValue
    public static Baseline remove(Baseline baseline, String name) {
        Optional<FlowType> existing = find(baseline, name);
        if (existing.isPresent()) {
            return existing.get().removeFrom(baseline);
        } else {
            return baseline;
        }
    }

    @CheckReturnValue
    public Baseline removeFrom(Baseline baseline) {
        return baseline.remove(uuid);
    }

    @CheckReturnValue
    public BaselineAnd<FlowType> addTo(Baseline baseline) {
        Optional<FlowType> existing = find(baseline, name);
        if (existing.isPresent()) {
            return existing.get().setUuid(baseline, uuid);
        } else {
            return baseline.add(this).and(this);
        }
    }

    @CheckReturnValue
    public BaselineAnd<FlowType> setName(Baseline baseline, String name) {
        if (name.equals(this.name)) {
            return baseline.and(this);
        } else {
            FlowType replacement = new FlowType(uuid, name);
            return baseline.add(replacement).and(replacement);
        }
    }

    public BaselineAnd<FlowType> setUuid(Baseline baseline, UUID uuid) {
        if (uuid.equals(this.uuid)) {
            return baseline.and(this);
        }
        /*
         * We need to ensure referential integrity at each step
         */
        // Add replacement
        FlowType replacement = new FlowType(uuid, name);
        baseline = baseline.add(replacement);

        // Update flows to point to replacement
        Iterator<Flow> it = baseline.getStore().getReverse(this.uuid, Flow.class).iterator();
        while (it.hasNext()) {
            Flow flow = it.next();
            baseline = flow.setType(baseline, replacement).getBaseline();
        }

        // Remove ourselves
        return baseline.remove(this.uuid).and(replacement);
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public FlowType(FlowTypeBean bean) {
        this(bean.getUuid(), bean.getName());
    }

    private FlowType(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    private final UUID uuid;
    private final String name;

    @Override
    public FlowTypeBean toBean(Baseline baseline) {
        return new FlowTypeBean(uuid, name);
    }
    private static final ReferenceFinder<FlowType> finder
            = new ReferenceFinder<>(FlowType.class);

    @Override
    public Stream<Reference> getReferences() {
        return finder.getReferences(this);
    }

    public Stream<Flow> getFlows(Baseline baseline) {
        return baseline.getReverse(uuid, Flow.class);
    }
}
