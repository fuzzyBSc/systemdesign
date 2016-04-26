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

import au.id.soundadvice.systemdesign.moduleapi.Identifiable;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 * An immutable set keyed on identifier.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <E> The identifiable type to store
 */
public class ByIdentifier<E extends Relation> {

    @Override
    public String toString() {
        return relations.toString();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(this.relations);
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
        final ByIdentifier<?> other = (ByIdentifier<?>) obj;
        if (!Objects.equals(this.relations, other.relations)) {
            return false;
        }
        return true;
    }

    private static final ByIdentifier EMPTY = new ByIdentifier(Collections.emptyMap());

    public static <T extends Relation> ByIdentifier<T> empty() {
        return EMPTY;
    }

    static <T extends Identifiable> ByIdentifier valueOf(Stream<T> relations) {
        return new ByIdentifier(Collections.unmodifiableMap(
                relations.parallel().collect(
                        Collectors.toConcurrentMap(Identifiable::getIdentifier, Function.identity()))));
    }

    private final Map<String, E> relations;

    private ByIdentifier(Map<String, E> unmodifiable) {
        this.relations = unmodifiable;
    }

    public Optional<E> get(String key) {
        return Optional.ofNullable(relations.get(key));
    }

    @CheckReturnValue
    public ByIdentifier put(E value) {
        E old = relations.get(value.getIdentifier());
        if (value.equals(old)) {
            return this;
        } else {
            Map<String, E> map = new HashMap<>(relations);
            map.put(value.getIdentifier(), value);
            return new ByIdentifier(Collections.unmodifiableMap(map));
        }
    }

    @CheckReturnValue
    public ByIdentifier remove(String key) {
        if (relations.containsKey(key)) {
            if (relations.size() == 1) {
                return EMPTY;
            } else {
                Map<String, E> map = new HashMap<>(relations);
                map.remove(key);
                return new ByIdentifier(Collections.unmodifiableMap(map));
            }
        } else {
            return this;
        }
    }

    @CheckReturnValue
    public ByIdentifier removeAll(Collection<String> keys) {
        List<String> toRemove = keys.parallelStream()
                .filter(key -> relations.containsKey(key))
                .collect(Collectors.toList());
        if (toRemove.isEmpty()) {
            return this;
        } else if (relations.size() == toRemove.size()) {
            return EMPTY;
        } else {
            Map<String, E> map = new HashMap<>(relations);
            map.keySet().removeAll(toRemove);
            return new ByIdentifier(Collections.unmodifiableMap(map));
        }
    }

    public int size() {
        return relations.size();
    }

    public Stream<E> stream() {
        return relations.values().stream();
    }

    public boolean contains(String key) {
        return relations.containsKey(key);
    }

    boolean isEmpty() {
        return relations.isEmpty();
    }

}
