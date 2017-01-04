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
import au.com.bytecode.opencsv.CSVWriter;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.util.Pair;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Merge {

    private static Optional<String> min(
            Optional<String> ancestor,
            Optional<String> left,
            Optional<String> right) {
        Optional<String> result = ancestor;
        if (!result.isPresent() || (left.isPresent() && left.get().compareTo(result.get()) < 0)) {
            result = left;
        }
        if (!result.isPresent() || (right.isPresent() && right.get().compareTo(result.get()) < 0)) {
            result = right;
        }
        return result;
    }

    public static Supplier<Optional<Map<String, String>>> getMapReader(
            CSVReader reader, Optional<String[]> optHeader) {
        if (optHeader.isPresent()) {
            String[] header = optHeader.get();
            return () -> {
                try {
                    for (;;) {
                        String[] line = reader.readNext();
                        if (line == null) {
                            return Optional.empty();
                        }
                        if (line.length == 1 && line[0].isEmpty()) {
                            // Loop again on an empty line
                            continue;
                        }
                        Map<String, String> result = new HashMap<>();
                        for (int ii = 0; ii < header.length; ++ii) {
                            String cell = line.length > ii ? line[ii] : "";
                            result.put(header[ii], cell);
                        }
                        return Optional.of(result);
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            };
        } else {
            return () -> Optional.empty();
        }
    }

    public static Consumer<Optional<Record>> getMapWriter(
            CSVWriter writer, List<String> header) {
        return record -> {
            if (record.isPresent()) {
                List<String> result = new ArrayList<>(header.size());
                for (int ii = 0; ii < header.size(); ++ii) {
                    String cell = record.get().get(header.get(ii)).orElse("");
                    result.add(cell);
                }
                writer.writeNext(result.toArray(new String[0]));
            } else {
                // This is probably a deletion
            }
        };
    }

    public static void threeWayCSV(
            RecordReader ancestorReader, RecordReader leftReader, RecordReader rightReader,
            CSVWriter out) throws IOException {
        String now = ISO8601.now();
        Function<Triplet<Optional<Record>>, Optional<Record>> boundRecordMerger
                = triplet -> mergeRecord(triplet, now);

        try {
            List<String> allFields = Stream.concat(
                    ancestorReader.fields(),
                    Stream.concat(leftReader.fields(), rightReader.fields()))
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
            out.writeNext(allFields.toArray(new String[0]));
            Consumer<Optional<Record>> outWriter = getMapWriter(out, allFields);

            Optional<Record> ancestorNext;
            Optional<Record> leftNext;
            Optional<Record> rightNext;

            /*
             * Populate state variables. Files should be sorted in identifier
             * order so we should be able to do this in constant space
             */
            ancestorNext = ancestorReader.read();
            leftNext = leftReader.read();
            rightNext = rightReader.read();

            for (;;) {
                Optional<String> currentIdentifier = min(
                        ancestorNext.map(Record::getIdentifier),
                        leftNext.map(Record::getIdentifier),
                        rightNext.map(Record::getIdentifier));
                if (!currentIdentifier.isPresent()) {
                    // Done.
                    break;
                }
                Optional<Record> ancestorCurrent = ancestorNext.filter(
                        record -> record.getIdentifier().equals(currentIdentifier.get()));
                Optional<Record> leftCurrent = leftNext.filter(
                        record -> record.getIdentifier().equals(currentIdentifier.get()));
                Optional<Record> rightCurrent = rightNext.filter(
                        record -> record.getIdentifier().equals(currentIdentifier.get()));
                Optional<Record> result = merge(ancestorCurrent, leftCurrent, rightCurrent,
                        boundRecordMerger);

                outWriter.accept(result);

                if (ancestorCurrent.isPresent()) {
                    ancestorNext = ancestorReader.read();
                }
                if (leftCurrent.isPresent()) {
                    leftNext = leftReader.read();
                }
                if (rightCurrent.isPresent()) {
                    rightNext = rightReader.read();
                }
            }
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    public static <T> T merge(
            T ancestor, T left, T right,
            BinaryOperator<T> conflictResolver) {
        if (ancestor.equals(left)) {
            // Left hasn't changed, so return right
            return right;
        } else if (ancestor.equals(right)) {
            // Right hasn't changed, so return left
            return left;
        } else if (left.equals(right)) {
            // Both have changed, but they are the same so either will do
            return left;
        } else {
            // Both have changed, and they don't agree
            return conflictResolver.apply(left, right);
        }
    }

    public static final class Triplet<T> {

        public Triplet(T ancestor, T left, T right) {
            this.ancestor = ancestor;
            this.left = left;
            this.right = right;
        }

        private final T ancestor;
        private final T left;
        private final T right;
    }

    public static <T> T merge(
            T ancestor, T left, T right,
            Function<Triplet<T>, T> conflictResolver) {
        return merge(ancestor, left, right,
                (conflictLeft, conflictRight) -> {
                    return conflictResolver.apply(new Triplet<>(
                            ancestor, conflictLeft, conflictRight));
                });
    }

    public static Optional<Record> mergeRecord(
            Triplet<Optional<Record>> triplet, String now) {
        if (triplet.left.isPresent() && !triplet.right.isPresent()) {
            return triplet.left;
        }
        if (!triplet.left.isPresent() && triplet.right.isPresent()) {
            return triplet.right;
        }
        if (!triplet.left.isPresent() && !triplet.right.isPresent()) {
            return Optional.empty();
        } else {
            assert triplet.left.isPresent();
            assert triplet.right.isPresent();
            boolean leftIsNewer = Record.newerOf(triplet.left.get(), triplet.right.get())
                    .equals(triplet.left.get());
            BinaryOperator<Optional<String>> cellmerge;
            if (leftIsNewer) {
                cellmerge = (l, r) -> l;
            } else {
                cellmerge = (l, r) -> r;
            }
            Map<String, String> mergedFields = Stream.concat(
                    // Find all keys
                    triplet.left.get().getAllFields().keySet().stream(),
                    triplet.right.get().getAllFields().keySet().stream())
                    .distinct()
                    .flatMap(key -> {
                        // Perform a three-way merge on each cell
                        Optional<String> ancestor
                                = triplet.ancestor.flatMap(record -> record.get(key));
                        Optional<String> left
                                = triplet.left.get().get(key);
                        Optional<String> right
                                = triplet.right.get().get(key);

                        Optional<String> merged = merge(ancestor, left, right, cellmerge);
                        // Return the left result if we have a cell-level conflict
                        return merged.map(m -> Stream.of(new Pair<>(key, m))).orElse(Stream.empty());
                    })
                    .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
            return Optional.of(
                    triplet.left.get().asBuilder()
                    .putAll(mergedFields)
                    .build(now));
        }
    }
}
