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
package au.id.soundadvice.systemdesign.storage.files;

import au.com.bytecode.opencsv.CSVReader;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class RecordReader implements AutoCloseable {

    private final CSVReader csvreader;
    private final String[] header;
    private final Table recordType;

    public RecordReader(Table recordType, CSVReader csvreader) throws IOException {
        this.recordType = recordType;
        this.csvreader = csvreader;

        this.header = readLine(csvreader).orElse(new String[0]);
    }

    @Override
    public void close() throws IOException {
        csvreader.close();
    }

    public Iterator<Record> iterator() {
        return new Iterator<Record>() {
            Optional<Record> nextRecord = Optional.empty();

            @Override
            public boolean hasNext() {
                if (nextRecord.isPresent()) {
                    return true;
                } else {
                    try {
                        nextRecord = read();
                        return (nextRecord.isPresent());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }

            @Override
            public Record next() {
                if (nextRecord.isPresent() || hasNext()) {
                    Optional<Record> bean = nextRecord;
                    nextRecord = Optional.empty();
                    return bean.get();
                } else {
                    throw new NoSuchElementException();
                }
            }
        };
    }

    public Stream<Record> lines() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                iterator(), Spliterator.NONNULL), false);
    }

    public Optional<Record> read() throws IOException {
        return readLine(csvreader).map(line -> lineToRecord(line));
    }

    Record lineToRecord(String[] line) {
        Map<String, String> fields = new HashMap<>();
        int commonLength = Math.min(header.length, line.length);
        for (int ii = 0; ii < commonLength; ++ii) {
            fields.put(header[ii], line[ii]);
        }
        return Record.load(recordType, fields);
    }

    private static Optional<String[]> readLine(CSVReader csvreader) throws IOException {
        for (;;) {
            String[] line = csvreader.readNext();
            while (line != null && line.length == 1 && line[0].isEmpty()) {
                // Ignore empty lines
                line = csvreader.readNext();
            }
            return Optional.ofNullable(line);
        }
    }

    public Stream<String> fields() {
        return Stream.of(header);
    }

}
