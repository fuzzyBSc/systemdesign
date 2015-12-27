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

import au.id.soundadvice.systemdesign.moduleapi.UUIDPair;
import au.id.soundadvice.systemdesign.moduleapi.RelationPair;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.physical.Interface;
import au.id.soundadvice.systemdesign.logical.beans.FlowBean;
import au.id.soundadvice.systemdesign.moduleapi.Direction;
import au.id.soundadvice.systemdesign.moduleapi.relation.Reference;
import au.id.soundadvice.systemdesign.moduleapi.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import javafx.util.Pair;

/**
 * A flow represents the transfer of information, energy and/or materials from
 * one function to another.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Flow implements Relation {

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
    public static Stream<Flow> find(Relations baseline) {
        return baseline.findByClass(Flow.class);
    }

    public static Stream<Flow> find(Relations baseline, Interface iface) {
        return baseline.findReverse(iface.getUuid(), Flow.class);
    }

    public static Optional<Flow> get(
            Relations baseline, Function left, Function right, FlowType type) {
        return get(baseline, left.getUuid(), right.getUuid(), type.getUuid());
    }

    private static Optional<Flow> get(
            Relations baseline, UUID leftFunction, UUID rightFunction, UUID type) {
        return baseline.findReverse(leftFunction, Flow.class).parallel()
                .filter(candidate -> {
                    return rightFunction.equals(candidate.flowScope.otherEnd(leftFunction))
                            && type.equals(candidate.getType().getUuid());
                }).findAny();
    }

    @CheckReturnValue
    public static Pair<Relations, Flow> add(
            Relations baseline,
            RelationPair<Function> endpoints,
            FlowType flowType) {
        return add(baseline,
                endpoints.getLeft(), endpoints.getRight(),
                flowType, endpoints.getDirection());
    }

    @CheckReturnValue
    public static Pair<Relations, Flow> add(
            Relations baseline,
            Function left, Function right,
            FlowType flowType, Direction direction) {
        Interface iface;
        {
            Item leftItem = left.getItem().getTarget(baseline);
            Item rightItem = right.getItem().getTarget(baseline);
            Pair<Relations, Interface> tmp = Interface.create(baseline, leftItem, rightItem);
            baseline = tmp.getKey();
            iface = tmp.getValue();
        }

        Optional<Flow> existing = get(baseline, left, right, flowType);
        if (existing.isPresent()) {
            Flow flow = existing.get();
            Direction current = flow.getDirectionFrom(left);
            Direction updated = current.add(direction);
            if (current.equals(updated)) {
                // Nothing changed
                return new Pair<>(baseline, flow);
            } else {
                flow = flow.setDirectionFrom(left, updated);
                return new Pair<>(baseline.add(flow), flow);
            }
        } else {
            Flow flow = new Flow(
                    UUID.randomUUID(),
                    iface.getUuid(),
                    new UUIDPair(left.getUuid(), right.getUuid(), direction),
                    flowType.getUuid());
            return new Pair<>(baseline.add(flow), flow);
        }
    }

    @CheckReturnValue
    public Relations removeFrom(Relations baseline) {
        return baseline.remove(uuid);
    }

    @CheckReturnValue
    public static Relations remove(
            Relations baseline, Function left, Function right, FlowType type, Direction direction) {
        Optional<Flow> existing = get(baseline, left, right, type);
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
                Flow updated = existingFlow.setDirectionFrom(left, remaining);
                return baseline.add(updated);
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

    public Interface getInterface(Relations baseline) {
        return iface.getTarget(baseline);
    }

    public Reference<Flow, Function> getLeft() {
        return left;
    }

    public Function getLeft(Relations baseline) {
        return left.getTarget(baseline);
    }

    public Reference<Flow, Function> getRight() {
        return right;
    }

    public Function getRight(Relations baseline) {
        return right.getTarget(baseline);
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

    public FlowType getType(Relations baseline) {
        return type.getTarget(baseline);
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

    public FlowBean toBean(Relations baseline) {
        StringBuilder builder = new StringBuilder();
        Function leftEnd = left.getTarget(baseline);
        Function rightEnd = right.getTarget(baseline);
        FlowType flowType = type.getTarget(baseline);
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
    private static final ReferenceFinder<Flow> FINDER
            = new ReferenceFinder<>(Flow.class);

    @Override
    public Stream<Reference> getReferences() {
        return FINDER.getReferences(this);
    }

    @CheckReturnValue
    public Pair<Relations, Flow> setType(Relations baseline, FlowType type) {
        if (this.type.getUuid().equals(type.getUuid())) {
            return new Pair<>(baseline, this);
        } else {
            // Add flow direction(s) for the new type
            Pair<Relations, Flow> addResult = add(
                    baseline,
                    left.getTarget(baseline),
                    right.getTarget(baseline),
                    type, this.getDirection());
            baseline = addResult.getKey();
            Flow replacement = addResult.getValue();
            // Remove ourselves
            baseline = baseline.remove(uuid);

            // See if the old type should be removed
            FlowType oldType = this.type.getTarget(baseline);
            if (!oldType.getFlows(baseline).findAny().isPresent()) {
                baseline = oldType.removeFrom(baseline);
            }
            return new Pair<>(baseline, replacement);
        }
    }

    @CheckReturnValue
    private Flow setDirectionFrom(Function from, Direction value) {
        return new Flow(
                uuid, iface.getUuid(),
                flowScope.setDirectionFrom(from.getUuid(), value), type.getUuid());
    }

    public RelationPair<Function> getEndpoints(Relations baseline) {
        return RelationPair.resolve(baseline, flowScope, Function.class).get();
    }

    public Function otherEnd(Relations baseline, Function function) {
        UUID otherEndUUID = flowScope.otherEnd(function.getUuid());
        // Assume referential integrity is guaranteed by store
        return baseline.get(otherEndUUID, Function.class).get();
    }

    public Function otherEnd(Relations baseline, Item item) {
        Function leftFunction = left.getTarget(baseline);
        Function rightFunction = right.getTarget(baseline);
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

    public boolean hasEnd(Relations baseline, Item item) {
        Function leftFunction = left.getTarget(baseline);
        Function rightFunction = right.getTarget(baseline);
        return leftFunction.getItem().getUuid().equals(item.getUuid())
                || rightFunction.getItem().getUuid().equals(item.getUuid());
    }
}
