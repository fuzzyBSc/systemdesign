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
package au.id.soundadvice.systemdesign.logical.entity;

import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.moduleapi.entity.UniqueConstraint;

/**
 * A view of a function within a specific drawing. The separation of the main
 * Function class and the FunctionView class is intended to allow each function
 * to appear independently on multiple drawings while still maintaining some
 * properties between such drawings.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum FunctionView implements Table {
    functionView;

    public static Point2D DEFAULT_ORIGIN = new Point2D(200, 200);

    @Override
    public String getTableName() {
        return name();
    }

    /**
     * Return all views of functions. For functions allocated to subsystems we
     * should always see one view of that function, corresponding to its trace.
     * External functions may appear on multiple drawings and thus have multiple
     * views.
     *
     * @param baseline The baseline to search
     * @return
     */
    public static Stream<Record> find(Baseline baseline) {
        return baseline.findByType(FunctionView.functionView);
    }

    public static Stream<Record> findForFunction(Baseline baseline, Record function) {
        return baseline.findReverse(function.getIdentifier(), functionView);
    }

    public static Stream<Record> findForDrawing(Baseline baseline, Record drawing) {
        return baseline.findReverse(drawing.getIdentifier(), functionView);
    }

    public static Optional<Record> get(Baseline baseline, Record function, Record drawing) {
        return findForFunction(baseline, function)
                .filter(view -> FunctionView.functionView.getDrawing(baseline, view).is(drawing))
                .findAny();
    }

    @CheckReturnValue
    public static Pair<Baseline, Record> create(
            Baseline baseline, String now, Record function, Record drawing) {
        Optional<Record> existingView = get(baseline, function, drawing);
        if (existingView.isPresent()) {
            return new Pair<>(baseline, existingView.get());
        } else {
            Optional<Record> nearMatch = findForFunction(baseline, function).findAny();
            Record view;
            if (nearMatch.isPresent()) {
                view = nearMatch.get().asBuilder()
                        .setContainer(drawing)
                        .build(now);
            } else {
                view = Record.create(functionView)
                        .setViewOf(function)
                        .setContainer(drawing)
                        .setOrigin(DEFAULT_ORIGIN)
                        .build(now);
            }
            baseline = baseline.add(view);
            return new Pair<>(baseline, view);
        }
    }

    @CheckReturnValue
    public WhyHowPair<Baseline> createNeededViews(WhyHowPair<Baseline> baselines, String now) {
        Iterator<Record> drawings = baselines.getChild().findByType(this).iterator();
        WhyHowPair<Baseline> result = baselines;
        while (drawings.hasNext()) {
            Record drawing = drawings.next();
            Map<RecordID, Record> functionsWithExistingViews
                    = FunctionView.findForDrawing(baselines.getChild(), drawing)
                    .map(view -> FunctionView.functionView.getFunction(baselines.getChild(), view))
                    .collect(Collectors.toMap(Record::getIdentifier, o -> o));
            Stream<Record> functionsForDiagram;
            Optional<RecordID> trace = drawing.getTrace();
            if (trace.isPresent()) {
                functionsForDiagram = baselines.getChild()
                        .findByTrace(trace)
                        .flatMap(function -> {
                            // Find related
                            Stream<Record> related = Flow.findForFunction(baselines.getChild(), function)
                                    .map(Record::getConnectionScope)
                                    .map(scope -> scope.otherEnd(function.getIdentifier()))
                                    .flatMap(otherEndIdentifier -> baselines.getChild().get(otherEndIdentifier, Function.function)
                                            .map(Stream::of).orElse(Stream.empty()));
                            return Stream.concat(Stream.of(function), related);
                        })
                        .distinct();
            } else {
                // All child functions should be on the context diagram
                functionsForDiagram = Function.find(baselines.getChild());
            }
            Iterator<Record> functionsToAdd = functionsForDiagram
                    .filter(function -> !functionsWithExistingViews.containsKey(function.getIdentifier()))
                    .iterator();
            while (functionsToAdd.hasNext()) {
                Record functionToAdd = functionsToAdd.next();
                result = result.setChild(FunctionView.create(result.getChild(), now, functionToAdd, drawing)
                        .getKey());
            }
        }
        return result;
    }

    public Record getFunction(Baseline baseline, Record view) {
        return baseline.get(view.getViewOf().get(), Function.function).get();
    }

    public Record getDrawing(Baseline baseline, Record view) {
        return baseline.get(view.getContainer().get(), LogicalDrawingRecord.logicalDrawing).get();
    }

    @Override
    public Stream<UniqueConstraint> getUniqueConstraints() {
        return Stream.of(
                record -> new Object[]{record.getViewOf(), record.getContainer()});
    }

    @Override
    public Record merge(WhyHowPair<Baseline> baselines, String now, Record left, Record right) {
        return Record.newerOf(left, right);
    }

    @Override
    public Stream<Problem> getTraceProblems(WhyHowPair<Baseline> context, Record traceParent, Stream<Record> traceChildren) {
        // Views don't trace
        return Stream.empty();
    }

    @Override
    public Stream<Problem> getUntracedParentProblems(WhyHowPair<Baseline> context, Stream<Record> untracedParents) {
        // Views don't trace
        return Stream.empty();
    }

    @Override
    public Stream<Problem> getUntracedChildProblems(WhyHowPair<Baseline> context, Stream<Record> untracedChildren) {
        // Views don't trace
        return Stream.empty();
    }

    // Is this view part of the main drawing for the function's parent, or is it
    // a foreign view situated within another drawing?
    public boolean isForeign(Baseline baseline, Record view) {
        Record function = getFunction(baseline, view);
        Record drawing = getDrawing(baseline, view);
        return !function.getTrace().equals(drawing.getTrace());
    }

}
