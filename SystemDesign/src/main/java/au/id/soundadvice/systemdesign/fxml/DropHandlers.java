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

import au.id.soundadvice.systemdesign.baselines.AllocatedBaseline;
import au.id.soundadvice.systemdesign.baselines.EditState;
import au.id.soundadvice.systemdesign.baselines.FunctionalBaseline;
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.files.Identifiable;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget.Drop;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.FunctionView;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
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
                UndoState state, UUID sourceUUID, UUID targetUUID) {
            Optional<FunctionView> sourceView
                    = state.getAllocatedInstance(sourceUUID, FunctionView.class);
            if (sourceView.isPresent()) {
                sourceUUID = sourceView.get().getFunction().getUuid();
            }
            Optional<FunctionView> targetView
                    = state.getAllocatedInstance(targetUUID, FunctionView.class);
            if (targetView.isPresent()) {
                targetUUID = targetView.get().getFunction().getUuid();
            }
            return getActionsImpl(state, sourceUUID, targetUUID);
        }

        private Map<TransferMode, BooleanSupplier> getActionsImpl(
                UndoState state, UUID sourceUUID, UUID targetUUID) {
            Map<TransferMode, BooleanSupplier> result = new HashMap<>();
            Optional<Function> sourceChildFunction = state.getAllocatedInstance(
                    sourceUUID, Function.class);
            Optional<Function> targetChildFunction = state.getAllocatedInstance(
                    targetUUID, Function.class);
            Optional<Function> targetTraceFunction = state.getFunctionalInstance(
                    targetUUID, Function.class);
            if (targetTraceFunction.isPresent()) {
                // Only use if the function traces to the system of interest
                Optional<FunctionalBaseline> functional = state.getFunctional();
                assert functional.isPresent();
                if (!targetTraceFunction.get().getItem().getUuid().equals(
                        functional.get().getSystemOfInterest().getUuid())) {
                    targetTraceFunction = Optional.empty();
                }
            }

            AllocatedBaseline allocated = state.getAllocated();
            RelationStore store = allocated.getStore();
            if (sourceChildFunction.isPresent() && targetTraceFunction.isPresent()) {
                // Trace the source function to the parent fuction.
                // This appears as a move in the logical tree.
                result.put(TransferMode.MOVE, () -> {
                    Stream<UUID> toRemove
                            = store.getReverse(sourceUUID, Flow.class).parallel()
                            .map(Identifiable::getUuid);
                    edit.getUndo().set(state.setAllocated(
                            allocated.removeAll(toRemove).add(
                                    sourceChildFunction.get().setTrace(targetUUID))));
                    return true;
                });
            }
            if (sourceChildFunction.isPresent() && targetChildFunction.isPresent()) {
                // Trace the source function to the parent fuction.
                // This appears as a move in the logical tree.
                result.put(TransferMode.LINK, () -> {
                    interactions.addFlow(sourceUUID, targetUUID);
                    return true;
                });
            }

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
                UndoState state, UUID sourceUUID, UUID targetUUID) {
            Map<TransferMode, BooleanSupplier> result = new HashMap<>();
            Optional<Item> sourceItem = state.getAllocatedInstance(
                    sourceUUID, Item.class);
            Optional<Item> targetItem = state.getAllocatedInstance(
                    targetUUID, Item.class);

            if (sourceItem.isPresent() && targetItem.isPresent()) {
                // Trace the source function to the parent fuction.
                // This appears as a move in the logical tree.
                result.put(TransferMode.LINK, () -> {
                    interactions.addInterface(sourceUUID, targetUUID);
                    return true;
                });
            }

            return result;
        }
    }
}
