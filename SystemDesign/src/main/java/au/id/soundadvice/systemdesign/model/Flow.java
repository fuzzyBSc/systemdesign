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
import au.id.soundadvice.systemdesign.beans.FlowDirection;
import au.id.soundadvice.systemdesign.relation.Reference;
import au.id.soundadvice.systemdesign.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.relation.Relation;
import au.id.soundadvice.systemdesign.relation.RelationContext;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Flow implements BeanFactory<RelationContext, FlowBean>, Relation {

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
        if (!Objects.equals(this.left, other.left)) {
            return false;
        }
        if (!Objects.equals(this.right, other.right)) {
            return false;
        }
        if (this.direction != other.direction) {
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

    public Reference<Flow, FlowEnd> getLeft() {
        return left;
    }

    public Reference<Flow, FlowEnd> getRight() {
        return right;
    }

    public FlowDirection getDirection() {
        return direction;
    }

    public String getType() {
        return type;
    }

    public Flow(FlowBean bean) {
        this.uuid = bean.getUuid();
        this.direction = bean.getDirection();
        this.left = new Reference<>(this, bean.getLeft(), FlowEnd.class);
        this.right = new Reference<>(this, bean.getRight(), FlowEnd.class);
        this.type = bean.getType();
    }

    private final UUID uuid;
    private final Reference<Flow, FlowEnd> left;
    private final Reference<Flow, FlowEnd> right;
    private final FlowDirection direction;
    private final String type;

    @Override
    public FlowBean toBean(RelationContext context) {
        StringBuilder builder = new StringBuilder();
        FlowEnd leftEnd = left.getTarget(context);
        FlowEnd rightEnd = right.getTarget(context);
        switch (direction) {
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
            case Bidirectional:
                builder.append(leftEnd.getDisplayName());
                builder.append(" <-").append(type).append("-> ");
                builder.append(rightEnd.getDisplayName());
                break;
            default:
                throw new AssertionError(direction.name());

        }

        return new FlowBean(
                uuid, direction, left.getUuid(), right.getUuid(), type, builder.toString());
    }
    private static final ReferenceFinder<Flow> finder
            = new ReferenceFinder<>(Flow.class);

    @Override
    public Collection<Reference<?, ?>> getReferences() {
        return finder.getReferences(this);
    }
}
