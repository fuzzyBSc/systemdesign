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
import au.id.soundadvice.systemdesign.beans.FunctionBean;
import au.id.soundadvice.systemdesign.relation.Reference;
import au.id.soundadvice.systemdesign.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.relation.Relation;
import au.id.soundadvice.systemdesign.relation.RelationContext;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Function implements RequirementContext, BeanFactory<RelationContext, FunctionBean>, Relation {

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 83 * hash + Objects.hashCode(this.uuid);
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
        final Function other = (Function) obj;
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.item, other.item)) {
            return false;
        }
        if (!Objects.equals(this.trace, other.trace)) {
            return false;
        }
        if (this.external != other.external) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.origin, other.origin)) {
            return false;
        }
        return true;
    }

    public boolean isConsistent(Function other) {
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.item, other.item)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return true;
    }

    @CheckReturnValue
    public Function makeConsistent(Function other) {
        return new Function(
                other.getUuid(),
                other.getItem().getUuid(), trace, external,
                other.name, origin);
    }

    @Override
    public String toString() {
        return name;
    }

    private static Point2D defaultOrigin = new Point2D(200, 200);

    public static Function createNew(UUID item, String name) {
        return new Function(UUID.randomUUID(), item, Optional.empty(), false, name, defaultOrigin);
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public Reference<Function, Item> getItem() {
        return item;
    }

    public Optional<UUID> getTrace() {
        return this.trace;
    }

    public boolean isExternal() {
        return external;
    }

    public String getName() {
        return name;
    }

    public Function(FunctionBean bean) {
        this.uuid = bean.getUuid();
        this.trace = Optional.ofNullable(bean.getTrace());
        this.external = bean.isExternal();
        this.item = new Reference<>(this, bean.getItem(), Item.class);
        this.name = bean.getName();
        this.origin = new Point2D(bean.getOriginX(), bean.getOriginY());
    }

    private Function(UUID uuid, UUID item, Optional<UUID> trace, boolean external, String name, Point2D origin) {
        this.uuid = uuid;
        this.item = new Reference<>(this, item, Item.class);
        this.trace = trace;
        this.external = external;
        this.name = name;
        this.origin = origin;
    }

    private final UUID uuid;
    private final Reference<Function, Item> item;
    private final Optional<UUID> trace;
    private final boolean external;
    private final String name;
    private final Point2D origin;

    @Override
    public RequirementType getRequirementType() {
        return RequirementType.Functional;
    }

    @Override
    public FunctionBean toBean(RelationContext context) {
        return new FunctionBean(
                uuid, item.getUuid(), getDisplayName(context), trace, external, name,
                (int) origin.getX(), (int) origin.getY());
    }

    public String getDisplayName(RelationContext context) {
        return getDisplayName(item.getTarget(context));
    }

    public String getDisplayName(Item item) {
        StringBuilder builder = new StringBuilder();
        builder.append(name);
        builder.append(" (");
        builder.append(item.getDisplayName());
        builder.append(')');
        return builder.toString();
    }
    private static final ReferenceFinder<Function> finder
            = new ReferenceFinder<>(Function.class);

    @Override
    public Stream<Reference> getReferences() {
        return finder.getReferences(this);
    }

    @CheckReturnValue
    public Function setName(String value) {
        return new Function(uuid, item.getUuid(), trace, external, value, origin);
    }

    @CheckReturnValue
    public Function setTrace(UUID value) {
        return new Function(uuid, item.getUuid(), Optional.of(value), external, name, origin);
    }

    @CheckReturnValue
    public Function setOrigin(Point2D value) {
        return new Function(uuid, item.getUuid(), trace, external, name, value);
    }

    public Point2D getOrigin() {
        return origin;
    }

    public Function asExternal(UUID allocateToParentFunction) {
        return new Function(
                uuid, item.getUuid(),
                Optional.of(allocateToParentFunction), true,
                name, origin);
    }
}
