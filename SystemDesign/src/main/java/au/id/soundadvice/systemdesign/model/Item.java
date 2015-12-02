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

import au.id.soundadvice.systemdesign.model.UndoState.StateAnd;
import au.id.soundadvice.systemdesign.beans.BeanFactory;
import au.id.soundadvice.systemdesign.beans.ItemBean;
import au.id.soundadvice.systemdesign.fxml.UniqueName;
import au.id.soundadvice.systemdesign.model.Baseline.BaselineAnd;
import au.id.soundadvice.systemdesign.relation.Reference;
import au.id.soundadvice.systemdesign.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.relation.Relation;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
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
public class Item implements BeanFactory<Baseline, ItemBean>, Relation {

    /**
     * Return all items for the baseline.
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<Item> find(Baseline baseline) {
        return baseline.getStore().getByClass(Item.class);
    }

    public Stream<BudgetAllocation> findBudgetAllocations(Baseline baseline) {
        return baseline.getStore().getReverse(uuid, BudgetAllocation.class);
    }

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
        final Item other = (Item) obj;
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.parent, other.parent)) {
            return false;
        }
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (this.external != other.external) {
            return false;
        }
        return true;
    }

    public boolean isConsistent(Item other) {
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return true;
    }

    public static IDPath getNextItemId(Baseline baseline) {
        Optional<Integer> currentMax = find(baseline).parallel()
                .filter(item -> !item.isExternal())
                .map(item -> {
                    try {
                        return Integer.parseInt(item.getShortId().toString());
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                })
                .collect(Collectors.maxBy(Integer::compareTo));
        int nextId;
        if (currentMax.isPresent()) {
            nextId = currentMax.get() + 1;
        } else {
            nextId = 1;
        }
        return IDPath.valueOfSegment(Integer.toString(nextId));
    }

    /**
     * Create a new item.
     *
     * @param baseline The baseline to update
     * @param name The name of the item
     * @param origin The location for the item on the screen
     * @param color The item's color
     * @return The updated baseline
     */
    @CheckReturnValue
    public static BaselineAnd<Item> create(
            Baseline baseline, String name, Point2D origin, Color color) {
        Item item = new Item(
                UUID.randomUUID(),
                Identity.find(baseline).getUuid(),
                getNextItemId(baseline), name, false);
        baseline = baseline.add(item);
        // Also add the coresponding view
        baseline = ItemView.create(baseline, item, origin, color).getBaseline();
        return baseline.and(item);
    }

    /**
     * Create a new item with a default name.
     *
     * @param baseline The baseline to update
     * @param origin The location for the item on the screen
     * @return The updated baseline
     */
    @CheckReturnValue
    public static BaselineAnd<Item> create(Baseline baseline, Point2D origin, Color color) {
        String name = find(baseline).parallel()
                .filter(item -> !item.isExternal())
                .map(Item::getName)
                .collect(new UniqueName("New Item"));
        return create(baseline, name, origin, color);
    }

    /**
     * Flow an external item down from the functional baseline to the allocated
     * baseline.
     *
     * @param state The state to update
     * @param template The item to flow down from the state's functional
     * baseline
     * @return The updated baseline
     */
    @CheckReturnValue
    public static StateAnd<Item> flowDownExternal(UndoState state, Item template) {
        Baseline functional = state.getFunctional();
        Baseline allocated = state.getAllocated();
        Item item = new Item(
                template.uuid,
                Identity.find(allocated).getUuid(),
                template.getIdPath(functional), template.name, true);
        allocated = allocated.add(item);
        // Also add the coresponding view
        ItemView viewTemplate = template.getView(functional);
        allocated = ItemView.create(
                allocated, item, viewTemplate.getOrigin(), viewTemplate.getColor())
                .getBaseline();
        return state.setAllocated(allocated).and(item);
    }

