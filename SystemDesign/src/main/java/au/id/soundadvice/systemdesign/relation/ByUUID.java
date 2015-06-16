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
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 * An immutable set keyed on UUID.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <E> The identifiable type to store
 */
public class ByUUID<E extends Identifiable> {

    private static final ByUUID empty = new ByUUID(Collections.emptyMap());

    public static <T extends Identifiable> ByUUID<T> empty() {
        return empty;
    }

    static <T extends Identifiable> ByUUID valueOf(Collection<? extends T> relations) {
        return new ByUUID(Collections.unmodifiableMap(
                relations.parallelStream().collect(
                        Collectors.<T, UUID, T>toConcurrentMap(
                                T::getUuid,
                                Function.identity()))));
    }

    private final Map<UUID, E> relations;

    private ByUUID(Map<UUID, E> unmodifiable) {
        this.relations = unmodifiable;
    }

    public E get(UUID key) {
        return relations.get(key);
    }

    @CheckReturnValue
    public ByUUID put(E value) {
        E old = relations.get(value.getUuid());
        if (value.equals(old)) {
            return this;
        } else {
            Map<UUID, E> map = new HashMap<>(relations);
            map.put(value.getUuid(), value);
            return new ByUUID(Collections.unmodifiableMap(map));
        }
    }

    @CheckReturnValue
    public ByUUID remove(UUID key) {
        if (relations.containsKey(key)) {
            if (relations.size() == 1) {
                return empty;
            } else {
                Map<UUID, E> map = new HashMap<>(relations);
                map.remove(key);
                return new ByUUID(Collections.unmodifiableMap(map));
            }
        } else {
            return this;
        }
    }

    @CheckReturnValue
    public ByUUID removeAll(Collection<UUID> keys) {
        List<UUID> toRemove = keys.parallelStream()
                .filter(uuid -> relations.containsKey(uuid))
                .collect(Collectors.toList());
        if (toRemove.isEmpty()) {
            return this;
        } else {
            if (relations.size() == toRemove.size()) {
                return empty;
            } else {
                Map<UUID, E> map = new HashMap<>(relations);
                map.keySet().removeAll(toRemove);
                return new ByUUID(Collections.unmodifiableMap(map));
            }
        }
    }

    public int size() {
        return relations.size();
    }

    public Collection<E> values() {
        return relations.values();
    }

    public boolean contains(UUID key) {
        return relations.containsKey(key);
    }

    boolean isEmpty() {
        return relations.isEmpty();
    }

}