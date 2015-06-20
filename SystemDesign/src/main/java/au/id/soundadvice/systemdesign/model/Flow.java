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
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Flow implements BeanFactory<RelationContext, FlowBean>, Relation {

    public static Optional<Flow> findExisting(
            RelationStore context, UndirectedPair functions, String type) {
        return context.getReverse(functions.getLeft(), Flow.class).parallel()
                .filter((candidate) -> {
                    return functions.getRight().equals(candidate.getRight().getUuid())
                    && type.equals(candidate.getType());
                }).findAny();
    }

    @Override
    public String toString() {
        return type;
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

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public Reference<Flow, Interface> getInterface() {
        return iface;
    }

    public Reference<Flow, Function> getLeft() {
        return left;
    }

    public Reference<Flow, Function> getRight() {
        return right;
    }

    public Direction getDirection() {
        return flowScope.getDirection();
    }

    public Direction getDirectionFrom(Function from) {
        return flowScope.getDirectionFrom(from.getUuid());
    }

    public String getType() {
        return type;
    }

    public static Flow createNew(UUID iface, DirectedPair scope, String type) {
        return new Flow(UUID.randomUUID(), iface, scope, type);
    }

    public Flow(FlowBean bean) {
        this(bean.getUuid(),
                bean.getInterface(),
                new DirectedPair(bean.getLeft(), bean.getRight(), bean.getDirection()),
                bean.getType());
    }

    private Flow(UUID uuid, UUID iface, DirectedPair flowScope, String type) {
        this.uuid = uuid;
        this.iface = new Reference<>(this, iface, Interface.class);
        this.flowScope = flowScope;
        this.left = new Reference<>(this, flowScope.getLeft(), Function.class);
        this.right = new Reference<>(this, flowScope.getRight(), Function.class);
        this.type = type;
    }

    private final UUID uuid;
    private final Reference<Flow, Interface> iface;
    private final DirectedPair flowScope;
    private final Reference<Flow, Function> left;
    private final Reference<Flow, Function> right;
    private final String type;

    @Override
    public FlowBean toBean(RelationContext context) {
        StringBuilder builder = new StringBuilder();
        Function leftEnd = left.getTarget(context);
        Function rightEnd = right.getTarget(context);
        switch (flowScope.getDirection()) {
            case Normal:
                builder.append(leftEnd.getDisplayName());
                builder.append(" --").append(type).append("-> ");
                builder.append(rightEnd.getDisplayName());
                break;
            case Reverse:
                builder.append(rightEnd.getDisplayName());
                builder.append(" --").append(type).append("-> ");
                builder.append(leftEnd.getDisplayName());
                break;
            case Both:
                builder.append(leftEnd.getDisplayName());
                builder.append(" <-").append(type).append("-> ");
                builder.append(rightEnd.getDisplayName());
                break;
            default:
                throw new AssertionError(flowScope.toString());

        }

        return new FlowBean(
                uuid, iface.getUuid(),
                flowScope.getDirection(),
                left.getUuid(), right.getUuid(),
                type, builder.toString());
    }
    private static final ReferenceFinder<Flow> finder
            = new ReferenceFinder<>(Flow.class);

    @Override
    public Stream<Reference> getReferences() {
        return finder.getReferences(this);
    }

    @CheckReturnValue
    public Flow setType(String value) {
        return new Flow(uuid, iface.getUuid(), flowScope, value);
    }

    @CheckReturnValue
    public Flow setDirection(Direction value) {
        return new Flow(
                uuid, iface.getUuid(),
                flowScope.setDirection(value), type);
    }

    @CheckReturnValue
    public Flow setDirectionFrom(Function from, Direction value) {
        return new Flow(
                uuid, iface.getUuid(),
                flowScope.setDirectionFrom(from.getUuid(), value), type);
    }

    public DirectedPair getScope() {
        return flowScope;
    }

    public Function otherEnd(RelationContext store, Function function) {
        UUID otherEndUUID = flowScope.otherEnd(function.getUuid());
        return store.get(otherEndUUID, Function.class);
    }

    public Function otherEnd(RelationContext store, Item item) {
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
}
