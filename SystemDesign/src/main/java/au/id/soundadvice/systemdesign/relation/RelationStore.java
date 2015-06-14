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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
        Map<UUID, Relation> tmpRelations = new HashMap<>();
        Map<ReferenceTarget<?>, Map<Class<?>, List<Relation>>> unfixedReverse = new HashMap<>();
        for (Relation relation : input) {
            tmpRelations.put(relation.getUuid(), relation);
            relation.getReferences().stream().map((reference) -> reference.getTo()).map((lookup) -> {
                Map<Class<?>, List<Relation>> map = unfixedReverse.get(lookup);
                if (map == null) {
                    map = new HashMap<>();
                    unfixedReverse.put(lookup, map);
                }
                return map;
            }).map((map) -> {
                List<Relation> list = map.get(relation.getClass());
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(relation.getClass(), list);
                }
                return list;
            }).forEach((list) -> {
                list.add(relation);
            });
        }

        Map<UUID, Map<Class<?>, List<Relation>>> fixedReverse = new HashMap<>();
        unfixedReverse.entrySet().stream().forEach((entry) -> {
            UUID key = entry.getKey().getKey();
            if (entry.getKey().getType().isInstance(tmpRelations.get(key))) {
                fixedReverse.put(key, entry.getValue());
            }
        });

        Map<Class<?>, List<Relation>> tmpByClass = collateClasses(tmpRelations);

        Deque<UUID> toDelete = getDanglingRelations(tmpRelations, unfixedReverse);
        cascadingDelete(toDelete, tmpRelations, tmpByClass, fixedReverse);

        return new RelationStore(
                Collections.unmodifiableMap(tmpRelations),
                Collections.unmodifiableMap(tmpByClass),
                Collections.unmodifiableMap(fixedReverse));
    }

    private static Map<Class<?>, List<Relation>> collateClasses(Map<UUID, Relation> relations) {
        Map<Class<?>, List<Relation>> result = new HashMap<>();
        relations.values().stream().forEach((relation) -> {
            Class<? extends Relation> type = relation.getClass();
            List<Relation> list = result.get(type);
            if (list == null) {
                list = new ArrayList<>();
                result.put(type, list);
            }
            list.add(relation);
        });
        return result;
    }

    private static final RelationStore empty = new RelationStore(
            Collections.<UUID, Relation>emptyMap(),
            Collections.<Class<?>, List<Relation>>emptyMap(),
            Collections.<UUID, Map<Class<?>, List<Relation>>>emptyMap());

    public static RelationStore empty() {
        return empty;
    }

    private final Map<UUID, Relation> relations;
    private final Map<Class<?>, List<Relation>> byClass;
    private final Map<UUID, Map<Class<?>, List<Relation>>> reverseRelations;

    private RelationStore(
            Map<UUID, Relation> relations,
            Map<Class<?>, List<Relation>> byClass,
            Map<UUID, Map<Class<?>, List<Relation>>> reverseRelations) {
        this.relations = relations;
        this.byClass = byClass;
        this.reverseRelations = reverseRelations;
    }

    @Override
    public <T extends Relation> T get(UUID key, Class<T> type) {
        Object result = relations.get(key);
        return type.cast(result);
    }

    public <T extends Relation> List<T> getByClass(Class<T> type) {
        List<Relation> result = byClass.get(type);
        if (result == null) {
            return Collections.emptyList();
        } else {
            return (List<T>) result;
        }
    }

    @Override
    public <F extends Relation> Collection<? extends F> getReverse(UUID key, Class<F> fromType) {
        Map<Class<?>, List<Relation>> map = reverseRelations.get(key);
        if (map == null) {
            return Collections.emptyList();
        } else {
            List<? extends Relation> list = map.get(fromType);
            if (list == null) {
                return Collections.emptyList();
            } else {
                return (List<F>) list;
            }
        }
    }

    @CheckReturnValue
    public RelationStore put(Relation value) {
        UUID key = value.getUuid();
        Relation oldValue = relations.get(key);
        if (value.equals(oldValue)) {
            return this;
        } else {
            // Check referential integrity
            boolean referencesOK = value.getReferences().parallelStream()
                    .map((reference) -> reference.getTo())
                    .allMatch((to) -> {
                        Object target = relations.get(to.getKey());
                        return to.getType().isInstance(target);
                    });
            if (!referencesOK) {
                // Leave store as it was - don't add the new relation
                return this;
            }

            Map<UUID, Relation> tmpRelations = new HashMap<>(relations);
            Map<Class<?>, List<Relation>> tmpByClass = new HashMap<>(byClass);
            Map<UUID, Map<Class<?>, List<Relation>>> tmpReverse = new HashMap<>(reverseRelations);
            if (oldValue != null) {
                // Remove old presence before inserting new
                if (value.getClass().equals(oldValue.getClass())) {
                    removeByClass(oldValue, tmpByClass);
                    removeReverse(key, oldValue, tmpReverse);
                } else {
                    // This is a completely different object
                    Deque<UUID> toDelete = new ArrayDeque<>();
                    toDelete.add(key);
                    cascadingDelete(toDelete, tmpRelations, tmpByClass, tmpReverse);
                }
            }
            tmpRelations.put(key, value);
            addByClass(value, tmpByClass);
            value.getReferences().stream()
                    .map((reference) -> reference.getTo())
                    .map((lookup) -> {
                        Map<Class<?>, List<Relation>> map = tmpReverse.get(lookup.getKey());
                        if (map == null) {
                            map = new HashMap<>();
                        } else {
                            map = new HashMap<>(map);
                        }
                        tmpReverse.put(lookup.getKey(), map);
                        return map;
                    }).map((map) -> {
                        List<Relation> list = map.get(tmpReverse.getClass());
                        if (list == null) {
                            list = new ArrayList<>();
                        } else {
                            list = new ArrayList<>(list);
                        }
                        map.put(value.getClass(), list);
                        return list;
                    }).forEach((list) -> {
                        list.add(value);
                    });
            return new RelationStore(
                    Collections.unmodifiableMap(tmpRelations),
                    Collections.unmodifiableMap(tmpByClass),
                    Collections.unmodifiableMap(tmpReverse));
        }
    }

    @CheckReturnValue
    public RelationStore remove(UUID key) {
        Relation relation = relations.get(key);
        if (relation == null) {
            return this;
        } else {
            Map<UUID, Relation> tmpRelations = new HashMap<>(relations);
            Map<Class<?>, List<Relation>> tmpByClass = new HashMap<>(byClass);
            Map<UUID, Map<Class<?>, List<Relation>>> tmpReverse = new HashMap<>(reverseRelations);
            Deque<UUID> toDelete = new ArrayDeque<>();
            toDelete.add(key);
            cascadingDelete(toDelete, tmpRelations, tmpByClass, tmpReverse);
            return new RelationStore(
                    Collections.unmodifiableMap(tmpRelations),
                    Collections.unmodifiableMap(tmpByClass),
                    Collections.unmodifiableMap(tmpReverse));
        }
    }

    private static void removeReverse(
            UUID key,
            Relation relation,
            Map<UUID, Map<Class<?>, List<Relation>>> reverse) {
        Map<Class<?>, List<Relation>> map = reverse.get(key);
        if (map == null) {
            // Nothing to remove
            return;
        }
        List<Relation> list = map.get(relation.getClass());
        if (list == null) {
            // Nothing to remove
            return;
        }
        map = new HashMap<>(map);
        list = new ArrayList<>(list);
        list.remove(relation);
        if (list.isEmpty()) {
            map.remove(relation.getClass());
        } else {
            map.put(relation.getClass(), list);
        }
        if (map.isEmpty()) {
            reverse.remove(key);
        } else {
            reverse.put(key, map);
        }
    }

    private static Deque<UUID> getDanglingRelations(
            Map<UUID, Relation> relations,
            Map<ReferenceTarget<?>, Map<Class<?>, List<Relation>>> reverse) {
        Deque<UUID> toDelete = new ArrayDeque<>();
        reverse.entrySet().stream().forEach((map) -> {
            UUID key = map.getKey().getKey();
            Class<?> type = map.getKey().getType();
            Relation target = relations.get(key);
            if (!type.isInstance(target)) {
                map.getValue().entrySet().stream().forEach((list) -> {
                    list.getValue().stream().forEach((source) -> {
                        toDelete.add(source.getUuid());
                    });
                });
            }
        });
        return toDelete;
    }

    private static void cascadingDelete(
            Deque<UUID> toDelete,
            Map<UUID, Relation> relations,
            Map<Class<?>, List<Relation>> byClass,
            Map<UUID, Map<Class<?>, List<Relation>>> reverse) {
        while (!toDelete.isEmpty()) {
            UUID key = toDelete.pop();
            Relation relation = relations.remove(key);
            if (relation == null) {
                // Already removed
                continue;
            }
            removeByClass(relation, byClass);
            relation.getReferences().stream()
                    .map((reference) -> reference.getTo()).forEach((lookup) -> {
                        removeReverse(lookup.getKey(), relation, reverse);
                    });
            Map<Class<?>, List<Relation>> references = reverse.remove(key);
            if (references != null) {
                references.values().stream().forEach((entry) -> {
                    entry.stream().forEach((source) -> {
                        toDelete.push(source.getUuid());
                    });
                });
            }
        }
    }

    private static void removeByClass(
            Relation relation, Map<Class<?>, List<Relation>> byClass) {
        List<Relation> byClassList = new ArrayList<>(byClass.get(relation.getClass()));
        byClassList.remove(relation);
        if (byClassList.isEmpty()) {
            byClass.remove(relation.getClass());
        } else {
            byClass.put(relation.getClass(), byClassList);
        }
    }

    private static void addByClass(
            Relation relation, Map<Class<?>, List<Relation>> byClass) {
        List<Relation> byClassList = byClass.get(relation.getClass());
        if (byClassList == null) {
            byClassList = new ArrayList<>();
        } else {
            byClassList = new ArrayList<>(byClassList);
        }
        byClassList.add(relation);
        byClass.put(relation.getClass(), byClassList);
    }
}
