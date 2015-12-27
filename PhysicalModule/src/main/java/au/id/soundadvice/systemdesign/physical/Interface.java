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

import au.id.soundadvice.systemdesign.moduleapi.RelationPair;
import au.id.soundadvice.systemdesign.moduleapi.UUIDPair;
import au.id.soundadvice.systemdesign.physical.beans.InterfaceBean;
import au.id.soundadvice.systemdesign.moduleapi.relation.Reference;
import au.id.soundadvice.systemdesign.moduleapi.relation.ReferenceFinder;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

/**
 * An association between two items that implies Flows may exist between them.
 * Each pair of Items in an allocated baseline may either have a corresponding
 * interface or have no corresponding interface. The description of the
 * interface is compose primarily of the flows across it, the nature of the two
 * items, and any associated interface requirements.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Interface implements Relation {

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
        if (!Objects.equals(this.scope, other.scope)) {
            return false;
        }
        return true;
    }

    /**
     * Return all interfaces between subsystems of the allocated baseline, as
     * well as all interfaces between subsystems and external systems.
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<Interface> find(Relations baseline) {
        return baseline.findByClass(Interface.class);
    }

    /**
     * Return the interfaces related to this time within the specified baseline.
     *
     * @param baseline The level of the system to search within
     * @param item The item to search
     * @return
     */
    public static Stream<Interface> find(Relations baseline, Item item) {
        return baseline.findReverse(item.getUuid(), Interface.class);
    }

    /**
     * Create a new Interface.
     *
     * @param baseline The baseline to update
     * @param left An item for this interface to connect to
     * @param right An item for this interface to connect to
     * @return The updated baseline
     */
    @CheckReturnValue
    public static Pair<Relations, Interface> create(
            Relations baseline, Item left, Item right) {
        Optional<Interface> existing = get(baseline, left, right);
        if (existing.isPresent()) {
            return new Pair<>(baseline, existing.get());
        } else {
            Interface newInterface = new Interface(
                    UUID.randomUUID(), new UUIDPair(left.getUuid(), right.getUuid()));
            return new Pair<>(baseline.add(newInterface), newInterface);
        }
    }

    /**
     * Remove an interface.
     *
     * @param baseline The baseline to update
     * @param left An item the interface is connected to
     * @param right An item the interface is connected to
     * @return The updated baseline
     */
    @CheckReturnValue
    public static Relations remove(
            Relations baseline, Item left, Item right) {
        Optional<Interface> existing = get(baseline, left, right);
        if (existing.isPresent()) {
            return baseline.remove(existing.get().getUuid());
        } else {
            return baseline;
        }
    }

    public Relations removeFrom(Relations baseline) {
        return baseline.remove(uuid);
    }

    /**
     * Find the interface (if any) between the nominated items.
     *
     * @param baseline The baseline to query
     * @param left One of the items for the interface
     * @param right One of the items for the interface
     * @return The interface, or Optional.empty() if no such interface exists.
     */
    public static Optional<Interface> get(
            Relations baseline, Item left, Item right) {
        return get(baseline, left.getUuid(), right.getUuid());
    }

    private static Optional<Interface> get(
            Relations baseline, UUID leftItem, UUID rightItem) {
        return baseline.findReverse(leftItem, Interface.class)
                .filter(iface -> iface.scope.otherEnd(leftItem).equals(rightItem))
                .findAny();
    }

    public RelationPair<Item> getEndpoints(Relations baseline) {
        return RelationPair.resolve(baseline, scope, Item.class).get();
    }

    public Interface(InterfaceBean bean) {
        this(bean.getUuid(), new UUIDPair(
                bean.getLeftItem(),
                bean.getRightItem()));
    }

    private Interface(UUID uuid, UUIDPair scope) {
        this.uuid = uuid;
        this.scope = scope;
        this.left = new Reference<>(this, scope.getLeft(), Item.class);
        this.right = new Reference<>(this, scope.getRight(), Item.class);
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public Reference<Interface, Item> getLeft() {
        return left;
    }

    public Item getLeft(Relations baseline) {
        return left.getTarget(baseline);
    }

    public Reference<Interface, Item> getRight() {
        return right;
    }

    public Item getRight(Relations baseline) {
        return right.getTarget(baseline);
    }

    private final UUID uuid;
    private final Reference<Interface, Item> left;
    private final Reference<Interface, Item> right;
    private final UUIDPair scope;

    public String getLongID(Relations baseline) {
        Item leftItem = this.left.getTarget(baseline);
        Item rightItem = this.right.getTarget(baseline);
        IDPath leftPath = leftItem.getIdPath(baseline);
        IDPath rightPath = rightItem.getIdPath(baseline);
        if (leftPath.compareTo(rightPath) > 0) {
            // Invert
            {
                IDPath tmp = leftPath;
                leftPath = rightPath;
                rightPath = tmp;
            }
        }

        return leftPath + ":" + rightPath;
    }

    public String getLongDescription(Relations baseline) {
        Item leftItem = this.left.getTarget(baseline);
        Item rightItem = this.right.getTarget(baseline);
        IDPath leftPath = leftItem.getIdPath(baseline);
        IDPath rightPath = rightItem.getIdPath(baseline);
        if (leftPath.compareTo(rightPath) > 0) {
            // Invert
            {
                Item tmp = leftItem;
                leftItem = rightItem;
                rightItem = tmp;
            }
            {
                IDPath tmp = leftPath;
                leftPath = rightPath;
                rightPath = tmp;
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append(leftPath);
        builder.append(':');
        builder.append(rightPath);
        builder.append(' ');
        builder.append(leftItem.getName());
        builder.append(':');
        builder.append(rightItem.getName());
        return builder.toString();
    }

    public InterfaceBean toBean(Relations baseline) {
        return new InterfaceBean(
                uuid, left.getUuid(), right.getUuid(),
                getLongDescription(baseline));
    }

    private static final ReferenceFinder<Interface> finder
            = new ReferenceFinder<>(Interface.class);

    @Override
    public Stream<Reference> getReferences() {
        return finder.getReferences(this);
    }

    public Item otherEnd(Relations baseline, Item item) {
        UUID otherEndUUID = scope.otherEnd(item.getUuid());
        // Referential integrity should be guaranteed by the store
        return baseline.get(otherEndUUID, Item.class).get();
    }
}
