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
import au.id.soundadvice.systemdesign.beans.InterfaceBean;
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
public class Interface implements RequirementContext, BeanFactory<RelationContext, InterfaceBean>, Relation {

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.uuid);
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
        final Interface other = (Interface) obj;
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.left, other.left)) {
            return false;
        }
        if (!Objects.equals(this.right, other.right)) {
            return false;
        }
        return true;
    }

    public boolean isRedundantTo(Interface other) {
        if (!Objects.equals(this.left.getTo(), other.left.getTo())) {
            return false;
        }
        if (!Objects.equals(this.right.getTo(), other.right.getTo())) {
            return false;
        }
        return true;
    }

    public Interface(UUID left, UUID right) {
        this.uuid = UUID.randomUUID();
        // Normalise left/right by UUID
        if (left.compareTo(right) < 0) {
            this.left = new Reference<>(this, left, Item.class);
            this.right = new Reference<>(this, right, Item.class);
        } else {
            this.left = new Reference<>(this, right, Item.class);
            this.right = new Reference<>(this, left, Item.class);
        }
    }

    public Interface(InterfaceBean bean) {
        this.uuid = bean.getUuid();
        UUID leftItemUuid = bean.getLeftItem();
        UUID rightItemUuid = bean.getRightItem();
        // Normalise left/right by UUID
        if (leftItemUuid.compareTo(rightItemUuid) < 0) {
            this.left = new Reference<>(this, leftItemUuid, Item.class);
            this.right = new Reference<>(this, rightItemUuid, Item.class);
        } else {
            this.left = new Reference<>(this, rightItemUuid, Item.class);
            this.right = new Reference<>(this, leftItemUuid, Item.class);
        }
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public Reference<Interface, Item> getLeft() {
        return left;
    }

    public Reference<Interface, Item> getRight() {
        return right;
    }

    public String getDescription(RelationContext context) {
        Item leftItem = this.left.getTarget(context);
        Item rightItem = this.right.getTarget(context);
        IDPath leftPath = leftItem.getIdPath(context);
        IDPath rightPath = rightItem.getIdPath(context);
        if (leftPath.compareTo(rightPath) < 0) {
            return leftPath + ":" + rightPath;
        } else {
            return rightPath + ":" + leftPath;
        }
    }

    private final UUID uuid;
    private final Reference<Interface, Item> left;
    private final Reference<Interface, Item> right;

    @Override
    public RequirementType getRequirementType() {
        return RequirementType.Interface;
    }

    @Override
    public InterfaceBean toBean(RelationContext context) {
        return new InterfaceBean(
                uuid, null, left.getUuid(), right.getUuid(), getDescription(context));
    }

    private static final ReferenceFinder<Interface> finder
            = new ReferenceFinder<>(Interface.class);

    @Override
    public Collection<Reference<?, ?>> getReferences() {
        return finder.getReferences(this);
    }
}
