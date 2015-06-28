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
import au.id.soundadvice.systemdesign.model.Item;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.util.Pair;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FunctionViewAutoFix {

    static UndoState fix(UndoState state) {
        final UndoState preRemoveState = state;
        Stream<FunctionView> removals = preRemoveState.getAllocated().getFunctions()
                .flatMap(function -> {
                    return function.getViews(preRemoveState.getAllocated())
                    .flatMap(view -> {
                        Baseline functional = preRemoveState.getFunctional();
                        if (function.isExternal()) {
                            Optional<Item> systemOfInterest = preRemoveState.getSystemOfInterest();
                            if (!systemOfInterest.isPresent()) {
                                // The function shouldn't even be there. Manual fix.
                                return Stream.empty();
                            }
                            Optional<Function> drawing = view.getDrawing(functional);
                            if (!drawing.isPresent()) {
                                /*
                                 * Unallocated function in child baseline.
                                 */
                                return Stream.of(view);
                            }
                            Function systemFunction = drawing.get();
                            boolean flowsExist = systemFunction.getFlows(functional)
                            .map(flow -> flow.otherEnd(functional, systemFunction).getUuid())
                            .anyMatch(externalFunctionUUID -> externalFunctionUUID.equals(function.getUuid()));
                            if (flowsExist) {
                                // Keep it
                                return Stream.empty();
                            } else {
                                // Toss it
                                return Stream.of(view);
                            }
                        } else {
                            /*
                             * Delete this view if its drawing does not match
                             * the function trace
                             */
                            Optional<Function> functionTrace = function.getTrace(functional);
                            Optional<Function> drawing = view.getDrawing(functional);
                            if (functionTrace.equals(drawing)) {
                                // No problem
                                return Stream.empty();
                            } else {
                                return Stream.of(view);
                            }
                        }
                    });
                });
        {
            Iterator<FunctionView> it = removals.iterator();
            Baseline allocated = state.getAllocated();
            while (it.hasNext()) {
                FunctionView view = it.next();
                allocated = view.removeFrom(allocated);
            }
            state = state.setAllocated(allocated);
        }

        final UndoState preAddState = state;
        Stream<Pair<Function, Optional<Function>>> additions = preAddState.getAllocated().getFunctions()
                .flatMap(function -> {
                    Baseline functional = preAddState.getFunctional();
                    Baseline allocated = preAddState.getAllocated();
                    Map<Optional<Function>, FunctionView> views
                    = function.getViews(allocated)
                    .collect(Collectors.toMap(
                                    functionView -> functionView.getDrawing(functional),
                                    java.util.function.Function.identity()));

                    if (function.isExternal()) {
                        Optional<Item> systemOfInterest = preAddState.getSystemOfInterest();
                        if (!systemOfInterest.isPresent()) {
                            // The function shouldn't even be there. Manual fix.
                            return Stream.empty();
                        }
                        // Get the flows to/from the external function
                        return function.getFlows(functional)
                        .map(flow -> flow.otherEnd(functional, function))
                        .filter(otherEndFunction -> {
                            /*
                             * Narrow down to functions that belong to the
                             * system of interest and for which no view exists
                             */
                            FunctionView viewForFunction = views.get(
                                    Optional.of(otherEndFunction));
                            return viewForFunction == null
                            && systemOfInterest.get().getUuid().equals(
                                    otherEndFunction.getItem().getUuid());
                        })
                        // The UUIDs of functions of the system of interest that have flows with this external function
                        .distinct()
                        .map(drawing -> new Pair<>(
                                        function, Optional.of(drawing)));
                    } else {
                        /*
                         * Create a view if none exists for the function's trace
                         */
                        Optional<Function> systemFunction = function.getTrace(functional);
                        FunctionView view = views.get(systemFunction);
                        if (view == null) {
                            // We need to add this view
                            return Stream.of(new Pair<>(function, systemFunction));
                        } else {
                            return Stream.empty();
                        }
                    }
                }
                );
        {
            Iterator<Pair<Function, Optional<Function>>> it = additions.iterator();
            Baseline allocated = state.getAllocated();
            while (it.hasNext()) {
                Pair<Function, Optional<Function>> view = it.next();

                allocated = FunctionView.create(
                        allocated, view.getKey(), view.getValue(), FunctionView.defaultOrigin)
                        .getBaseline();
            }
            state = state.setAllocated(allocated);
        }

        return state;
    }

}
