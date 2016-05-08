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

import au.id.soundadvice.systemdesign.storage.files.Merge;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class MergeTest {

    @Test
    public void testEmpty() throws Exception {
        try (CSVReader ancestor = new CSVReader(new StringReader(""));
                CSVReader left = new CSVReader(new StringReader(""));
                CSVReader right = new CSVReader(new StringReader(""));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out);
            assertEquals("", result.toString());
        }
    }

    @Test
    public void testEmptyWithHeaders() throws Exception {
        try (CSVReader ancestor = new CSVReader(new StringReader("a,b,uuid,z"));
                CSVReader left = new CSVReader(new StringReader("a,b,uuid,z"));
                CSVReader right = new CSVReader(new StringReader("a,b,uuid,z"));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out);
            assertEquals("\"a\",\"b\",\"uuid\",\"z\"" + System.lineSeparator(), result.toString());
        }
    }

    @Test
    public void allEqual() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (
                CSVReader ancestor = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,B," + uuid + ",Z"));
                CSVReader left = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,B," + uuid + ",Z"));
                CSVReader right = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,B," + uuid + ",Z"));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out);
            assertEquals("\"a\",\"b\",\"uuid\",\"z\"" + System.lineSeparator()
                    + "\"A\",\"B\",\"" + uuid + "\",\"Z\"" + System.lineSeparator(),
                    result.toString());
        }
    }

    @Test
    public void allEqualDistinct() throws Exception {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        UUID uuid3 = UUID.randomUUID();
        if (uuid2.compareTo(uuid3) > 0) {
            // Make uuid2 order less than uuid3 for stable test results
            UUID tmp = uuid3;
            uuid3 = uuid2;
            uuid2 = tmp;
        }
        try (
                CSVReader ancestor = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,B," + uuid1 + ",Z"));
                CSVReader left = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,B," + uuid2 + ",Z"));
                CSVReader right = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,B," + uuid3 + ",Z"));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out);
            assertEquals("\"a\",\"b\",\"uuid\",\"z\"" + System.lineSeparator()
                    + "\"A\",\"B\",\"" + uuid2 + "\",\"Z\"" + System.lineSeparator()
                    + "\"A\",\"B\",\"" + uuid3 + "\",\"Z\"" + System.lineSeparator(),
                    result.toString());
        }
    }

    @Test
    public void leftChanged() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (
                CSVReader ancestor = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,B," + uuid + ",Z"));
                CSVReader left = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "AAA,B," + uuid + ",Z"));
                CSVReader right = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,B," + uuid + ",Z"));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out);
            assertEquals("\"a\",\"b\",\"uuid\",\"z\"" + System.lineSeparator()
                    + "\"AAA\",\"B\",\"" + uuid + "\",\"Z\"" + System.lineSeparator(),
                    result.toString());
        }
    }

    @Test
    public void rightChanged() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (
                CSVReader ancestor = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,B," + uuid + ",Z"));
                CSVReader left = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,B," + uuid + ",Z"));
                CSVReader right = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,BBB," + uuid + ",Z"));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out);
            assertEquals("\"a\",\"b\",\"uuid\",\"z\"" + System.lineSeparator()
                    + "\"A\",\"BBB\",\"" + uuid + "\",\"Z\"" + System.lineSeparator(),
                    result.toString());
        }
    }

    @Test
    public void bothChangedDifferentCells() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (
                CSVReader ancestor = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,B," + uuid + ",Z"));
                CSVReader left = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "AAA,B," + uuid + ",Z"));
                CSVReader right = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,BBB," + uuid + ",Z"));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out);
            assertEquals("\"a\",\"b\",\"uuid\",\"z\"" + System.lineSeparator()
                    + "\"AAA\",\"BBB\",\"" + uuid + "\",\"Z\"" + System.lineSeparator(),
                    result.toString());
        }
    }

    @Test
    public void bothChangedSameCell() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (
                CSVReader ancestor = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "A,B," + uuid + ",Z"));
                CSVReader left = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "AAA,B," + uuid + ",Z"));
                CSVReader right = new CSVReader(new StringReader(
                        "a,b,uuid,z" + System.lineSeparator()
                        + "BBB,B," + uuid + ",Z"));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out);
            assertEquals("\"a\",\"b\",\"uuid\",\"z\"" + System.lineSeparator()
                    + "\"AAA\",\"B\",\"" + uuid + "\",\"Z\"" + System.lineSeparator(),
                    result.toString());
        }
    }

    @Test
    public void functionsTest() throws Exception {
        String O = "\"description\",\"external\",\"item\",\"name\",\"trace\",\"uuid\"\n"
                + "\"Approve Changes (3 Control Board)\",\"false\",\"1a53ad8d-5a23-4ab9-87a2-e4ca344182b3\",\"Approve Changes\",\"\",\"a2ef8fb3-dae7-44b5-acf9-232190762242\"" + System.lineSeparator()
                + "\"Develop Systems (2 Engineering Team)\",\"false\",\"d674806d-db60-4c2d-afbf-725f7108a2d3\",\"Develop Systems\",\"\",\"31a28a71-41c2-4a99-a763-d51dfb7c3883\"" + System.lineSeparator()
                + "\"Report Inconsistency (1 System Modelling System)\",\"false\",\"30cb1e53-de6d-4f1b-99e0-d685ca866af3\",\"Report Inconsistency\",\"\",\"4902c502-bc8d-4a45-b912-bbc8c10c7598\"" + System.lineSeparator()
                + "\"Store Models (1 System Modelling System)\",\"false\",\"30cb1e53-de6d-4f1b-99e0-d685ca866af3\",\"Store Models\",\"\",\"7a9ae517-1122-4e21-9acd-4fee178a108a\"" + System.lineSeparator();
        String A = "\"description\",\"external\",\"item\",\"name\",\"trace\",\"uuid\"\n"
                + "\"Approve Changes (3 Control Board)\",\"false\",\"1a53ad8d-5a23-4ab9-87a2-e4ca344182b3\",\"Approve Changes\",\"\",\"a2ef8fb3-dae7-44b5-acf9-232190762242\"" + System.lineSeparator()
                + "\"Develop Systems (2 Engineering Team)\",\"false\",\"d674806d-db60-4c2d-afbf-725f7108a2d3\",\"Develop Systems\",\"\",\"31a28a71-41c2-4a99-a763-d51dfb7c3883\"" + System.lineSeparator()
                + "\"Report Inconsistencies (1 System Modelling System)\",\"false\",\"30cb1e53-de6d-4f1b-99e0-d685ca866af3\",\"Report Inconsistencies\",\"\",\"4902c502-bc8d-4a45-b912-bbc8c10c7598\"" + System.lineSeparator()
                + "\"Store Models (1 System Modelling System)\",\"false\",\"30cb1e53-de6d-4f1b-99e0-d685ca866af3\",\"Store Models\",\"\",\"7a9ae517-1122-4e21-9acd-4fee178a108a\"" + System.lineSeparator();
        String B = "\"description\",\"external\",\"item\",\"name\",\"trace\",\"uuid\"\n"
                + "\"Approve Changes (3 Control Board)\",\"false\",\"1a53ad8d-5a23-4ab9-87a2-e4ca344182b3\",\"Approve Changes\",\"\",\"a2ef8fb3-dae7-44b5-acf9-232190762242\"" + System.lineSeparator()
                + "\"Develop Systems (2 Engineering Team)\",\"false\",\"d674806d-db60-4c2d-afbf-725f7108a2d3\",\"Develop Systems\",\"\",\"31a28a71-41c2-4a99-a763-d51dfb7c3883\"" + System.lineSeparator()
                + "\"Report Inconsistency (1 System Modelling System)\",\"false\",\"30cb1e53-de6d-4f1b-99e0-d685ca866af3\",\"Report Inconsistency\",\"\",\"4902c502-bc8d-4a45-b912-bbc8c10c7598\"" + System.lineSeparator()
                + "\"Store Inconsistencies (1 System Modelling System)\",\"false\",\"30cb1e53-de6d-4f1b-99e0-d685ca866af3\",\"Store Inconsistencies\",\"\",\"7a9ae517-1122-4e21-9acd-4fee178a108a\"" + System.lineSeparator();
        String P = "\"description\",\"external\",\"item\",\"name\",\"trace\",\"uuid\"\n"
                + "\"Approve Changes (3 Control Board)\",\"false\",\"1a53ad8d-5a23-4ab9-87a2-e4ca344182b3\",\"Approve Changes\",\"\",\"a2ef8fb3-dae7-44b5-acf9-232190762242\"" + System.lineSeparator()
                + "\"Develop Systems (2 Engineering Team)\",\"false\",\"d674806d-db60-4c2d-afbf-725f7108a2d3\",\"Develop Systems\",\"\",\"31a28a71-41c2-4a99-a763-d51dfb7c3883\"" + System.lineSeparator()
                + "\"Report Inconsistencies (1 System Modelling System)\",\"false\",\"30cb1e53-de6d-4f1b-99e0-d685ca866af3\",\"Report Inconsistencies\",\"\",\"4902c502-bc8d-4a45-b912-bbc8c10c7598\"" + System.lineSeparator()
                + "\"Store Inconsistencies (1 System Modelling System)\",\"false\",\"30cb1e53-de6d-4f1b-99e0-d685ca866af3\",\"Store Inconsistencies\",\"\",\"7a9ae517-1122-4e21-9acd-4fee178a108a\"" + System.lineSeparator();
        try (
                CSVReader ancestor = new CSVReader(new StringReader(O));
                CSVReader left = new CSVReader(new StringReader(A));
                CSVReader right = new CSVReader(new StringReader(B));
                StringWriter result = new StringWriter();
                CSVWriter out = new CSVWriter(result)) {
            Merge.threeWayCSV(ancestor, left, right, out);
            assertEquals(P, result.toString());
        }
    }

}
