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
package au.id.soundadvice.systemdesign.moduleapi.relation;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public interface Relations {

    /**
     * Returns an object matching the lookup.
     *
     * @param <T> The expected relation type
     * @param key The key type
     * @param type The expected relation type
     * @return
     */
    public <T extends Relation> Optional<T> get(UUID key, Class<T> type);

    /**
     * Returns an object matching the lookup. Equivalent to
     * get(sample.getUuid(), sample.getClass());
     *
     * @param <T> The expected relation type
     * @param sample A sample object of type T
     * @return
     */
    public <T extends Relation> Optional<T> get(T sample);

    /**
     * Returns relations of the nominated type
     *
     * @param <T> The type of relation to search for
     * @param type The type of relation to search for
     * @return A stream of relations matching type
     */
    public <T extends Relation> Stream<T> findByClass(Class<T> type);

    /**
     * Returns a list of objects with references to the given key.
     *
     * @param key The key type
     * @return
     */
    public Stream<Relation> findReverse(UUID key);

    /**
     * Returns a list of objects with references to the given key.
     *
     * @param <F> The expected relation type
     * @param key The key type
     * @param fromType The expected relation type
     * @return
     */
    public <F extends Relation> Stream<F> findReverse(
            UUID key, Class<F> fromType);

    @CheckReturnValue
    public <T extends Relation> Relations add(T newRelation);

    @CheckReturnValue
    public Relations remove(UUID uuid);

    public int size();
}
