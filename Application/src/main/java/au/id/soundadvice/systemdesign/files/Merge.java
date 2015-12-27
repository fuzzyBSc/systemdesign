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
import au.com.bytecode.opencsv.CSVWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    private static void validateHeader(Optional<String[]> header) throws IOException {
        if (header.isPresent()
                && Stream.of(header.get()).noneMatch(cell -> "uuid".equals(cell))) {
            throw new IOException("Expected uuid header in " + Arrays.toString(header.get()));
        }
    }

    private static Optional<UUID> getUUID(Optional<Map<String, String>> line) throws IOException {
        try {
            return line.map(map -> UUID.fromString(map.get("uuid")));
        } catch (IllegalArgumentException ex) {
            throw new IOException(ex);
        }
    }

    private static Optional<UUID> min(
            Optional<UUID> ancestor,
            Optional<UUID> left,
            Optional<UUID> right) {
        Optional<UUID> result = ancestor;
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

    public static Consumer<Optional<Map<String, String>>> getMapWriter(
            CSVWriter writer, String[] header) {
        return line -> {
            if (line.isPresent()) {
                List<String> result = new ArrayList<>(header.length);
                for (int ii = 0; ii < header.length; ++ii) {
                    String cell = line.get().getOrDefault(header[ii], "");
                    result.add(cell);
                }
                writer.writeNext(result.toArray(new String[0]));
            } else {
                // This is probably a deletion
            }
        };
    }

    public static void threeWayCSV(
            CSVReader ancestor, CSVReader left, CSVReader right,
            CSVWriter out) throws IOException {
        try {
            Optional<String[]> ancestorHeader = Optional.ofNullable(ancestor.readNext());
            Optional<String[]> leftHeader = Optional.ofNullable(left.readNext());
            Optional<String[]> rightHeader = Optional.ofNullable(right.readNext());
            Optional<String[]> mergedHeader = merge(
                    ancestorHeader, leftHeader, rightHeader, Merge::mergeHeaders);
            // Validate that these are files we can merge
            validateHeader(ancestorHeader);
            validateHeader(leftHeader);
            validateHeader(rightHeader);
            if (mergedHeader.isPresent()) {
                out.writeNext(mergedHeader.get());
            } else {
                // The merged content is empty
                return;
            }
            Supplier<Optional<Map<String, String>>> ancestorReader
                    = getMapReader(ancestor, ancestorHeader);
            Supplier<Optional<Map<String, String>>> leftReader
                    = getMapReader(left, leftHeader);
            Supplier<Optional<Map<String, String>>> rightReader
                    = getMapReader(right, rightHeader);
            Consumer<Optional<Map<String, String>>> outWriter
                    = getMapWriter(out, mergedHeader.get());

            Optional<Map<String, String>> ancestorNext;
            Optional<Map<String, String>> leftNext;
            Optional<Map<String, String>> rightNext;
            Optional<UUID> ancestorNextUUID;
            Optional<UUID> leftNextUUID;
            Optional<UUID> rightNextUUID;

            /*
             * Populate state variables. Files should be sorted in UUID order so
             * we should be able to do this in constant space
             */
            ancestorNext = ancestorReader.get();
            ancestorNextUUID = getUUID(ancestorNext);
            leftNext = leftReader.get();
            leftNextUUID = getUUID(leftNext);
            rightNext = rightReader.get();
            rightNextUUID = getUUID(rightNext);

            for (;;) {
                Optional<UUID> currentUUID = min(
                        ancestorNextUUID, leftNextUUID, rightNextUUID);
                if (!currentUUID.isPresent()) {
                    // Done.
                    break;
                }
                Optional<Map<String, String>> ancestorCurrent
                        = currentUUID.equals(ancestorNextUUID) ? ancestorNext : Optional.empty();
                Optional<Map<String, String>> leftCurrent
                        = currentUUID.equals(leftNextUUID) ? leftNext : Optional.empty();
                Optional<Map<String, String>> rightCurrent
                        = currentUUID.equals(rightNextUUID) ? rightNext : Optional.empty();
                Optional<Map<String, String>> result = merge(ancestorCurrent, leftCurrent, rightCurrent,
                        Merge::mergeLine);

                outWriter.accept(result);

                if (ancestorCurrent.isPresent()) {
                    ancestorNext = ancestorReader.get();
                    ancestorNextUUID = getUUID(ancestorNext);
                }
                if (leftCurrent.isPresent()) {
                    leftNext = leftReader.get();
                    leftNextUUID = getUUID(leftNext);
                }
                if (rightCurrent.isPresent()) {
                    rightNext = rightReader.get();
                    rightNextUUID = getUUID(rightNext);
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

    public static Optional<String[]> mergeHeaders(
            Optional<String[]> left, Optional<String[]> right) {
        List<String> allHeaders = Stream.concat(
                left.map(e -> Stream.of(e)).orElse(Stream.empty()),
                right.map(e -> Stream.of(e)).orElse(Stream.empty()))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        if (allHeaders.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(allHeaders.toArray(new String[0]));
        }
    }

    public static Optional<Map<String, String>> mergeLine(
            Triplet<Optional<Map<String, String>>> triplet) {
        if (triplet.left.isPresent() && !triplet.right.isPresent()) {
            return triplet.left;
        }
        if (!triplet.left.isPresent() && triplet.right.isPresent()) {
            return triplet.right;
        }
        if (!triplet.left.isPresent() && !triplet.right.isPresent()) {
            return Optional.empty();
        } else {
            return Optional.of(Stream.concat(
                    // Find all keys
                    triplet.left.map(e -> e.keySet().stream()).orElse(Stream.empty()),
                    triplet.right.map(e -> e.keySet().stream()).orElse(Stream.empty()))
                    .distinct()
                    .flatMap(key -> {
                        // Perform a three-way merge on each cell
                        Optional<String> ancestor
                                = triplet.ancestor.flatMap(map -> Optional.ofNullable(map.get(key)));
                        Optional<String> left
                                = triplet.left.flatMap(map -> Optional.ofNullable(map.get(key)));
                        Optional<String> right
                                = triplet.right.flatMap(map -> Optional.ofNullable(map.get(key)));
                        Optional<String> merged = merge(ancestor, left, right, (l, r) -> l);
                        // Return the left result if we have a cell-level conflict
                        return merged.map(m -> Stream.of(new Pair<>(key, m))).orElse(Stream.empty());
                    })
                    .collect(Collectors.toMap(Pair::getKey, Pair::getValue)));
        }
    }
}
