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
package au.id.soundadvice.systemdesign.files;

import au.com.bytecode.opencsv.CSVReader;
import au.id.soundadvice.systemdesign.storage.files.Merge;
import au.com.bytecode.opencsv.CSVWriter;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import au.id.soundadvice.systemdesign.storage.files.RecordReader;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class MergeTest {

    @Test
    public void testEmpty() throws Exception {
        Table unknownType = new Table.Default("unknown");
        String resultString;
        try (RecordReader ancestor = new RecordReader(unknownType, new CSVReader(new StringReader("")));
                RecordReader left = new RecordReader(unknownType, new CSVReader(new StringReader("")));
                RecordReader right = new RecordReader(unknownType, new CSVReader(new StringReader("")));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out, ISO8601.EPOCH);
            resultString = result.toString();
        }
        try (RecordReader result = new RecordReader(unknownType, new CSVReader(new StringReader(resultString)))) {
            Optional<Record> entry = result.read();
            assertFalse(entry.isPresent());
        }
    }

    @Test
    public void testEmptyWithHeaders() throws Exception {
        Table unknownType = new Table.Default("unknown");
        String resultString;
        try (RecordReader ancestor = new RecordReader(unknownType, new CSVReader(new StringReader("a,b,identifier,z")));
                RecordReader left = new RecordReader(unknownType, new CSVReader(new StringReader("a,b,identifier,z")));
                RecordReader right = new RecordReader(unknownType, new CSVReader(new StringReader("a,b,identifier,z")));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out, ISO8601.EPOCH);
            resultString = result.toString();
        }
        try (RecordReader result = new RecordReader(unknownType, new CSVReader(new StringReader(resultString)))) {
            Optional<Record> entry = result.read();
            assertFalse(entry.isPresent());
        }
    }

    @Test
    public void allEqual() throws Exception {
        RecordID identifier = RecordID.create();
        Table unknownType = new Table.Default("unknown");
        String resultString;
        try (
                RecordReader ancestor = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "A,B," + identifier + ",Z")));
                RecordReader left = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "A,B," + identifier + ",Z")));
                RecordReader right = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "A,B," + identifier + ",Z")));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out, ISO8601.EPOCH);
            resultString = result.toString();
        }
        try (RecordReader result = new RecordReader(unknownType, new CSVReader(new StringReader(resultString)))) {
            Optional<Record> entry = result.read();
            Record expected = Record.create(unknownType)
                    .setIdentifier(identifier)
                    .putField("a", "A")
                    .putField("b", "B")
                    .putField("z", "Z")
                    .build(ISO8601.EPOCH);
            assertEquals(Optional.of(expected), entry);
            assertEquals(Optional.empty(), result.read());
        }
    }

    @Test
    public void allEqualDistinct() throws Exception {
        RecordID identifier1 = RecordID.create();
        RecordID identifier2 = RecordID.create();
        RecordID identifier3 = RecordID.create();
        if (identifier2.compareTo(identifier3) > 0) {
            // Make identifier2 order less than identifier3 for stable test results
            RecordID tmp = identifier3;
            identifier3 = identifier2;
            identifier2 = tmp;
        }
        Table unknownType = new Table.Default("unknown");
        String resultString;
        try (
                RecordReader ancestor = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "A,B," + identifier1 + ",Z")));
                RecordReader left = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "A,B," + identifier2 + ",Z")));
                RecordReader right = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "A,B," + identifier3 + ",Z")));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out, ISO8601.EPOCH);
            resultString = result.toString();
        }
        try (RecordReader result = new RecordReader(unknownType, new CSVReader(new StringReader(resultString)))) {
            Optional<Record> entry = result.read();
            Record expected = Record.create(unknownType)
                    .setIdentifier(identifier2)
                    .putField("a", "A")
                    .putField("b", "B")
                    .putField("z", "Z")
                    .build(ISO8601.EPOCH);
            assertEquals(Optional.of(expected), entry);
            entry = result.read();
            expected = Record.create(unknownType)
                    .setIdentifier(identifier3)
                    .putField("a", "A")
                    .putField("b", "B")
                    .putField("z", "Z")
                    .build(ISO8601.EPOCH);
            assertEquals(Optional.of(expected), entry);
            assertEquals(Optional.empty(), result.read());
        }
    }

    @Test
    public void leftChanged() throws Exception {
        RecordID identifier = RecordID.create();
        Table unknownType = new Table.Default("unknown");
        String resultString;
        try (
                RecordReader ancestor = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "A,B," + identifier + ",Z")));
                RecordReader left = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "AAA,B," + identifier + ",Z")));
                RecordReader right = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "A,B," + identifier + ",Z")));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out, ISO8601.EPOCH);
            resultString = result.toString();
        }
        try (RecordReader result = new RecordReader(unknownType, new CSVReader(new StringReader(resultString)))) {
            Optional<Record> entry = result.read();
            Record expected = Record.create(unknownType)
                    .setIdentifier(identifier)
                    .putField("a", "AAA")
                    .putField("b", "B")
                    .putField("z", "Z")
                    .build(ISO8601.EPOCH);
            assertEquals(Optional.of(expected), entry);
            assertEquals(Optional.empty(), result.read());
        }
    }

    @Test
    public void rightChanged() throws Exception {
        RecordID identifier = RecordID.create();
        Table unknownType = new Table.Default("unknown");
        String resultString;
        try (
                RecordReader ancestor = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "A,B," + identifier + ",Z")));
                RecordReader left = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "A,B," + identifier + ",Z")));
                RecordReader right = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "A,BBB," + identifier + ",Z")));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out, ISO8601.EPOCH);
            resultString = result.toString();
        }
        try (RecordReader result = new RecordReader(unknownType, new CSVReader(new StringReader(resultString)))) {
            Optional<Record> entry = result.read();
            Record expected = Record.create(unknownType)
                    .setIdentifier(identifier)
                    .putField("a", "A")
                    .putField("b", "BBB")
                    .putField("z", "Z")
                    .build(ISO8601.EPOCH);
            assertEquals(Optional.of(expected), entry);
            assertEquals(Optional.empty(), result.read());
        }
    }

    @Test
    public void bothChangedDifferentCells() throws Exception {
        RecordID identifier = RecordID.create();
        Table unknownType = new Table.Default("unknown");
        String resultString;
        try (
                RecordReader ancestor = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "A,B," + identifier + ",Z")));
                RecordReader left = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "AAA,B," + identifier + ",Z")));
                RecordReader right = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,z" + System.lineSeparator()
                        + "A,BBB," + identifier + ",Z")));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out, ISO8601.EPOCH);
            resultString = result.toString();
        }
        try (RecordReader result = new RecordReader(unknownType, new CSVReader(new StringReader(resultString)))) {
            Optional<Record> entry = result.read();
            Record expected = Record.create(unknownType)
                    .setIdentifier(identifier)
                    .putField("a", "AAA")
                    .putField("b", "BBB")
                    .putField("z", "Z")
                    .build(ISO8601.EPOCH);
            assertEquals(Optional.of(expected), entry);
            assertEquals(Optional.empty(), result.read());
        }
    }

    @Test
    public void bothChangedSameCellLeft() throws Exception {
        RecordID identifier = RecordID.create();
        Table unknownType = new Table.Default("unknown");
        long now = System.currentTimeMillis();
        long ancestorTime = now - 3000;
        long leftTime = now - 1000;
        long rightTime = now - 2000;
        String resultString;
        try (
                RecordReader ancestor = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,lastChange,z" + System.lineSeparator()
                        + "A,B," + identifier + "," + ISO8601.of(ancestorTime) + ",Z")));
                RecordReader left = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,lastChange,z" + System.lineSeparator()
                        + "AAA,B," + identifier + "," + ISO8601.of(leftTime) + ",Z")));
                RecordReader right = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,lastChange,z" + System.lineSeparator()
                        + "BBB,B," + identifier + "," + ISO8601.of(rightTime) + ",Z")));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out, ISO8601.of(now));
            resultString = result.toString();
        }
        try (RecordReader result = new RecordReader(unknownType, new CSVReader(new StringReader(resultString)))) {
            Optional<Record> entry = result.read();
            Record expected = Record.create(unknownType)
                    .setIdentifier(identifier)
                    .putField("a", "AAA")
                    .putField("b", "B")
                    .putField("z", "Z")
                    .build(ISO8601.of(now));
            assertEquals(Optional.of(expected), entry);
            assertEquals(Optional.empty(), result.read());
        }
    }

    @Test
    public void bothChangedSameCellRight() throws Exception {
        RecordID identifier = RecordID.create();
        Table unknownType = new Table.Default("unknown");
        long now = System.currentTimeMillis();
        long ancestorTime = now - 3000;
        long leftTime = now - 2000;
        long rightTime = now - 1000;
        String resultString;
        try (
                RecordReader ancestor = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,lastChange,z" + System.lineSeparator()
                        + "A,B," + identifier + "," + ISO8601.of(ancestorTime) + ",Z")));
                RecordReader left = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,lastChange,z" + System.lineSeparator()
                        + "AAA,B," + identifier + "," + ISO8601.of(leftTime) + ",Z")));
                RecordReader right = new RecordReader(unknownType, new CSVReader(new StringReader(
                        "a,b,identifier,lastChange,z" + System.lineSeparator()
                        + "BBB,B," + identifier + "," + ISO8601.of(rightTime) + ",Z")));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out, ISO8601.of(now));
            resultString = result.toString();
        }
        try (RecordReader result = new RecordReader(unknownType, new CSVReader(new StringReader(resultString)))) {
            Optional<Record> entry = result.read();
            Record expected = Record.create(unknownType)
                    .setIdentifier(identifier)
                    .putField("a", "BBB")
                    .putField("b", "B")
                    .putField("z", "Z")
                    .build(ISO8601.of(now));
            assertEquals(Optional.of(expected), entry);
            assertEquals(Optional.empty(), result.read());
        }
    }
}
