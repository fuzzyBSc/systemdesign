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
import au.id.soundadvice.systemdesign.beans.FlowBean;
import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.model.Baseline.BaselineAnd;
import au.id.soundadvice.systemdesign.relation.Reference;
import au.id.soundadvice.systemdesign.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.relation.Relation;
import au.id.soundadvice.systemdesign.relation.RelationContext;
import au.id.soundadvice.systemdesign.relation.RelationStore;
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
public class Flow implements BeanFactory<Baseline, FlowBean>, Relation {

    @Override
    public String toString() {
        return uuid.toString();
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
        final Flow other = (Flow) obj;
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.iface, other.iface)) {
            return false;
        }
        if (!Objects.equals(this.flowScope, other.flowScope)) {
            return false;
        }
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        return true;
    }

    /**
     * A transfer of information, energy or materials from one function to
     * another
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<Flow> find(Baseline baseline) {
        return baseline.getStore().getByClass(Flow.class);
    }

    @CheckReturnValue
    public static BaselineAnd<Flow> add(
            Baseline baseline,
            RelationPair<Function> endpoints,
            FlowType flowType) {
        return add(baseline,
                endpoints.getLeft(), endpoints.getRight(),
                flowType, endpoints.getDirection());
    }

    @CheckReturnValue
    public static BaselineAnd<Flow> add(
            Baseline baseline,
            Function left, Function right,
            FlowType flowType, Direction direction) {
        Interface iface;
        {
            RelationStore store = baseline.getStore();
            Item leftItem = left.getItem().getTarget(store);
            Item rightItem = right.getItem().getTarget(store);
            BaselineAnd<Interface> tmp = Interface.create(baseline, leftItem, rightItem);
            baseline = tmp.getBaseline();
            iface = tmp.getRelation();
        }

        Optional<Flow> existing = baseline.getFlow(
                new UUIDPair(left.getUuid(), right.getUuid()),
                flowType.getUuid());
        if (existing.isPresent()) {
            Flow flow = existing.get();
            Direction current = flow.getDirectionFrom(left);
            Direction updated = current.add(direction);
            if (current.equals(updated)) {
                // Nothing changed
                return baseline.and(flow);
            } else {
                flow = flow.setDirectionFrom(left, updated);
                return baseline.add(flow).and(flow);
            }
        } else {
            Flow flow = new Flow(
                    UUID.randomUUID(),
                    iface.getUuid(),
                    new UUIDPair(left.getUuid(), right.getUuid(), direction),
                    flowType.getUuid());
            return baseline.add(flow).and(flow);
        }
    }

    @CheckReturnValue
    public Baseline removeFrom(Baseline baseline) {
        return baseline.remove(uuid);
    }

    @CheckReturnValue
    public static Baseline remove(
            Baseline baseline, Function left, Function right, FlowType type, Direction direction) {
        Optional<Flow> existing = baseline.getFlow(
                new UUIDPair(left.getUuid(), right.getUuid()), type.getUuid());
        if (existing.isPresent()) {
            Flow existingFlow = existing.get();
            Direction current = existingFlow.getDirectionFrom(left);
            Direction remaining = current.remove(direction);
            if (current.equals(remaining)) {
                // No change
                return baseline;
            } else if (remaining == Direction.None) {
                // Remove the flow completely
                return baseline.remove(existingFlow.getUuid());
            } else {
                // Update the flow
                return baseline.update(existingFlow.getUuid(), Flow.class,
                        flow -> flow.setDirectionFrom(left, remaining));
            }
        } else {
            return baseline;
        }
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public Reference<Flow, Interface> getInterface() {
        return iface;
    }

    public Interface getInterface(Baseline baseline) {
        return iface.getTarget(baseline.getContext());
    }

    public Reference<Flow, Function> getLeft() {
        return left;
    }

    public Function getLeft(Baseline baseline) {
        return left.getTarget(baseline.getContext());
    }

    public Reference<Flow, Function> getRight() {
        return right;
    }

    public Function getRight(Baseline baseline) {
        return right.getTarget(baseline.getContext());
    }

    public Direction getDirection() {
        return flowScope.getDirection();
    }

    public Direction getDirectionFrom(Function from) {
        return flowScope.getDirectionFrom(from.getUuid());
    }

    public Reference<Flow, FlowType> getType() {
        return type;
    }

    public FlowType getType(Baseline baseline) {
        return type.getTarget(baseline.getContext());
    }

    public Flow(FlowBean bean) {
        this(bean.getUuid(),
                bean.getInterface(),
                new UUIDPair(bean.getLeft(), bean.getRight(), bean.getDirection()),
                bean.getTypeUUID());
    }

    private Flow(UUID uuid, UUID iface, UUIDPair flowScope, UUID type) {
        this.uuid = uuid;
        this.iface = new Reference<>(this, iface, Interface.class);
        this.flowScope = flowScope;
        this.left = new Reference<>(this, flowScope.getLeft(), Function.class);
        this.right = new Reference<>(this, flowScope.getRight(), Function.class);
        this.type = new Reference<>(this, type, FlowType.class);
    }

    private final UUID uuid;
    private final Reference<Flow, Interface> iface;
    private final UUIDPair flowScope;
    private final Reference<Flow, Function> left;
    private final Reference<Flow, Function> right;
    private final Reference<Flow, FlowType> type;

    @Override
    public FlowBean toBean(Baseline baseline) {
        StringBuilder builder = new StringBuilder();
        RelationStore store = baseline.getStore();
        Function leftEnd = left.getTarget(store);
        Function rightEnd = right.getTarget(store);
        FlowType flowType = type.getTarget(store);
        switch (flowScope.getDirection()) {
            case Normal:
                builder.append(leftEnd.getDisplayName(baseline));
                builder.append(" --").append(flowType).append("-> ");
                builder.append(rightEnd.getDisplayName(baseline));
                break;
            case Reverse:
                builder.append(rightEnd.getDisplayName(baseline));
                builder.append(" --").append(flowType).append("-> ");
                builder.append(leftEnd.getDisplayName(baseline));
                break;
            case Both:
                builder.append(leftEnd.getDisplayName(baseline));
                builder.append(" <-").append(flowType).append("-> ");
                builder.append(rightEnd.getDisplayName(baseline));
                break;
            default:
                throw new AssertionError(flowScope.toString());

        }

        return new FlowBean(
                uuid, iface.getUuid(),
                flowScope.getDirection(),
                left.getUuid(), right.getUuid(),
                type.getUuid(), builder.toString());
    }
    private static final ReferenceFinder<Flow> finder
            = new ReferenceFinder<>(Flow.class);

    @Override
    public Stream<Reference> getReferences() {
        return finder.getReferences(this);
    }

    @CheckReturnValue
    public BaselineAnd<Flow> setType(Baseline baseline, FlowType type) {
        if (this.type.getUuid().equals(type.getUuid())) {
            return baseline.and(this);
        } else {
            // Add flow direction(s) for the new type
            BaselineAnd<Flow> addResult = add(
                    baseline,
                    left.getTarget(baseline.getContext()),
                    right.getTarget(baseline.getContext()),
                    type, this.getDirection());
            baseline = addResult.getBaseline();
            Flow replacement = addResult.getRelation();
            // Remove ourselves
            baseline = baseline.remove(uuid);

            // See if the old type should be removed
            FlowType oldType = this.type.getTarget(baseline.getContext());
            if (!oldType.getFlows(baseline).findAny().isPresent()) {
                baseline = oldType.removeFrom(baseline);
            }
            return baseline.and(replacement);
        }
    }

    @CheckReturnValue
    private Flow setDirectionFrom(Function from, Direction value) {
        return new Flow(
                uuid, iface.getUuid(),
                flowScope.setDirectionFrom(from.getUuid(), value), type.getUuid());
    }

    public RelationPair<Function> getEndpoints(Baseline baseline) {
        return RelationPair.resolve(baseline, flowScope, Function.class).get();
    }

    public Function otherEnd(Baseline baseline, Function function) {
        UUID otherEndUUID = flowScope.otherEnd(function.getUuid());
        // Assume referential integrity is guaranteed by store
        return baseline.get(otherEndUUID, Function.class).get();
    }

    public Function otherEnd(Baseline baseline, Item item) {
        RelationContext store = baseline.getContext();
        Function leftFunction = left.getTarget(store);
        Function rightFunction = right.getTarget(store);
        if (leftFunction.getItem().getUuid().equals(item.getUuid())) {
            return rightFunction;
        } else if (rightFunction.getItem().getUuid().equals(item.getUuid())) {
            return leftFunction;
        } else {
            throw new IllegalArgumentException(this + " does not link to " + item);
        }
    }

    public boolean hasEnd(Function function) {
        return flowScope.hasEnd(function.getUuid());
    }

    public boolean hasEnd(Baseline baseline, Item item) {
        RelationContext store = baseline.getContext();
        Function leftFunction = left.getTarget(store);
        Function rightFunction = right.getTarget(store);
        return leftFunction.getItem().getUuid().equals(item.getUuid())
                || rightFunction.getItem().getUuid().equals(item.getUuid());
    }
}
