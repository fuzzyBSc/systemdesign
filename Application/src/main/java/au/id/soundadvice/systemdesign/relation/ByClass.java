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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

/**
 * An immutable set keyed on class and UUID.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <E> The identifiable type to store
 */
public class ByClass<E extends Relation> {

    private static final ByClass EMPTY = new ByClass(Collections.emptyMap());

    public static ByClass empty() {
        return EMPTY;
    }

    static <E extends Relation> ByClass<E> valueOf(Stream<E> relations) {
        ConcurrentMap<Class<?>, List<E>> byClass = relations.parallel()
                .collect(Collectors.<E, Class<?>>groupingByConcurrent(E::getClass));
        return new ByClass(Collections.unmodifiableMap(
                byClass.entrySet().parallelStream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey(),
                        entry -> ByUUID.valueOf(entry.getValue().stream())))));
    }

    private final Map<Class<?>, ByUUID<E>> relations;

    private ByClass(Map<Class<?>, ByUUID<E>> unmodifiable) {
        this.relations = unmodifiable;
    }

    public <T> Stream<T> get(Class<T> key) {
        ByUUID<E> result = relations.get(key);
        if (result == null) {
            return Stream.empty();
        } else {
            return result.stream().map(e -> key.cast(e));
        }
    }

    public Stream<Relation> stream() {
        return relations.values().stream().flatMap(ByUUID::stream);
    }

    @CheckReturnValue
    private ByClass<E> putImpl(E value) {
        HashMap<Class<?>, ByUUID<E>> map = new HashMap<>(relations);
        ByUUID<E> set = relations.get(value.getClass());
        if (set == null) {
            set = ByUUID.empty();
        }
        set = set.put(value);
        map.put(value.getClass(), set);
        return new ByClass<>(Collections.unmodifiableMap(map));
    }

    @CheckReturnValue
    public ByClass<E> put(E value) {
        ByClass<E> tmp = remove(value.getUuid());
        return tmp.putImpl(value);
    }

    @CheckReturnValue
    public ByClass<E> remove(UUID key) {
        return new ByClass<>(Collections.<Class<?>, ByUUID<E>>unmodifiableMap(
                relations.entrySet().parallelStream()
                .map(entry -> new Pair<>(
                        entry.getKey(), entry.getValue().remove(key)))
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toConcurrentMap(
                        entry -> entry.getKey(),
                        entry -> entry.getValue()))));
    }

    @CheckReturnValue
    public ByClass<E> removeAll(Collection<UUID> keys) {
        return new ByClass<>(Collections.<Class<?>, ByUUID<E>>unmodifiableMap(
                relations.entrySet().parallelStream()
                .map(entry -> new Pair<>(
                        entry.getKey(), entry.getValue().removeAll(keys)))
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toConcurrentMap(
                        entry -> entry.getKey(),
                        entry -> entry.getValue()))));
    }

    public Stream<ByUUID<E>> values() {
        return relations.values().stream();
    }

    public boolean isEmpty() {
        return relations.isEmpty();
    }
}
