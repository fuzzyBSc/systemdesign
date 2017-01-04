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
package au.id.soundadvice.systemdesign.moduleapi.collection;

import au.id.soundadvice.systemdesign.moduleapi.entity.ConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public interface Baseline {

    /**
     * Returns a record matching the lookup.
     *
     * @param identifier The record identifier
     * @param type The expected record type
     * @return
     */
    public Optional<Record> get(RecordID identifier, Table type);

    /**
     * Returns a record matching the lookup.
     *
     * @param identifier The record identifier
     * @return
     */
    public Optional<Record> getAnyType(RecordID identifier);

    /**
     * Returns a record matching the lookup. Equivalent to
     * get(sample.getIdentifier(), sample.getType());
     *
     * @param sample A sample record that may or may not still be in the current
     * baseline
     * @return
     */
    public Optional<Record> get(Record sample);

    /**
     * Returns records of the nominated type
     *
     * @param type The type of record to search for
     * @return A stream of records matching type
     */
    public Stream<Record> findByType(Table type);

    /**
     * Returns records of the nominated type
     *
     * @param parentIdentifier The identifier of the traced object in the parent
     * baseline
     * @return A stream of records that trace to parentIdentifier
     */
    public Stream<Record> findByTrace(Optional<RecordID> parentIdentifier);

    /**
     * Returns records that completely include the nominated scope. If the
     * scope's direction is None, all directions are included.
     *
     * @param scope The scope of the connection records to return
     * @return A stream of records that match the nominated scope
     */
    public Stream<Record> findByScope(ConnectionScope scope);

    /**
     * Returns records whose long name is equal to the specified value.
     *
     * @param longName The long name of the records to return
     * @return A stream of records that match the nominated long name
     */
    public Stream<Record> findByLongName(String longName);

    /**
     * Returns a list of records in the baseline with references to the given
     * identifier.
     *
     * @param identifier The identifier to find references to
     * @return A stream of records that have references to identifier
     */
    public Stream<Record> findReverse(RecordID identifier);

    /**
     * Returns a list of records in the current baseline with references to the
     * given identifier, of the nominated type.
     *
     * @param identifier The identifier to find references to
     * @param fromType The expected record type
     * @return A stream of record of type fromType with references to identifier
     */
    public Stream<Record> findReverse(
            RecordID identifier, Table fromType);

    /**
     * Adds a new record, replacing any existing record with the same
     * identifier.
     *
     * @param newRecord The record to add
     * @return The new baseline
     */
    @CheckReturnValue
    public Baseline add(Record newRecord);

    /**
     * Removes the identified record, if it exists.
     *
     * @param identifier The identifier of the record to remove
     * @return The new baseline
     */
    @CheckReturnValue
    public Baseline remove(RecordID identifier);

    /**
     * Returns the number of records in the baseline.
     *
     * @return
     */
    public int size();

    public Stream<Record> stream();

    @CheckReturnValue
    public Baseline mergeRecords(String now, Stream<Record> toMerge, BinaryOperator<Record> mergeFunction);
}
