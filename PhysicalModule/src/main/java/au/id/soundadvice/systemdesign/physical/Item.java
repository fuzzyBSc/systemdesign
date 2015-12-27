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

import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.physical.beans.ItemBean;
import au.id.soundadvice.systemdesign.moduleapi.relation.Reference;
import au.id.soundadvice.systemdesign.moduleapi.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javax.annotation.CheckReturnValue;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import javafx.util.Pair;

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
public class Item implements Relation {

    /**
     * Return all items for the baseline.
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<Item> find(Relations baseline) {
        return baseline.findByClass(Item.class);
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

    public static IDPath getNextItemId(Relations baseline) {
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
    public static Pair<Relations, Item> create(
            Relations baseline, String name, Point2D origin, Color color) {
        Item item = new Item(
                UUID.randomUUID(),
                getNextItemId(baseline), name, false);
        baseline = baseline.add(item);
        // Also add the coresponding view
        baseline = ItemView.create(baseline, item, origin, color).getKey();
        return new Pair<>(baseline, item);
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
    public static Pair<UndoState, Item> flowDownExternal(UndoState state, Item template) {
        Relations functional = state.getFunctional();
        Relations allocated = state.getAllocated();
        Item item = new Item(
                template.uuid,
                template.getIdPath(functional), template.name, true);
        allocated = allocated.add(item);
        state = state.setAllocated(allocated);
        // Also add the coresponding view
        Optional<ItemView> viewTemplate = template.findViews(functional).findAny();
        allocated = ItemView.create(
                allocated, item,
                viewTemplate.map(ItemView::getOrigin).orElse(ItemView.DEFAULT_ORIGIN),
                viewTemplate.map(ItemView::getColor).orElse(ItemView.DEFAULT_COLOR))
                .getKey();

        for (BiFunction<UndoState, Item, UndoState> action : FLOW_DOWN_ACTIONS) {
            state = action.apply(state, item);
        }
        return state.setAllocated(allocated).and(item);
    }

    private static final List<BiFunction<UndoState, Item, UndoState>> FLOW_DOWN_ACTIONS
            = new CopyOnWriteArrayList<>();

    public static void addFlowDownAction(BiFunction<UndoState, Item, UndoState> action) {
        FLOW_DOWN_ACTIONS.add(action);
    }

    /**
     * Remove an item from a baseline.
     *
     * @param baseline The baseline to update
     * @return The updated baseline
     */
    @CheckReturnValue
    public Relations removeFrom(Relations baseline) {
        return baseline.remove(uuid);
    }

    @Override
    public String toString() {
        return id + " " + name;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public IDPath getIdPath(Relations baseline) {
        if (external) {
            return id;
        } else {
            IDPath baselineIdPath = baseline.findByClass(Identity.class)
                    .findAny()
                    .map(Identity::getIdPath)
                    .orElse(IDPath.empty());

            return baselineIdPath.resolve(id);
        }
    }

    public String getName() {
        return name;
    }

    public boolean isExternal() {
        return external;
    }

    private final UUID uuid;
    /**
     * Path identifier. If external is true this is a full path. Otherwise it is
     * a single path segment that is relative to the current allocated baseline.
     */
    private final IDPath id;
    private final String name;
    private final boolean external;

    public Item(ItemBean bean) {
        this.uuid = bean.getUuid();
        this.id = IDPath.valueOfDotted(bean.getId());
        this.name = bean.getName();
        this.external = bean.isExternal();
    }

    public ItemBean toBean() {
        return new ItemBean(
                uuid, id.toString(),
                name,
                external);
    }

    public String getDisplayName() {
        return this.toString();
    }

    private static final ReferenceFinder<Item> FINDER
            = new ReferenceFinder<>(Item.class);

    @Override
    public Stream<Reference> getReferences() {
        return FINDER.getReferences(this);
    }

    private Item(
            UUID uuid, IDPath id, String name, boolean external) {
        this.uuid = uuid;
        this.id = id;
        this.name = name;
        this.external = external;
    }

    public Identity asIdentity(Relations baseline) {
        return new Identity(uuid, getIdPath(baseline), name);
    }

    public Item asExternal(Relations baseline) {
        if (external) {
            return this;
        } else {
            return new Item(uuid, getIdPath(baseline), name, true);
        }
    }

    public IDPath getShortId() {
        return id;
    }

    @CheckReturnValue
    public Pair<Relations, Item> setName(Relations baseline, String name) {
        if (this.name.equals(name)) {
            return new Pair<>(baseline, this);
        } else {
            Item result = new Item(
                    uuid, id, name, external);
            return new Pair<>(baseline.add(result), result);
        }
    }

    @CheckReturnValue
    public Pair<Relations, Item> setShortId(Relations baseline, IDPath id) {
        if (this.id.equals(id)) {
            return new Pair<>(baseline, this);
        } else {
            Item result = new Item(
                    uuid, id, name, external);
            return new Pair<>(baseline.add(result), result);
        }
    }

    @CheckReturnValue
    public Pair<Relations, Item> makeConsistent(Relations baseline, Item other) {
        Item result = new Item(
                uuid, other.getShortId(), other.getName(), external);
        return new Pair<>(baseline.add(result), result);
    }

    public ItemView getView(Relations baseline) {
        return findViews(baseline).findAny().get();
    }

    public Stream<ItemView> findViews(Relations baseline) {
        return baseline.findReverse(uuid, ItemView.class);
    }

    public Optional<Item> getTrace(UndoState state) {
        if (external) {
            return state.getFunctional().get(uuid, Item.class);
        } else {
            return Identity.getSystemOfInterest(state);
        }
    }
}
