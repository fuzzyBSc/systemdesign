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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

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

    public static RelationStore valueOf(Stream<Relation> input) {
        // Collect the stream in order to duplicate it
        List<Relation> collectedInput = input.collect(Collectors.toList());
        ByUUID<Relation> byUUID = ByUUID.valueOf(collectedInput.stream());
        ByClass<Relation> byClass = ByClass.valueOf(collectedInput.stream());
        ByReverse.Loader<Relation> reverseLoader = new ByReverse.Loader<>(byUUID);
        ByReverse<Relation> byReverse = reverseLoader.build();

        // Delete any items needed to establish referential integrity
        List<UUID> toDeleteAsUUIDs = reverseLoader.getDeletedRelations().parallel()
                .map(Relation::getUuid)
                .collect(Collectors.toList());
        if (!toDeleteAsUUIDs.isEmpty()) {
            byUUID = byUUID.removeAll(toDeleteAsUUIDs);
            byClass = byClass.removeAll(toDeleteAsUUIDs);
            // byReverse entries have already been removed
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
    @Nullable
    public <T extends Relation> T get(UUID key, Class<T> type) {
        Relation result = key == null ? null : relations.get(key);
        if (type.isInstance(result)) {
            return (T) result;
        } else {
            return null;
        }
    }

    public <T extends Relation> Stream<T> getByClass(Class<T> type) {
        return byClass.get(type);
    }

    @Override
    public <F extends Relation> Stream<F> getReverse(UUID key, Class<F> fromType) {
        return reverseReferences.get(key, fromType);
    }

    @CheckReturnValue
    public RelationStore put(Relation value) {
        // Check referential integrity
        boolean referencesOK = value.getReferences().parallel()
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
        return removeAll(Stream.of(key));
    }

    @CheckReturnValue
    public RelationStore removeAll(Stream<UUID> seed) {
        Set<Relation> toDelete = seed.parallel()
                .map(uuid -> relations.get(uuid))
                .filter((relation) -> relation != null)
                .collect(Collectors.toCollection(HashSet::new));
        if (toDelete.isEmpty()) {
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