    /**
     * Restore a deleted Item.
     *
     * @param was The baseline to restore from
     * @param allocated The baseline to restore into
     * @param item The item to restore
     * @return The updated baseline
     */
    @CheckReturnValue
    public static BaselineAnd<Item> restore(Baseline was, Baseline allocated, Item item) {
        // Item and view
        ItemView view = item.getView(was);
        allocated = allocated.add(item);
        allocated = allocated.add(view);

        {
            Iterator<Interface> it = item.findInterfaces(was).iterator();
            while (it.hasNext()) {
                Interface iface = it.next();
                Optional<Item> otherItem = allocated.get(iface.otherEnd(was, item));
                if (otherItem.isPresent()) {
                    allocated = Interface.restore(was, allocated, iface).getBaseline();
                }
            }
        }
        {
            Iterator<BudgetAllocation> it = item.findBudgetAllocations(was).iterator();
            while (it.hasNext()) {
                allocated = allocated.add(it.next());
            }
        }
        {
            Iterator<Function> it = item.findOwnedFunctions(was).iterator();
            while (it.hasNext()) {
                allocated = Function.restore(was, allocated, it.next()).getBaseline();
            }
        }

        return allocated.and(item);
    }

    /**
     * Remove an item from a baseline.
     *
     * @param baseline The baseline to update
     * @return The updated baseline
     */
    @CheckReturnValue
    public Baseline removeFrom(Baseline baseline) {
        return baseline.remove(uuid);
    }

    /**
     * Return the list of functions that this item implements directly.
     *
     * @param baseline This item's baseline
     * @return
     */
    public Stream<Function> findOwnedFunctions(Baseline baseline) {
        return baseline.getReverse(uuid, Function.class);
    }

    /**
     * Return the interfaces related to this time within the specified baseline.
     *
     * @param baseline The level of the system to search within
     * @return
     */
    public Stream<Interface> findInterfaces(Baseline baseline) {
        return baseline.getReverse(uuid, Interface.class);
    }

    @Override
    public String toString() {
        return id + " " + name;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public IDPath getIdPath(Baseline baseline) {
        if (external) {
            return id;
        } else {
            return parent.getTarget(baseline.getStore()).getIdPath().resolve(id);
        }
    }

    public String getName() {
        return name;
    }

    public boolean isExternal() {
        return external;
    }

    private final UUID uuid;
    private final Reference<Item, Identity> parent;
    /**
     * Path identifier. If external is true this is a full path. Otherwise it is
     * a single path segment that is relative to the current allocated baseline.
     */
    private final IDPath id;
    private final String name;
    private final boolean external;

    public Item(UUID parent, ItemBean bean) {
        this.uuid = bean.getUuid();
        this.parent = new Reference(this, parent, Identity.class);
        this.id = IDPath.valueOfDotted(bean.getId());
        this.name = bean.getName();
        this.external = bean.isExternal();
    }

    @Override
    public ItemBean toBean(Baseline context) {
        return new ItemBean(
                uuid, id.toString(),
                name,
                external);
    }

    public String getDisplayName() {
        return this.toString();
    }

    private static final ReferenceFinder<Item> finder
            = new ReferenceFinder<>(Item.class);

    @Override
    public Stream<Reference> getReferences() {
        return finder.getReferences(this);
    }

    private Item(
            UUID uuid, UUID parent, IDPath id, String name, boolean external) {
        this.uuid = uuid;
        this.parent = new Reference(this, parent, Identity.class);
        this.id = id;
        this.name = name;
        this.external = external;
    }

    public Identity asIdentity(Baseline baseline) {
        return new Identity(uuid, getIdPath(baseline), name);
    }

    public Item asExternal(Baseline baseline) {
        if (external) {
            return this;
        } else {
            return new Item(uuid, parent.getUuid(), getIdPath(baseline), name, true);
        }
    }

    public IDPath getShortId() {
        return id;
    }

    @CheckReturnValue
    public BaselineAnd<Item> setName(Baseline baseline, String name) {
        if (this.name.equals(name)) {
            return baseline.and(this);
        } else {
            Item result = new Item(
                    uuid, parent.getUuid(), id, name, external);
            return baseline.add(result).and(result);
        }
    }

    @CheckReturnValue
    public BaselineAnd<Item> setShortId(Baseline baseline, IDPath id) {
        if (this.id.equals(id)) {
            return baseline.and(this);
        } else {
            Item result = new Item(
                    uuid, parent.getUuid(), id, name, external);
            return baseline.add(result).and(result);
        }
    }

    @CheckReturnValue
    public BaselineAnd<Item> makeConsistent(Baseline baseline, Item other) {
        Item result = new Item(
                uuid, parent.getUuid(), other.getShortId(), other.getName(), external);
        return baseline.add(result).and(result);
    }

    public ItemView getView(Baseline baseline) {
        return findViews(baseline).findAny().get();
    }

    public Stream<ItemView> findViews(Baseline baseline) {
        return baseline.getReverse(uuid, ItemView.class);
    }
}
