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
import au.id.soundadvice.systemdesign.beans.ItemBean;
import au.id.soundadvice.systemdesign.relation.Reference;
import au.id.soundadvice.systemdesign.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.relation.Relation;
import au.id.soundadvice.systemdesign.relation.RelationContext;
import javafx.geometry.Point2D;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Item implements RequirementContext, BeanFactory<RelationContext, ItemBean>, FlowEnd, Relation {

    public IDSegment getShortId() {
        return shortId;
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
        final Item other = (Item) obj;
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.parent, other.parent)) {
            return false;
        }
        if (!Objects.equals(this.shortId, other.shortId)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.description, other.description)) {
            return false;
        }
        if (this.external != other.external) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return shortId + " " + name;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public IDPath getIdPath(RelationContext context) {
        return parent.getTarget(context).getIdPath().getChild(shortId);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isExternal() {
        return external;
    }

    private final UUID uuid;
    private final Reference<Item, Identity> parent;
    private final IDSegment shortId;
    private final String name;
    private final String description;
    private final boolean external;
    private final Point2D origin;

    public Item(UUID parent, ItemBean bean) {
        this.uuid = bean.getUuid();
        this.parent = new Reference(this, parent, Identity.class);
        this.shortId = new IDSegment(bean.getId());
        this.name = bean.getName();
        this.description = bean.getDescription();
        this.external = bean.isExternal();
        this.origin = new Point2D(bean.getOriginX(), bean.getOriginY());
    }

    @Override
    public ItemBean toBean(RelationContext context) {
        return new ItemBean(
                uuid, shortId.toString(),
                name, description,
                origin.getX(), origin.getY(),
                external);
    }

    @Override
    public RequirementType getRequirementType() {
        return RequirementType.NonFunctional;
    }

    @Override
    public String getFlowEndName() {
        return name;
    }
    private static final ReferenceFinder<Item> finder
            = new ReferenceFinder<>(Item.class);

    @Override
    public Collection<Reference<?, ?>> getReferences() {
        return finder.getReferences(this);
    }

    public static Item newItem(UUID parent, IDSegment shortId, String name, String description) {
        return new Item(
                UUID.randomUUID(), parent, shortId, name, description, false, Point2D.ZERO);
    }

    private Item(
            UUID uuid, UUID parent, IDSegment shortId, String name, String description, boolean external,
            Point2D origin) {
        this.uuid = uuid;
        this.parent = new Reference(this, parent, Identity.class);
        this.shortId = shortId;
        this.name = name;
        this.description = description;
        this.external = external;
        this.origin = origin;
    }

    @CheckReturnValue
    public Item setName(String value) {
        return new Item(uuid, parent.getUuid(), shortId, value, description, external, origin);
    }

    @CheckReturnValue
    public Item setOrigin(Point2D value) {
        return new Item(uuid, parent.getUuid(), shortId, name, description, external, value);
    }

    public Point2D getOrigin() {
        return origin;
    }

}
