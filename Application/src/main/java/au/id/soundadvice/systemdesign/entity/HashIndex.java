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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

/**
 * An immutable set keyed on class and UUID.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <I> Index type
 */
public class HashIndex<I> {

    public static <I> HashIndex<I> empty(Function<Record, Stream<I>> bucketExtractor) {
        return new HashIndex<>(bucketExtractor, Collections.emptyMap());
    }

    public static <I> HashIndex<I> valueOf(Function<Record, Stream<I>> bucketExtractor, Stream<Record> records) {
        Map<I, List<Record>> pass1 = records
                .flatMap(record -> bucketExtractor.apply(record)
                        .map(bucket -> new Pair<>(bucket, record)))
                .collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toList())));
        Map<I, ByIdentifier> pass2 = pass1.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> ByIdentifier.valueOf(entry.getValue().stream())));
        return new HashIndex<>(bucketExtractor, Collections.unmodifiableMap(pass2));
    }

    private final Function<Record, Stream<I>> bucketExtractor;
    private final Map<I, ByIdentifier> records;

    private HashIndex(Function<Record, Stream<I>> bucketExtractor, Map<I, ByIdentifier> unmodifiable) {
        this.bucketExtractor = bucketExtractor;
        this.records = unmodifiable;
    }

    public Stream<Record> get(I key) {
        ByIdentifier result = records.get(key);
        if (result == null) {
            return Stream.empty();
        } else {
            return result.stream();
        }
    }

    public Stream<Record> stream() {
        return records.values().stream().flatMap(ByIdentifier::stream);
    }

    @CheckReturnValue
    private HashIndex<I> putImpl(Record value) {
        Stream<I> buckets = bucketExtractor.apply(value);
        HashMap<I, ByIdentifier> map = new HashMap<>(records);
        buckets.sequential()
                .forEach(bucket -> {
                    ByIdentifier set = records.get(bucket);
                    if (set == null) {
                        set = ByIdentifier.empty();
                    }
                    set = set.put(value);
                    map.put(bucket, set);
                });
        return new HashIndex<>(
                bucketExtractor, Collections.unmodifiableMap(map));
    }

    @CheckReturnValue
    public HashIndex<I> replace(Optional<Record> wasValue, Record isValue) {
        HashIndex<I> tmp = this;
        if (wasValue.isPresent()) {
            tmp = remove(wasValue.get());
        }
        return tmp.putImpl(isValue);
    }

    @CheckReturnValue
    public HashIndex<I> remove(Record wasValue) {
        Stream<I> wasBuckets = bucketExtractor.apply(wasValue);
        HashMap<I, ByIdentifier> map = new HashMap<>(records);
        wasBuckets.sequential().forEach(bucket -> {
            ByIdentifier bucketEntries = map.get(bucket).remove(wasValue.getIdentifier());
            if (bucketEntries.isEmpty()) {
                map.remove(bucket);
            } else {
                map.put(bucket, bucketEntries);
            }
        });
        return new HashIndex<>(bucketExtractor, Collections.unmodifiableMap(map));
    }

    @CheckReturnValue
    public HashIndex<I> removeAll(Collection<String> identifiers) {
        return new HashIndex<>(bucketExtractor, Collections.<I, ByIdentifier>unmodifiableMap(
                records.entrySet().parallelStream()
                .map(entry -> new Pair<>(
                        entry.getKey(), entry.getValue().removeAll(identifiers)))
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toConcurrentMap(
                        entry -> entry.getKey(),
                        entry -> entry.getValue()))));
    }

    public Stream<ByIdentifier> values() {
        return records.values().stream();
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }
}
