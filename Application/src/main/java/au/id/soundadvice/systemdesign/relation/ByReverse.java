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

import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

/**
 * An immutable set keyed on class and UUID.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <E> The type to store
 */
public class ByReverse<E extends Relation> {

    private static final ByReverse EMPTY = new ByReverse(Collections.emptyMap());

    public static ByReverse empty() {
        return EMPTY;
    }

    public static class Loader<E extends Relation> {

        private final Set<UUID> deletedRelations;
        private final ByReverse<E> result;

        public Loader(ByUUID<E> relations) {
            UUID invalidReference = UUID.randomUUID();
            Map<UUID, Set<E>> reverse = relations.stream()
                    .flatMap(relation -> relation.getReferences()
                            .map(reference -> {
                                UUID targetUUID = reference.getUuid();
                                Optional<E> target = relations.get(reference.getUuid());
                                if (!target.isPresent() || !reference.getType().isInstance(target.get())) {
                                    // Target does not exist or is of incorrect type
                                    targetUUID = invalidReference;
                                }
                                return new Pair<>(targetUUID, relation);
                            }))
                    .collect(Collectors.groupingBy(Pair::getKey,
                            Collectors.mapping(Pair::getValue, Collectors.toSet())));
            Map<UUID, ByClass<E>> tmpResult = reverse.entrySet().parallelStream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey(),
                            entry -> ByClass.valueOf(entry.getValue().stream())));
            Set<UUID> tmpDelete = new HashSet<>();
            tmpDelete.add(invalidReference);
            cascade(tmpResult, tmpDelete);
            tmpDelete.remove(invalidReference);
            deletedRelations = tmpDelete;
            result = new ByReverse<>(Collections.unmodifiableMap(tmpResult))
                    .removeAll(deletedRelations.stream().flatMap(uuid
                            -> relations.get(uuid).map(Stream::of).orElse(Stream.empty())));
        }

        public Stream<UUID> getDeletedRelations() {
            return deletedRelations.stream();
        }

        public ByReverse<E> build() {
            return result;
        }
    }

    private static <E extends Relation> void cascade(
            Map<UUID, ByClass<E>> reverse, Set<UUID> seed) {
        Deque<UUID> stack = seed.stream()
                .collect(Collectors.toCollection(ArrayDeque<UUID>::new));
        while (!stack.isEmpty()) {
            UUID current = stack.pop();
            ByClass<E> references = reverse.get(current);
            if (references != null) {
                references.values()
                        .flatMap(byClass -> byClass.stream())
                        .forEach(dependant -> {
                            if (seed.add(dependant.getUuid())) {
                                stack.push(dependant.getUuid());
                            }
                        });
            }
        }
    }

    private final Map<UUID, ByClass<E>> relations;

    private ByReverse(Map<UUID, ByClass<E>> unmodifiable) {
        this.relations = unmodifiable;
    }

    public void cascade(Set<UUID> seed) {
        cascade(relations, seed);
    }

    public <T> Stream<T> find(UUID target, Class<T> fromType) {
        ByClass<E> byClass = relations.get(target);
        if (byClass == null) {
            return Stream.empty();
        } else {
            return byClass.get(fromType);
        }
    }

    public Stream<Relation> find(UUID target) {
        ByClass<E> byClass = relations.get(target);
        if (byClass == null) {
            return Stream.empty();
        } else {
            return byClass.stream();
        }
    }

    @CheckReturnValue
    private ByReverse<E> putImpl(E value) {
        Map<UUID, ByClass<E>> map = new HashMap<>(relations);
        value.getReferences()
                .forEach(relation -> {
                    UUID targetUUID = relation.getUuid();
                    ByClass<E> byClass = map.get(targetUUID);
                    if (byClass == null) {
                        byClass = ByClass.empty();
                    }
                    byClass = byClass.put(value);
                    map.put(targetUUID, byClass);
                });
        return new ByReverse<>(Collections.unmodifiableMap(map));
    }

    @CheckReturnValue
    public ByReverse<E> replace(Optional<E> old, E value) {
        if (value.equals(old.orElse(null))) {
            return this;
        } else {
            ByReverse<E> tmp;
            if (old.isPresent() && !value.equals(old.get())) {
                tmp = removeAll(Stream.of(old.get()));
            } else {
                tmp = this;
            }
            return tmp.putImpl(value);
        }
    }

    @CheckReturnValue
    public ByReverse<E> removeAll(Stream<E> toDelete) {
        Map<UUID, List<UUID>> toDeleteByTarget = toDelete
                .flatMap(source -> {
                    return source.getReferences()
                            .map(reference -> {
                                UUID targetUUID = reference.getUuid();
                                return new Pair<>(targetUUID, source.getUuid());
                            });
                })
                .collect(Collectors.groupingBy(
                        Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toList())));
        if (toDeleteByTarget.isEmpty()) {
            // There were no references
            return this;
        }
        Map<UUID, ByClass<E>> map = new HashMap<>(relations);
        toDeleteByTarget.entrySet().stream()
                .forEach(entry -> {
                    ByClass<E> byClass = map.get(entry.getKey());
                    if (byClass != null) {
                        byClass = byClass.removeAll(entry.getValue());
                        if (byClass.isEmpty()) {
                            map.remove(entry.getKey());
                        } else {
                            map.put(entry.getKey(), byClass);
                        }
                    }
                });
        if (map.isEmpty()) {
            return EMPTY;
        } else {
            return new ByReverse<>(Collections.unmodifiableMap(map));
        }
    }
}