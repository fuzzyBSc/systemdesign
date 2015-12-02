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
package au.id.soundadvice.systemdesign.model;

import au.id.soundadvice.systemdesign.relation.Relation;
import java.util.Optional;
import java.util.UUID;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <T> The type of object being compared
 */
public class RelationDiff<T extends Relation> {

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

    public static <T extends Relation> RelationDiff<T> get(
            Optional<Baseline> was, Baseline is, UUID uuid, Class<T> type) {
        Optional<T> wasInstance = was.flatMap(
                wasBaseline -> wasBaseline.get(uuid, type));
        Optional<T> isInstance = is.get(uuid, type);
        return new RelationDiff<>(was, wasInstance, is, isInstance);
    }

    public static <T extends Relation> RelationDiff<T> get(
            Optional<Baseline> was, Baseline is, T sample) {
        Optional<T> wasInstance = was.flatMap(
                wasBaseline -> wasBaseline.get(sample));
        Optional<T> isInstance = is.get(sample);
        return new RelationDiff<>(was, wasInstance, is, isInstance);
    }

    public boolean isDiff() {
        return wasBaseline.isPresent();
    }

    public boolean isChanged() {
        return isDiff() && !wasInstance.equals(isInstance);
    }

    public boolean isAdded() {
        return isDiff() && !wasInstance.isPresent() && isInstance.isPresent();
    }

    public boolean isDeleted() {
        return wasInstance.isPresent() && !isInstance.isPresent();
    }

    private RelationDiff(
            Optional<Baseline> wasBaseline, Optional<T> wasInstance,
            Baseline isBaseline, Optional<T> isInstance) {
        this.wasBaseline = wasBaseline;
        this.wasInstance = wasInstance;
        this.isBaseline = isBaseline;
        this.isInstance = isInstance;
    }

}
