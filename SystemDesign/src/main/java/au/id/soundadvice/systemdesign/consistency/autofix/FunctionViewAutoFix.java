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
package au.id.soundadvice.systemdesign.consistency.autofix;

import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.FunctionView;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.util.Pair;

/**
 * Create and destroy FunctionViews to match rules.
 *
 * <ol>
 * <li>A view shall not exist if its associated drawing does not exist</li>
 * <li>A view shall exist on the drawing matching its function's trace</li>
 * <li>A view shall exist on each drawing for which it has a visible flow, ie a
 * view shall exist on each drawing for which any flow's other side is traced to
 * this drawing.</li>
 * </ol>
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FunctionViewAutoFix {

    static UndoState fix(UndoState state) {
        if (state.getSystemOfInterest().isPresent()) {
            final UndoState preTraceState = state;
            Iterator<FunctionView> it = FunctionView.find(preTraceState.getAllocated()).iterator();
            while (it.hasNext()) {
                FunctionView view = it.next();
                Optional<Function> drawing = view.getDrawing(preTraceState.getFunctional());
                if (!drawing.isPresent()) {
                    // Remove stale view
                    state = state.setAllocated(
                            view.removeFrom(state.getAllocated()));
                }
            }
        }

        final UndoState preDeduplicateState = state;
        Map<Function, Map<Optional<Function>, List<FunctionView>>> viewsByFunctionAndDrawing
                = FunctionView.find(preDeduplicateState.getAllocated())
                .collect(Collectors.groupingBy(
                        view -> view.getFunction(preDeduplicateState.getAllocated()),
                        Collectors.groupingBy(
                                view -> view.getDrawing(preDeduplicateState.getFunctional()))));
        // Only allow one view per function and drawing
        for (Map<Optional<Function>, List<FunctionView>> viewsByDrawing : viewsByFunctionAndDrawing.values()) {
            for (List<FunctionView> views : viewsByDrawing.values()) {
                // Remove views after the first one we found
                for (int ii = 1; ii < views.size(); ++ii) {
                    // Remove duplicate view
                    state = state.setAllocated(
                            views.get(ii).removeFrom(state.getAllocated()));
                }
            }
        }

        final UndoState preAddState = state;
        Stream<Pair<Function, Optional<Function>>> additions
                = Function.find(preAddState.getAllocated())
                .flatMap(function -> {
                    return function.getExpectedDrawings(preAddState)
                            .map(drawing -> {
                                return new Pair<>(function, drawing);
                            });
                });
        {
            Iterator<Pair<Function, Optional<Function>>> it = additions.iterator();
            Baseline allocated = state.getAllocated();
            while (it.hasNext()) {
                Pair<Function, Optional<Function>> view = it.next();

                // Create is a no-op for views that already exist
                allocated = FunctionView.create(allocated, view.getKey(), view.getValue(), FunctionView.DEFAULT_ORIGIN)
                        .getBaseline();
            }
            state = state.setAllocated(allocated);
        }

        return state;
    }

}
