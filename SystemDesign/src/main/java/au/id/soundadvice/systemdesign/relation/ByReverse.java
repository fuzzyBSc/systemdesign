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

import au.id.soundadvice.systemdesign.files.Identifiable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * An immutable set keyed on class and UUID.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <E> The type to store
 */
public class ByReverse<E extends Relation> {

    private static final ByReverse empty = new ByReverse(Collections.emptyMap());

    public static ByReverse empty() {
        return empty;
    }

    public static class Loader<E extends Relation> {

        private final Set<E> toDelete;
        private final ByReverse<E> result;

        public Loader(ByUUID<E> relations) {
            ConcurrentMap<UUID, List<E>> reverse = new ConcurrentHashMap<>();
            Set<E> tmpDelete = Collections.newSetFromMap(new ConcurrentHashMap<>());
            relations.values().parallelStream()
                    .forEach(relation -> {
                        relation.getReferences().parallelStream()
                        .forEach(reference -> {
                            ReferenceTarget<?> target = reference.getTo();
                            if (target.getType().isInstance(relations.get(target.getUuid()))) {
                                List<E> list = reverse.get(target.getUuid());
                                if (list == null) {
                                    list = Collections.synchronizedList(new ArrayList<>());
                                    List<E> old = reverse.putIfAbsent(target.getUuid(), list);
                                    if (old != null) {
                                        list = old;
                                    }
                                }
                                list.add(relation);
                            } else {
                                // Target does not exist or is of incorrect type
                                tmpDelete.add(relation);
                            }
                        });
                    });
            Map<UUID, ByClass<E>> tmpResult = reverse.entrySet().parallelStream()
                    .collect(Collectors.toMap(
                                    entry -> entry.getKey(),
                                    entry -> ByClass.valueOf(entry.getValue())));
            cascade(tmpResult, tmpDelete);
            result = new ByReverse<>(Collections.unmodifiableMap(tmpResult)).removeAll(tmpDelete);
            toDelete = Collections.unmodifiableSet(tmpDelete);
        }

        public Collection<E> getToDelete() {
            return toDelete;
        }

        public ByReverse<E> build() {
            return result;
        }
    }

    private static <E extends Identifiable> void cascade(
            Map<UUID, ByClass<E>> reverse, Set<E> seed) {
        Deque<UUID> stack = seed.stream()
                .map(Identifiable::getUuid)
                .collect(Collectors.toCollection(ArrayDeque<UUID>::new));
        while (!stack.isEmpty()) {
            UUID current = stack.pop();
            ByClass<E> references = reverse.get(current);
            if (references != null) {
                references.values().stream()
                        .forEach(byUUID -> {
                            byUUID.values().stream()
                            .forEach(dependant -> {
                                if (seed.add(dependant)) {
                                    stack.push(dependant.getUuid());
                                }
                            });
                        });
            }
        }
    }

    private final Map<UUID, ByClass<E>> relations;

    private ByReverse(Map<UUID, ByClass<E>> unmodifiable) {
        this.relations = unmodifiable;
    }

    public void cascade(Set<E> seed) {
        cascade(relations, seed);
    }

    public <T> Collection<T> get(UUID target, Class<T> fromType) {
        ByClass<E> byClass = relations.get(target);
        if (byClass == null) {
            return Collections.emptyList();
        } else {
            return byClass.get(fromType);
        }
    }

    @CheckReturnValue
    private ByReverse<E> putImpl(E value) {
        Map<UUID, ByClass<E>> map = new HashMap<>(relations);
        value.getReferences().stream()
                .forEach(relation -> {
                    ReferenceTarget<?> target = relation.getTo();
                    ByClass<E> byClass = map.get(target.getUuid());
                    if (byClass == null) {
                        byClass = ByClass.empty();
                    }
                    byClass = byClass.put(value);
                    map.put(target.getUuid(), byClass);
                });
        return new ByReverse<>(Collections.unmodifiableMap(map));
    }

    @CheckReturnValue
    public ByReverse<E> replace(@Nullable E old, E value) {
        if (value.equals(old)) {
            return this;
        } else {
            ByReverse<E> tmp;
            if (old == null) {
                tmp = this;
            } else {
                tmp = removeAll(Collections.singleton(old));
            }
            return tmp.putImpl(value);
        }
    }

    @CheckReturnValue
    public ByReverse<E> removeAll(Collection<E> toDelete) {
        Map<UUID, List<UUID>> toDeleteByTarget = new HashMap<>();
        toDelete.stream()
                .forEach(source -> {
                    source.getReferences().stream()
                    .forEach(reference -> {
                        UUID targetUUID = reference.getTo().getUuid();
                        List<UUID> list = toDeleteByTarget.get(targetUUID);
                        if (list == null) {
                            list = new ArrayList<>();
                            toDeleteByTarget.put(targetUUID, list);
                        }
                        list.add(source.getUuid());
                    });
                });
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
            return empty;
        } else {
            return new ByReverse<>(Collections.unmodifiableMap(map));
        }
    }
}
