/*
 * To change this license header, choose License Headers in Project Properties.
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
package au.id.soundadvice.systemdesign.moduleapi.collection;

import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <T> The type of object being compared
 */
public class DiffPair<T> implements DiffInfo {

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.wasInstance);
        hash = 79 * hash + Objects.hashCode(this.isInstance);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DiffPair<?> other = (DiffPair<?>) obj;
        if (!Objects.equals(this.wasInstance, other.wasInstance)) {
            return false;
        }
        if (!Objects.equals(this.isInstance, other.isInstance)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "was=" + wasInstance + ", is=" + isInstance;
    }

    public Optional<Baseline> getWasBaseline() {
        return wasBaseline;
    }

    public Optional<T> getWasInstance() {
        return wasInstance;
    }

    public Baseline getIsBaseline() {
        return isBaseline;
    }

    public Optional<T> getIsInstance() {
        return isInstance;
    }

    public T getSample() {
        return isInstance.orElseGet(() -> wasInstance.get());
    }

    private final Optional<Baseline> wasBaseline;
    private final Optional<T> wasInstance;
    private final Baseline isBaseline;
    private final Optional<T> isInstance;

    public static DiffPair<Baseline> get(Optional<Baseline> was, Baseline is) {
        return new DiffPair<>(was, was, is, Optional.of(is));
    }

    public static Stream<DiffPair<Record>> find(
            DiffPair<?> baselines,
            Function<Baseline, Stream<Record>> finder, Table recordType) {
        return baselines.map((baseline, record) -> finder.apply(baseline))
                .stream()
                .flatMap(stream -> stream)
                .map(Record::getIdentifier)
                .distinct()
                .map(recordIdentifier -> DiffPair.get(baselines, recordIdentifier, recordType));
    }

    public static DiffPair<Record> get(
            DiffPair<?> baselines, RecordID identifier, Table type) {
        Optional<Record> wasInstance = baselines.getWasBaseline().flatMap(
                wasBaseline -> wasBaseline.get(identifier, type));
        Optional<Record> isInstance = baselines.getIsBaseline().get(identifier, type);
        return new DiffPair<>(baselines.wasBaseline, wasInstance, baselines.isBaseline, isInstance);
    }

    public static DiffPair<Record> get(
            DiffPair<?> baselines, Record sample) {
        return get(baselines, sample.getIdentifier(), sample.getType());
    }

    public <O> DiffPair<O> map(Function<T, O> mapper) {
        return new DiffPair(wasBaseline, wasInstance.map(mapper),
                isBaseline, isInstance.map(mapper));
    }

    public <O> DiffPair<O> flatMap(Function<T, Optional<O>> mapper) {
        return new DiffPair(wasBaseline, wasInstance.flatMap(mapper),
                isBaseline, isInstance.flatMap(mapper));
    }

    public <O> DiffPair<O> map(BiFunction<Baseline, T, O> mapper) {
        Function<T, O> wasMapper = t -> mapper.apply(wasBaseline.get(), t);
        Function<T, O> isMapper = t -> mapper.apply(isBaseline, t);
        return new DiffPair(wasBaseline, wasInstance.map(wasMapper),
                isBaseline, isInstance.map(isMapper));
    }

    public <O> DiffPair<O> flatMap(BiFunction<Baseline, T, Optional<O>> mapper) {
        Function<T, Optional<O>> wasMapper = t -> mapper.apply(wasBaseline.get(), t);
        Function<T, Optional<O>> isMapper = t -> mapper.apply(isBaseline, t);
        return new DiffPair(wasBaseline, wasInstance.flatMap(wasMapper),
                isBaseline, isInstance.flatMap(isMapper));
    }

    public Stream<T> stream() {
        Stream.Builder<T> builder = Stream.builder();
        if (wasInstance.isPresent()) {
            builder.accept(wasInstance.get());
        }
        if (isInstance.isPresent()) {
            builder.accept(isInstance.get());
        }
        return builder.build();
    }

    @Override
    public boolean isDiff() {
        return wasBaseline.isPresent();
    }

    @Override
    public boolean isChanged() {
        return wasInstance.isPresent() && isInstance.isPresent()
                && !wasInstance.equals(isInstance);
    }

    @Override
    public boolean isAdded() {
        return isDiff() && !wasInstance.isPresent() && isInstance.isPresent();
    }

    @Override
    public boolean isDeleted() {
        return wasInstance.isPresent() && !isInstance.isPresent();
    }

    public DiffPair(
            Optional<Baseline> wasBaseline, Optional<T> wasInstance,
            Baseline isBaseline, Optional<T> isInstance) {
        this.wasBaseline = wasBaseline;
        this.wasInstance = wasInstance;
        this.isBaseline = isBaseline;
        this.isInstance = isInstance;
    }

}
