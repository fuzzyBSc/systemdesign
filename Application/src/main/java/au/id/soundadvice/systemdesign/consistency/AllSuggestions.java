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
package au.id.soundadvice.systemdesign.consistency;

import au.id.soundadvice.systemdesign.consistency.suggest.DirectoryNameMismatch;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class AllSuggestions {

    public static Stream<EditProblem> getEditProblems(EditState edit) {
        Stream<EditProblem> result = Stream.<Function<EditState, Stream<EditProblem>>>of(
                DirectoryNameMismatch::getProblems)
                .parallel()
                .flatMap(f -> f.apply(edit));
        return Stream.concat(result, getUndoProblems(edit.getState()).map(EditProblem::of));
    }

    public static Stream<Problem> getUndoProblems(WhyHowPair<Baseline> baselines) {
        return getAllTraceProblems(baselines);
    }

    private static Stream<Problem> getAllTraceProblems(WhyHowPair<Baseline> baselines) {
        // Resolve all of the traces
        Map<Optional<Record>, List<Record>> childTraceMap = baselines.getChild().stream()
                .collect(Collectors.groupingBy(
                        childRecord -> childRecord.getTrace().<Record>flatMap(trace -> baselines.getParent().getAnyType(trace))
                ));
        return Stream.concat(
                getUntracedChildProblems(baselines, childTraceMap),
                Stream.concat(
                        getUntracedParentProblems(baselines, childTraceMap),
                        getTraceProblems(baselines, childTraceMap)));
    }

    private static Stream<Problem> getUntracedChildProblems(
            WhyHowPair<Baseline> baselines, Map<Optional<Record>, List<Record>> childTraceMap) {
        List<Record> untracedChildren = childTraceMap.getOrDefault(
                Optional.empty(), Collections.emptyList());
        Map<Table, List<Record>> byType = untracedChildren.stream()
                .collect(Collectors.groupingBy(Record::getType));
        return byType.entrySet().stream()
                .flatMap(entry -> {
                    return entry.getKey().getUntracedChildProblems(baselines, entry.getValue().stream());
                });
    }

    private static Stream<Problem> getUntracedParentProblems(
            WhyHowPair<Baseline> baselines, Map<Optional<Record>, List<Record>> childTraceMap) {
        Map<Table, List<Record>> byType = baselines.getParent().stream()
                .filter(parentRecord -> !childTraceMap.containsKey(Optional.of(parentRecord)))
                .collect(Collectors.groupingBy(Record::getType));
        return byType.entrySet().stream()
                .flatMap(entry -> {
                    return entry.getKey().getUntracedParentProblems(baselines, entry.getValue().stream());
                });
    }

    private static Stream<Problem> getTraceProblems(
            WhyHowPair<Baseline> baselines, Map<Optional<Record>, List<Record>> childTraceMap) {
        return childTraceMap.entrySet().stream()
                .filter(entry -> entry.getKey().isPresent())
                .flatMap(entry -> {
                    Record traceParent = entry.getKey().get();
                    Stream<Record> traceChildren = entry.getValue().stream();
                    return traceParent.getType().getTraceProblems(baselines, traceParent, traceChildren);
                });
    }
}
