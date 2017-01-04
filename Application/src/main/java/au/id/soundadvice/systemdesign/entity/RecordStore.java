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

import au.id.soundadvice.systemdesign.moduleapi.entity.ConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;

/**
 * An immutable store of records, suitable for use within an undo buffer.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class RecordStore implements Baseline {

    @Override
    public String toString() {
        return byIdentifier.toString();
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + Objects.hashCode(this.byIdentifier);
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
        final RecordStore other = (RecordStore) obj;
        if (!Objects.equals(this.byIdentifier, other.byIdentifier)) {
            return false;
        }
        return true;
    }

    private static Stream<Table> extractType(Record record) {
        return Stream.of(record.getType());
    }

    private static Stream<Optional<String>> extractTrace(Record record) {
        return Stream.of(record.getTrace());
    }

    private static Stream<ConnectionScope> extractScope(Record record) {
        return record.getConnectionScope().enumerateDirections(true);
    }

    private static Stream<String> extractLongName(Record record) {
        return Stream.of(record.getLongName());
    }

    public static RecordStore valueOf(Stream<Record> input) {
        // Collect the stream in order to duplicate it
        List<Record> collectedInput = input.collect(Collectors.toList());
        ByIdentifier byIdentifier = ByIdentifier.valueOf(collectedInput.stream());
        HashIndex<Table> byType = HashIndex.valueOf(
                RecordStore::extractType, collectedInput.stream());
        HashIndex<Optional<String>> byTrace = HashIndex.valueOf(
                RecordStore::extractTrace, collectedInput.stream());
        HashIndex<ConnectionScope> byScope = HashIndex.valueOf(
                RecordStore::extractScope, collectedInput.stream());
        HashIndex<String> byLongName = HashIndex.valueOf(
                RecordStore::extractLongName, collectedInput.stream());
        ByReverse.Loader reverseLoader = new ByReverse.Loader(byIdentifier);
        ByReverse byReverse = reverseLoader.build();

        // Delete any items needed to establish referential integrity
        List<String> toDelete = reverseLoader.getDeletedRecords().parallel()
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            byIdentifier = byIdentifier.removeAll(toDelete);
            byType = byType.removeAll(toDelete);
            byTrace = byTrace.removeAll(toDelete);
            byScope = byScope.removeAll(toDelete);
            // byReverse entries have already been removed
        }

        return new RecordStore(
                byIdentifier,
                byType,
                byTrace,
                byScope,
                byLongName,
                byReverse);
    }

    private static final RecordStore EMPTY = new RecordStore(
            ByIdentifier.empty(),
            HashIndex.empty(RecordStore::extractType),
            HashIndex.empty(RecordStore::extractTrace),
            HashIndex.empty(RecordStore::extractScope),
            HashIndex.empty(RecordStore::extractLongName),
            ByReverse.empty());

    public static RecordStore empty() {
        return EMPTY;
    }

    private final ByIdentifier byIdentifier;
    private final HashIndex<Table> byType;
    private final HashIndex<Optional<String>> byTrace;
    private final HashIndex<ConnectionScope> byScope;
    private final HashIndex<String> byLongName;
    private final ByReverse reverseReferences;

    private RecordStore(
            ByIdentifier relations,
            HashIndex<Table> byType,
            HashIndex<Optional<String>> byTrace,
            HashIndex<ConnectionScope> byScope,
            HashIndex<String> byLongName,
            ByReverse reverseRelations) {
        this.byIdentifier = relations;
        this.byType = byType;
        this.byTrace = byTrace;
        this.byScope = byScope;
        this.byLongName = byLongName;
        this.reverseReferences = reverseRelations;
    }

    @Override
    public Optional<Record> get(String identifier, Table type) {
        return getAnyType(identifier)
                .filter(record -> type.equals(record.getType()));
    }

    @Override
    public Optional<Record> getAnyType(String identifier) {
        return byIdentifier.get(identifier);
    }

    @Override
    public Optional<Record> get(Record sample) {
        return this.get(sample.getIdentifier(), sample.getType());
    }

    @Override
    public Stream<Record> findByType(Table type) {
        return byType.get(type);
    }

    @Override
    public Stream<Record> findByTrace(Optional<String> parentIdentifier) {
        return byTrace.get(parentIdentifier);
    }

    @Override
    public Stream<Record> findByScope(ConnectionScope scope) {
        return byScope.get(scope);
    }

    @Override
    public Stream<Record> findByLongName(String value) {
        return byLongName.get(value);
    }

    @Override
    public Stream<Record> findReverse(String key, Table fromType) {
        return reverseReferences.find(key, fromType);
    }

    @Override
    public Stream<Record> findReverse(String key) {
        return reverseReferences.find(key);
    }

    @CheckReturnValue
    @Override
    public RecordStore add(Record value) {
        // Check referential integrity
        boolean referencesOK = value.getReferenceFields().parallel()
                .map(Map.Entry<String, String>::getValue)
                .allMatch(targetIdentifier -> {
                    Optional<Record> target = byIdentifier.get(targetIdentifier);
                    return target.isPresent();
                });
        if (!referencesOK) {
            // Leave store as it was - don't add the new record
            return this;
        }

        String key = value.getIdentifier();
        Optional<Record> oldValue = byIdentifier.get(key);
        if (value.equals(oldValue.orElse(null))) {
            return this;
        } else {
            RecordStore tmp = this;
            ByIdentifier tmpRelations = tmp.byIdentifier.put(value);
            HashIndex<Table> tmpByType = tmp.byType.replace(oldValue, value);
            HashIndex<Optional<String>> tmpByTrace = tmp.byTrace.replace(oldValue, value);
            HashIndex<ConnectionScope> tmpByScope = tmp.byScope.replace(oldValue, value);
            HashIndex<String> tmpByLongName = tmp.byLongName.replace(oldValue, value);
            ByReverse tmpReverseRelations
                    = tmp.reverseReferences.replace(oldValue, value);
            return new RecordStore(
                    tmpRelations, tmpByType, tmpByTrace, tmpByScope, tmpByLongName,
                    tmpReverseRelations);
        }
    }

    @CheckReturnValue
    @Override
    public RecordStore remove(String key) {
        return removeAll(Stream.of(key));
    }

    @CheckReturnValue
    public RecordStore removeAll(Stream<String> seed) {
        HashSet<String> toDelete = seed.parallel()
                .filter(key -> byIdentifier.get(key).isPresent())
                .collect(Collectors.toCollection(HashSet::new));
        if (toDelete.isEmpty()) {
            return this;
        } else {
            reverseReferences.cascade(toDelete);

            ByIdentifier tmpRelations = byIdentifier.removeAll(toDelete);
            HashIndex<Table> tmpByType = byType.removeAll(toDelete);
            HashIndex<Optional<String>> tmpByTrace = byTrace.removeAll(toDelete);
            HashIndex<ConnectionScope> tmpByScope = byScope.removeAll(toDelete);
            HashIndex<String> tmpByLongName = byLongName.removeAll(toDelete);
            ByReverse tmpReverseRelations
                    = reverseReferences.removeAll(
                            toDelete.stream().flatMap(key
                                    -> byIdentifier.get(key).map(Stream::of).orElse(Stream.empty())));
            return new RecordStore(
                    tmpRelations, tmpByType, tmpByTrace, tmpByScope, tmpByLongName,
                    tmpReverseRelations);
        }
    }

    @Override
    public int size() {
        return byIdentifier.size();
    }

    public boolean isEmpty() {
        return byIdentifier.isEmpty();
    }

    @Override
    public Stream<Record> stream() {
        return byIdentifier.stream();
    }

    @Override
    public Baseline mergeRecords(String now, Stream<Record> toMerge, BinaryOperator<Record> mergeFunction) {
        RecordStore result = this;

        Iterator<Record> it = toMerge.iterator();
        if (it.hasNext()) {
            Record current = it.next();

            while (it.hasNext()) {
                Record next = it.next();
                Record merged = mergeFunction.apply(current, next);

                result = result.add(merged);
                if (current.getIdentifier().equals(merged.getIdentifier())) {
                    result = result.redirectReferences(now, merged.getIdentifier(), current.getIdentifier());
                    result = result.remove(current.getIdentifier());
                }
                if (next.getIdentifier().equals(merged.getIdentifier())) {
                    result = result.redirectReferences(now, merged.getIdentifier(), next.getIdentifier());
                    result = result.remove(next.getIdentifier());
                }
            }
        }
        return result;
    }

    @CheckReturnValue
    private RecordStore redirectReferences(String now, String toIdentifier, String fromIdentifier) {
        Iterator<Record.Builder> it = reverseReferences.find(fromIdentifier)
                .map(record -> record.asBuilder().redirectReferences(toIdentifier, fromIdentifier))
                .iterator();
        RecordStore result = this;
        while (it.hasNext()) {
            Record record = it.next().build(now);
            result = result.add(record);
        }
        return result;
    }
}
