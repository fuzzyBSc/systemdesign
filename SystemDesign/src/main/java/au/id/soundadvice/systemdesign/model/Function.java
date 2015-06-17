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
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Function implements RequirementContext, BeanFactory<RelationContext, FunctionBean>, FlowEnd, Relation {

    @Override
    public String toString() {
        return getDisplayName();
    }

    public static Function create(UUID item, String name) {
        return new Function(UUID.randomUUID(), item, null, name);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.uuid);
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
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public Reference<Function, Item> getItem() {
        return item;
    }

    @Nullable
    public UUID getTrace() {
        return this.trace;
    }

    public String getName() {
        return name;
    }

    public Function(FunctionBean bean) {
        this.uuid = bean.getUuid();
        this.trace = bean.getTrace();
        this.item = new Reference<>(this, bean.getItem(), Item.class);
        this.name = bean.getName();
    }

    public Function(UUID uuid, UUID item, @Nullable UUID trace, String name) {
        this.uuid = uuid;
        this.item = new Reference<>(this, item, Item.class);
        this.trace = trace;
        this.name = name;
    }

    private final UUID uuid;
    private final Reference<Function, Item> item;
    @Nullable
    private final UUID trace;
    private final String name;

    @Override
    public RequirementType getRequirementType() {
        return RequirementType.Functional;
    }

    @Override
    public FunctionBean toBean(RelationContext context) {
        return new FunctionBean(uuid, item.getUuid(), trace, name);
    }

    @Override
    public String getDisplayName() {
        return name;
    }
    private static final ReferenceFinder<Function> finder
            = new ReferenceFinder<>(Function.class);

    @Override
    public Collection<Reference<?, ?>> getReferences() {
        return finder.getReferences(this);
    }

    @CheckReturnValue
    public Function setName(String value) {
        return new Function(uuid, item.getUuid(), trace, value);
    }

    @CheckReturnValue
    public Function setTrace(UUID value) {
        return new Function(uuid, item.getUuid(), value, name);
    }
}
