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
package au.id.soundadvice.systemdesign.relation;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.CheckReturnValue;

/**
 * An immutable store of relations, suitable for use within an undo buffer.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class RelationStore implements RelationContext {

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + Objects.hashCode(this.relations);
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
        final RelationStore other = (RelationStore) obj;
        if (!Objects.equals(this.relations, other.relations)) {
            return false;
        }
        return true;
    }

    public static RelationStore valueOf(Collection<? extends Relation> input) {
        ByUUID<Relation> byUUID = ByUUID.valueOf(input);
        ByClass<Relation> byClass = ByClass.valueOf(input);
        ByReverse.Loader<Relation> reverseLoader = new ByReverse.Loader<>(byUUID);
        ByReverse<Relation> byReverse = reverseLoader.build();

        // Delete any items needed to establish referential integrity
        Collection<Relation> toDelete = reverseLoader.getToDelete();
        if (!toDelete.isEmpty()) {
            List<UUID> toDeleteAsUUIDs = toDelete.parallelStream()
                    .map(Relation::getUuid)
                    .collect(Collectors.toList());
            byUUID = byUUID.removeAll(toDeleteAsUUIDs);
            byClass = byClass.removeAll(toDeleteAsUUIDs);
            byReverse = byReverse.removeAll(toDelete);
        }

        return new RelationStore(
                byUUID,
                byClass,
                byReverse);
    }

    private static final RelationStore empty = new RelationStore(
            ByUUID.empty(),
            ByClass.empty(),
            ByReverse.empty());

    public static RelationStore empty() {
        return empty;
    }

    private final ByUUID<Relation> relations;
    private final ByClass<Relation> byClass;
    private final ByReverse<Relation> reverseReferences;

    private RelationStore(
            ByUUID<Relation> relations,
            ByClass<Relation> byClass,
            ByReverse<Relation> reverseRelations) {
        this.relations = relations;
        this.byClass = byClass;
        this.reverseReferences = reverseRelations;
    }

    @Override
    public <T extends Relation> T get(UUID key, Class<T> type) {
        Relation result = key == null ? null : relations.get(key);
        return type.cast(result);
    }

    public <T extends Relation> Collection<T> getByClass(Class<T> type) {
        return byClass.get(type);
    }

    @Override
    public <F extends Relation> Collection<? extends F> getReverse(UUID key, Class<F> fromType) {
        return reverseReferences.get(key, fromType);
    }

    @CheckReturnValue
    public RelationStore put(Relation value) {
        // Check referential integrity
        boolean referencesOK = value.getReferences().parallelStream()
                .map((reference) -> reference.getTo())
                .allMatch((to) -> {
                    Object target = relations.get(to.getUuid());
                    return to.getType().isInstance(target);
                });
        if (!referencesOK) {
            // Leave store as it was - don't add the new relation
            return this;
        }

        UUID key = value.getUuid();
        Relation oldValue = relations.get(key);
        if (value.equals(oldValue)) {
            return this;
        } else {
            RelationStore tmp;
            if (oldValue != null && !value.getClass().equals(oldValue.getClass())) {
                // Remove first
                tmp = this.remove(oldValue.getUuid());
            } else {
                tmp = this;
            }
            ByUUID<Relation> tmpRelations = tmp.relations.put(value);
            ByClass<Relation> tmpByClass = tmp.byClass.put(value);
            ByReverse<Relation> tmpReverseRelations
                    = tmp.reverseReferences.replace(oldValue, value);
            return new RelationStore(
                    tmpRelations, tmpByClass, tmpReverseRelations);
        }
    }

    @CheckReturnValue
    public RelationStore remove(UUID key) {
        return removeAll(Collections.singleton(key));
    }

    @CheckReturnValue
    public RelationStore removeAll(Collection<UUID> seed) {
        Set<Relation> toDelete = seed.parallelStream()
                .map(uuid -> relations.get(uuid))
                .filter((relation) -> relation != null)
                .collect(Collectors.toCollection(HashSet::new));
        if (seed.isEmpty()) {
            return this;
        } else {
            reverseReferences.cascade(toDelete);
            List<UUID> toDeleteAsUUIDs = toDelete.parallelStream()
                    .map(Relation::getUuid)
                    .collect(Collectors.toList());

            ByUUID<Relation> tmpRelations = relations.removeAll(toDeleteAsUUIDs);
            ByClass<Relation> tmpByClass = byClass.removeAll(toDeleteAsUUIDs);
            ByReverse<Relation> tmpReverseRelations
                    = reverseReferences.removeAll(toDelete);
            return new RelationStore(
                    tmpRelations, tmpByClass, tmpReverseRelations);
        }
    }

    public int size() {
        return relations.size();
    }

    public boolean isEmpty() {
        return relations.isEmpty();
    }
}
