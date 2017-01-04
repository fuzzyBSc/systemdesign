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
package au.id.soundadvice.systemdesign.entity;

import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
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
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;

/**
 * An immutable set keyed on class and identifier.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ByReverse {

    private static final ByReverse EMPTY = new ByReverse(Collections.emptyMap());

    public static ByReverse empty() {
        return EMPTY;
    }

    public static class Loader {

        private final Set<String> deletedRecords;
        private final ByReverse result;

        public Loader(ByIdentifier records) {
            String invalidReference = UUID.randomUUID().toString();
            Map<String, Set<Record>> reverse = records.stream()
                    .flatMap(record -> record.getReferenceFields()
                            .map(Map.Entry<String, String>::getValue)
                            .map(targetIdentifier -> {
                                Optional<Record> target = records.get(targetIdentifier);
                                if (!target.isPresent()) {
                                    // Target does not exist
                                    targetIdentifier = invalidReference;
                                }
                                return new Pair<>(targetIdentifier, record);
                            }))
                    .collect(Collectors.groupingBy(Pair::getKey,
                            Collectors.mapping(Pair::getValue, Collectors.toSet())));
            Map<String, HashIndex<Table>> tmpResult = reverse.entrySet().parallelStream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey(),
                            entry -> HashIndex.valueOf(
                                    record -> Stream.of(record.getType()), entry.getValue().stream())));
            Set<String> tmpDelete = new HashSet<>();
            tmpDelete.add(invalidReference);
            cascade(tmpResult, tmpDelete);
            tmpDelete.remove(invalidReference);
            deletedRecords = tmpDelete;
            result = new ByReverse(Collections.unmodifiableMap(tmpResult))
                    .removeAll(deletedRecords.stream().flatMap(key
                            -> records.get(key).map(Stream::of).orElse(Stream.empty())));
        }

        public Stream<String> getDeletedRecords() {
            return deletedRecords.stream();
        }

        public ByReverse build() {
            return result;
        }
    }

    private static void cascade(
            Map<String, HashIndex<Table>> reverse, Set<String> seed) {
        Deque<String> stack = seed.stream()
                .collect(Collectors.toCollection(ArrayDeque<String>::new));
        while (!stack.isEmpty()) {
            String current = stack.pop();
            HashIndex<Table> references = reverse.get(current);
            if (references != null) {
                references.values()
                        .flatMap(byClass -> byClass.stream())
                        .forEach(dependant -> {
                            if (seed.add(dependant.getIdentifier())) {
                                stack.push(dependant.getIdentifier());
                            }
                        });
            }
        }
    }

    private final Map<String, HashIndex<Table>> records;

    private ByReverse(Map<String, HashIndex<Table>> unmodifiable) {
        this.records = unmodifiable;
    }

    public void cascade(Set<String> seed) {
        cascade(records, seed);
    }

    public Stream<Record> find(String target, Table fromType) {
        HashIndex<Table> byType = records.get(target);
        if (byType == null) {
            return Stream.empty();
        } else {
            return byType.get(fromType);
        }
    }

    public Stream<Record> find(String target) {
        HashIndex<Table> byType = records.get(target);
        if (byType == null) {
            return Stream.empty();
        } else {
            return byType.stream();
        }
    }

    @CheckReturnValue
    private ByReverse putImpl(Record value) {
        Map<String, HashIndex<Table>> map = new HashMap<>(records);
        value.getReferenceFields()
                .map(Map.Entry<String, String>::getValue)
                .forEach(targetIdentifier -> {
                    HashIndex<Table> byType = map.get(targetIdentifier);
                    if (byType == null) {
                        byType = HashIndex.empty(record -> Stream.of(record.getType()));
                    }
                    byType = byType.replace(Optional.empty(), value);
                    map.put(targetIdentifier, byType);
                });
        return new ByReverse(Collections.unmodifiableMap(map));
    }

    @CheckReturnValue
    public ByReverse replace(Optional<Record> old, Record value) {
        if (value.equals(old.orElse(null))) {
            return this;
        } else {
            ByReverse tmp;
            if (old.isPresent() && !value.equals(old.get())) {
                tmp = removeAll(Stream.of(old.get()));
            } else {
                tmp = this;
            }
            return tmp.putImpl(value);
        }
    }

    @CheckReturnValue
    public ByReverse removeAll(Stream<Record> toDelete) {
        Map<String, List<String>> toDeleteByTarget = toDelete
                .flatMap(source -> {
                    return source.getReferenceFields()
                            .map(Map.Entry<String, String>::getValue)
                            .map(targetIdentifier -> {
                                return new Pair<>(targetIdentifier, source.getIdentifier());
                            });
                })
                .collect(Collectors.groupingBy(
                        Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toList())));
        if (toDeleteByTarget.isEmpty()) {
            // There were no references
            return this;
        }
        Map<String, HashIndex<Table>> map = new HashMap<>(records);
        toDeleteByTarget.entrySet().stream()
                .forEach(entry -> {
                    HashIndex<Table> byType = map.get(entry.getKey());
                    if (byType != null) {
                        byType = byType.removeAll(entry.getValue());
                        if (byType.isEmpty()) {
                            map.remove(entry.getKey());
                        } else {
                            map.put(entry.getKey(), byType);
                        }
                    }
                });
        if (map.isEmpty()) {
            return EMPTY;
        } else {
            return new ByReverse(Collections.unmodifiableMap(map));
        }
    }
}
