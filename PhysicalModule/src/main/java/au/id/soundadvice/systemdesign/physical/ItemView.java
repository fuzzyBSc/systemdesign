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
package au.id.soundadvice.systemdesign.physical;

import au.id.soundadvice.systemdesign.physical.beans.ItemViewBean;
import au.id.soundadvice.systemdesign.moduleapi.relation.Reference;
import au.id.soundadvice.systemdesign.moduleapi.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import javafx.geometry.Point2D;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import javafx.scene.paint.Color;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

/**
 * A physical Item. Item is used as a fairly loose term in the model and could
 * mean system, subsystem, configuration item, or correspond to a number of
 * standards-based concepts. As far as the model goes it identifies something
 * that exists rather than dealing with what a thing does. Items are at the root
 * of how the model is put together. Each item either is or has the potential to
 * be a whole director unto itself containing other conceptual elements.
 *
 * An item is typically an entire system, an assembly of parts, or a hardware or
 * software configuration item. The kind of existence required of it can be
 * abstract. For software it could end up a unit of software installed under a
 * single software package, a name space or class.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ItemView implements Relation {

    public Color getColor() {
        return color;
    }

    /**
     * Return all views of items within the baseline.
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<ItemView> find(Relations baseline) {
        return baseline.findByClass(ItemView.class);
    }

    public static final Point2D DEFAULT_ORIGIN = new Point2D(200, 200);
    public static final Color DEFAULT_COLOR = Color.LIGHTYELLOW;

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.identifier);
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
        final ItemView other = (ItemView) obj;
        if (!Objects.equals(this.identifier, other.identifier)) {
            return false;
        }
        if (!Objects.equals(this.item, other.item)) {
            return false;
        }
        if (!Objects.equals(this.origin, other.origin)) {
            return false;
        }
        if (!Objects.equals(this.color, other.color)) {
            return false;
        }
        return true;
    }

    /**
     * Create a new item view.
     *
     * @param baseline The baseline to update
     * @param item The item this view refers to
     * @param origin The location for the item on the screen
     * @param color The color for this item
     * @return The updated baseline
     */
    @CheckReturnValue
    public static Pair<Relations, ItemView> create(
            Relations baseline,
            Item item,
            Point2D origin,
            Color color) {
        ItemView view = new ItemView(UUID.randomUUID().toString(), item.getIdentifier(), origin, color);
        return new Pair<>(baseline.add(view), view);
    }

    @CheckReturnValue
    public Relations removeFrom(Relations baseline) {
        return baseline.remove(identifier);
    }

    @Override
    public String toString() {
        return origin.toString();
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    public Reference<ItemView, Item> getItem() {
        return item;
    }

    private final String identifier;
    private final Reference<ItemView, Item> item;
    private final Point2D origin;
    private final Color color;

    public ItemView(ItemViewBean bean) {
        this.identifier = bean.getIdentifier();
        this.item = new Reference(this, bean.getItem(), Item.class);
        this.origin = new Point2D(bean.getOriginX(), bean.getOriginY());
        this.color = bean.getColor();
    }

    public ItemViewBean toBean(Relations baseline) {
        return new ItemViewBean(
                identifier,
                item.getKey(), item.getTarget(baseline).getDisplayName(),
                origin.getX(), origin.getY(),
                color);
    }

    public String getDisplayName() {
        return this.toString();
    }

    private static final ReferenceFinder<ItemView> FINDER
            = new ReferenceFinder<>(ItemView.class);

    @Override
    public Stream<Reference> getReferences() {
        return FINDER.getReferences(this);
    }

    private ItemView(
            String identifier,
            String item,
            Point2D origin,
            Color color) {
        this.identifier = identifier;
        this.item = new Reference(this, item, Item.class);
        this.origin = origin;
        this.color = color;
    }

    @CheckReturnValue
    public Pair<Relations, ItemView> setOrigin(Relations baseline, Point2D origin) {
        if (this.origin.equals(origin)) {
            return new Pair<>(baseline, this);
        } else {
            ItemView result = new ItemView(identifier, item.getKey(), origin, color);
            return new Pair<>(baseline.add(result), result);
        }
    }

    @CheckReturnValue
    public Pair<Relations, ItemView> setColor(Relations baseline, Color color) {
        if (this.color.equals(color)) {
            return new Pair<>(baseline, this);
        } else {
            ItemView result = new ItemView(identifier, item.getKey(), origin, color);
            return new Pair<>(baseline.add(result), result);
        }
    }

    public Point2D getOrigin() {
        return origin;
    }
}
