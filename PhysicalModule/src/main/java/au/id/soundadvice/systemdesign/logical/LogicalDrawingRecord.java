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
package au.id.soundadvice.systemdesign.logical;

import au.id.soundadvice.systemdesign.moduleapi.collection.BaselinePair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.physical.Identity;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.moduleapi.entity.UniqueConstraint;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum LogicalDrawingRecord implements Table {
    logicalDrawing;

    static Record createForContext(String now) {
        return Record.create(logicalDrawing)
                .setLongName("Logical View")
                .build(now);
    }

    static Record create(String now, Record traceFunction) {
        return Record.create(logicalDrawing)
                .setTrace(traceFunction)
                .setLongName(traceFunction.getLongName())
                .build(now);
    }

    @Override
    public String getTableName() {
        return name();
    }

    @Override
    public Stream<UniqueConstraint> getUniqueConstraints() {
        return Stream.of(Record::getTrace);
    }

    @Override
    public Record merge(BaselinePair baselines, String now, Record left, Record right) {
        return Record.newerOf(left, right);
    }

    @Override
    public Stream<Problem> getTraceProblems(BaselinePair context, Record traceParent, Stream<Record> traceChildren) {
        return traceChildren
                .filter(child -> !child.getLongName().equals(traceParent.getLongName()))
                .map(drawing -> {
                    return Problem.onLoadAutofixProblem(
                            (baselines, now) -> fixDrawingName(baselines, now, drawing));
                });
    }

    private BaselinePair fixDrawingName(BaselinePair baselines, String now, Record record) {
        Optional<Record> drawing = baselines.getChild().get(record);
        Optional<Record> trace = drawing.flatMap(dd -> getTrace(baselines, dd));
        if (drawing.isPresent() && trace.isPresent()) {
            Record updated = drawing.get().asBuilder()
                    .setLongName(trace.get().getLongName())
                    .build(now);
            return baselines.setChild(
                    baselines.getChild().add(updated));
        }
        return baselines;
    }

    @Override
    public Stream<Problem> getUntracedParentProblems(BaselinePair context, Stream<Record> untracedParents) {
        // These drawings aren't traced to
        return Stream.empty();
    }

    @Override
    public Stream<Problem> getUntracedChildProblems(BaselinePair context, Stream<Record> untracedChildren) {
        // We should be traced to a parent baseline function if the parent baseline exists
        Optional<Record> systemOfInterest = Identity.getSystemOfInterest(context);
        if (systemOfInterest.isPresent()) {
            return untracedChildren.map(drawing -> {
                return Problem.onLoadAutofixProblem(
                        (baselines, now) -> baselines.setChild(
                                baselines.getChild().remove(drawing.getIdentifier())));
            });
        }
        return Stream.empty();
    }

    public Optional<Record> getTrace(BaselinePair baselines, Record drawing) {
        return drawing.getTrace().flatMap(trace -> baselines.getParent().get(trace, Function.function));
    }

    public BaselinePair createNeededDrawings(BaselinePair baselines, String now) {
        // Make sure a drawing exists for each parent baseline function
        Optional<Record> systemOfInterest = Identity.getSystemOfInterest(baselines);
        Stream<Optional<Record>> expectedDrawingTraces;
        if (systemOfInterest.isPresent()) {
            expectedDrawingTraces = baselines.getParent().findByType(Function.function)
                    .map(Optional::of);
        } else {
            // Expect a single untraced drawing
            expectedDrawingTraces = Stream.of(Optional.empty());
        }
        Iterator<Optional<Record>> it = expectedDrawingTraces
                .filter(trace -> {
                    return !baselines.getChild().findByTrace(trace.map(Record::getIdentifier))
                            .findAny().isPresent();
                }).iterator();
        BaselinePair result = baselines;
        while (it.hasNext()) {
            Optional<Record> traceFunction = it.next();
            Record newDrawing;
            if (traceFunction.isPresent()) {
                newDrawing = create(now, traceFunction.get());
            } else {
                newDrawing = createForContext(now);
            }
            result = result.setChild(result.getChild().add(newDrawing));
        }
        return result;
    }
}
