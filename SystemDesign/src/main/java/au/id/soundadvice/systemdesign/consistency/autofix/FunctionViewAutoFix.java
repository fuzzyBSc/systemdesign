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

import au.id.soundadvice.systemdesign.baselines.AllocatedBaseline;
import au.id.soundadvice.systemdesign.baselines.FunctionalBaseline;
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.FunctionView;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FunctionViewAutoFix {

    static UndoState fix(UndoState state) {
        final UndoState preRemoveState = state;
        Stream<UUID> removals = preRemoveState.getAllocated().getFunctions()
                .flatMap(function -> {
                    return preRemoveState.getAllocated().getStore().getReverse(function.getUuid(), FunctionView.class)
                    .flatMap(view -> {
                        if (function.isExternal()) {
                            Optional<FunctionalBaseline> functional = preRemoveState.getFunctional();
                            if (!functional.isPresent()) {
                                // The function shouldn't even be there. Manual fix.
                                return Stream.empty();
                            }
                            Optional<UUID> drawing = view.getDrawing();
                            if (!drawing.isPresent()) {
                                /*
                                 * Unallocated function in child baseline.
                                 */
                                return Stream.of(view.getUuid());
                            }
                            RelationStore parentStore = functional.get().getStore();
                            UUID systemFunctionUUID = drawing.get();
                            boolean flowsExist = parentStore.getReverse(systemFunctionUUID, Flow.class)
                            .map(flow -> flow.getScope().otherEnd(systemFunctionUUID))
                            .anyMatch(externalFunctionUUID -> externalFunctionUUID.equals(function.getUuid()));
                            if (flowsExist) {
                                // Keep it
                                return Stream.empty();
                            } else {
                                // Toss it
                                return Stream.of(view.getUuid());
                            }
                        } else {
                            /*
                             * Delete this view if its drawing does not match
                             * the function trace
                             */
                            Optional<UUID> functionTrace = function.getTrace();
                            Optional<UUID> drawing = view.getDrawing();
                            if (functionTrace.equals(drawing)) {
                                // No problem
                                return Stream.empty();
                            } else {
                                return Stream.of(view.getUuid());
                            }
                        }
                    });
                });
        state = preRemoveState.setAllocated(preRemoveState.getAllocated().removeAll(removals));

        final UndoState preAddState = state;
        Stream<FunctionView> additions = preAddState.getAllocated().getFunctions()
                .flatMap(function -> {
                    Map<Optional<UUID>, FunctionView> views
                    = preAddState.getAllocated().getStore().getReverse(
                            function.getUuid(), FunctionView.class)
                    .collect(Collectors.toMap(
                                    FunctionView::getDrawing,
                                    java.util.function.Function.identity()));

                    if (function.isExternal()) {
                        Optional<FunctionalBaseline> functional = preAddState.getFunctional();
                        if (!functional.isPresent()) {
                            // The function shouldn't even be there. Manual fix.
                            return Stream.empty();
                        }
                        RelationStore parentStore = functional.get().getStore();
                        // Get the flows to/from the external function
                        return parentStore.getReverse(function.getUuid(), Flow.class)
                        .map(flow -> {
                            // Identify the other end of each flow
                            UUID otherEndUUID = flow.getScope().otherEnd(function.getUuid());
                            return parentStore.get(otherEndUUID, Function.class).get();
                        })
                        .filter(otherEndFunction -> {
                            /*
                             * Narrow down to functions that belong to the
                             * system of interest and for which no view exists
                             */
                            FunctionView viewForFunction = views.get(
                                    Optional.of(otherEndFunction.getUuid()));
                            return viewForFunction == null
                            && functional.get().getSystemOfInterest().getUuid().equals(
                                    otherEndFunction.getItem().getUuid());
                        })
                        // The UUIDs of functions of the system of interest that have flows with this external function
                        .map(Function::getUuid)
                        .distinct()
                        .map(drawingUUID -> FunctionView.createNew(
                                        function.getUuid(), Optional.of(drawingUUID), FunctionView.defaultOrigin));
                    } else {
                        /*
                         * Delete this view if its drawing does not match the
                         * function trace
                         */
                        Optional<UUID> functionTrace = function.getTrace();
                        FunctionView view = views.get(functionTrace);
                        if (view == null) {
                            // We need to add this view
                            return Stream.of(FunctionView.createNew(
                                            function.getUuid(), functionTrace,
                                            FunctionView.defaultOrigin));
                        } else {
                            return Stream.empty();
                        }
                    }
                }
                );
        AllocatedBaseline allocated = state.getAllocated();
        Iterator<FunctionView> it = additions.iterator();

        while (it.hasNext()) {
            allocated = allocated.add(it.next());
        }

        return state.setAllocated(allocated);
    }

}
