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
package au.id.soundadvice.systemdesign.fxml;

import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget.Drop;
import au.id.soundadvice.systemdesign.logical.Function;
import au.id.soundadvice.systemdesign.logical.FunctionView;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.physical.ItemView;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import javafx.scene.input.TransferMode;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class DropHandlers {

    public static class FunctionDropHandler implements Drop {

        public FunctionDropHandler(Interactions interactions, EditState edit) {
            this.interactions = interactions;
            this.edit = edit;
        }
        private final Interactions interactions;
        private final EditState edit;

        @Override
        public Map<TransferMode, BooleanSupplier> getActions(
                UndoState state, String sourceIdentifier, String targetIdentifier) {
            Relations allocated = state.getAllocated();
            Optional<FunctionView> sourceView
                    = allocated.get(sourceIdentifier, FunctionView.class);
            if (sourceView.isPresent()) {
                sourceIdentifier = sourceView.get().getFunction().getKey();
            }
            Optional<FunctionView> targetView
                    = allocated.get(targetIdentifier, FunctionView.class);
            if (targetView.isPresent()) {
                targetIdentifier = targetView.get().getFunction().getKey();
            }
            return getActionsImpl(state, sourceIdentifier, targetIdentifier);
        }

        private Map<TransferMode, BooleanSupplier> getActionsImpl(
                UndoState state, String sourceIdentifier, String targetIdentifier) {
            Relations functional = state.getFunctional();
            Relations allocated = state.getAllocated();
            Map<TransferMode, BooleanSupplier> result = new HashMap<>();
            Optional<Function> sourceChildFunction = allocated.get(
                    sourceIdentifier, Function.class);
            Optional<Function> targetChildFunction = allocated.get(
                    targetIdentifier, Function.class);
            Optional<Function> targetTraceFunction = functional.get(
                    targetIdentifier, Function.class)
                    .flatMap(candidate -> {
                        // Only use if the candidate is owned by the system of interest
                        Optional<Item> systemOfInterest = Identity.getSystemOfInterest(state);
                        if (systemOfInterest.isPresent()
                                && !candidate.getItem().getKey().equals(
                                        systemOfInterest.get().getIdentifier())) {
                            return Optional.empty();
                        } else {
                            return Optional.of(candidate);
                        }
                    });

            if (sourceChildFunction.isPresent() && targetTraceFunction.isPresent()) {
                // Trace the source function to the parent fuction.
                // This appears as a move in the logical tree.
                result.put(TransferMode.MOVE, () -> {
                    edit.updateAllocated(actionAllocated -> {
                        final Relations beforeMoveBaseline = actionAllocated;
                        Optional<Function> toMove = sourceChildFunction.flatMap(
                                function -> beforeMoveBaseline.get(function));
                        if (!toMove.isPresent()) {
                            return actionAllocated;
                        }
                        actionAllocated = toMove.get().setTrace(
                                actionAllocated, targetTraceFunction.get())
                                .getKey();
                        return actionAllocated;
                    });
                    return true;
                });
            }
            if (sourceChildFunction.isPresent() && targetChildFunction.isPresent()) {
                // Trace the source function to the parent fuction.
                // This appears as a move in the logical tree.
                result.put(TransferMode.LINK, () -> {
                    interactions.addFlow(
                            sourceChildFunction.get(), targetChildFunction.get());
                    return true;
                });
            }

            return result;
        }
    }

    public static class LogicalSchematicBackgroundDropHandler implements Drop {

        public LogicalSchematicBackgroundDropHandler(EditState edit) {
            this.edit = edit;
        }
        private final EditState edit;

        @Override
        public Map<TransferMode, BooleanSupplier> getActions(
                UndoState state, String sourceIdentifier, String targetIdentifier) {
            Relations functional = state.getFunctional();
            Optional<Function> drawing = functional.get(targetIdentifier, Function.class);
            if (drawing.isPresent()) {
                Relations allocated = state.getAllocated();
                Optional<FunctionView> sourceView
                        = allocated.get(sourceIdentifier, FunctionView.class);
                sourceIdentifier = sourceView.map(view -> view.getFunction().getKey())
                        .orElse(sourceIdentifier);
                Optional<Function> sourceFunction = allocated.get(sourceIdentifier, Function.class);
                return sourceFunction.map(function -> getActionsImpl(function, drawing.get()))
                        .orElse(Collections.emptyMap());
            } else {
                return Collections.emptyMap();
            }
        }

        private Map<TransferMode, BooleanSupplier> getActionsImpl(
                Function dragSource, Function drawing) {
            Map<TransferMode, BooleanSupplier> result = new HashMap<>();
            // Trace the source function to the parent fuction.
            // This appears as a move in the logical tree.
            result.put(TransferMode.MOVE, () -> {
                edit.updateState(state -> {
                    Relations functional = state.getFunctional();
                    Relations allocated = state.getAllocated();
                    Optional<Function> traceFunction
                            = functional.get(drawing);
                    Optional<Function> sourceFunction
                            = allocated.get(dragSource);
                    if (traceFunction.isPresent() && sourceFunction.isPresent()) {
                        allocated = sourceFunction.get().setTrace(
                                allocated, traceFunction.get()).getKey();
                    }
                    return state.setAllocated(allocated);
                });
                return true;
            });
            // Make a FunctionView on the target drawing.
            result.put(TransferMode.COPY, () -> {
                edit.updateState(state -> {
                    Relations functional = state.getFunctional();
                    Relations allocated = state.getAllocated();
                    Optional<Function> traceFunction
                            = functional.get(drawing);
                    Optional<Function> sourceFunction
                            = allocated.get(dragSource);
                    if (traceFunction.isPresent() && sourceFunction.isPresent()) {
                        allocated = FunctionView.create(
                                allocated,
                                sourceFunction.get(),
                                traceFunction,
                                FunctionView.DEFAULT_ORIGIN).getKey();
                    }
                    return state.setAllocated(allocated);
                });
                return true;
            });

            return result;
        }
    }

    public static class ItemDropHandler implements Drop {

        public ItemDropHandler(Interactions interactions) {
            this.interactions = interactions;
        }
        private final Interactions interactions;

        @Override
        public Map<TransferMode, BooleanSupplier> getActions(
                UndoState state, String sourceIdentifier, String targetIdentifier) {
            Relations allocated = state.getAllocated();
            Optional<ItemView> sourceView
                    = allocated.get(sourceIdentifier, ItemView.class);
            if (sourceView.isPresent()) {
                sourceIdentifier = sourceView.get().getItem().getKey();
            }
            Optional<ItemView> targetView
                    = allocated.get(targetIdentifier, ItemView.class);
            if (targetView.isPresent()) {
                targetIdentifier = targetView.get().getItem().getKey();
            }
            return getActionsImpl(state, sourceIdentifier, targetIdentifier);
        }

        public Map<TransferMode, BooleanSupplier> getActionsImpl(
                UndoState state, String sourceIdentifier, String targetIdentifier) {
            Relations allocated = state.getAllocated();
            Map<TransferMode, BooleanSupplier> result = new HashMap<>();
            Optional<Item> sourceItem = allocated.get(
                    sourceIdentifier, Item.class);
            Optional<Item> targetItem = allocated.get(
                    targetIdentifier, Item.class);

            if (sourceItem.isPresent() && targetItem.isPresent()) {
                // Trace the source function to the parent fuction.
                // This appears as a move in the logical tree.
                result.put(TransferMode.LINK, () -> {
                    interactions.addInterface(sourceItem.get(), targetItem.get());
                    return true;
                });
            }

            return result;
        }
    }
}
