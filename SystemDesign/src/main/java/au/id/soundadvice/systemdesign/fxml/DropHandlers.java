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
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.files.Identifiable;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget.Drop;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import javafx.scene.input.TransferMode;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class DropHandlers {

    public static class FunctionDropHandler implements Drop {

        public FunctionDropHandler(EditState edit) {
            this.edit = edit;
        }
        private final EditState edit;

        @Override
        public Map<TransferMode, BooleanSupplier> getActions(
                UndoState state, UUID sourceUUID, UUID targetUUID) {
            Map<TransferMode, BooleanSupplier> result = new HashMap<>();
            Function sourceChildFunction = state.getAllocatedInstance(
                    sourceUUID, Function.class);
            Function targetChildFunction = state.getAllocatedInstance(
                    targetUUID, Function.class);
            Function targetParentFunction = state.getFunctionalInstance(
                    targetUUID, Function.class);

            AllocatedBaseline allocated = state.getAllocated();
            RelationStore store = allocated.getStore();
            if (sourceChildFunction != null && targetParentFunction != null) {
                // Trace the source function to the parent fuction.
                // This appears as a move in the logical tree.
                result.put(TransferMode.MOVE, () -> {
                    List<UUID> toRemove
                            = store.getReverse(sourceUUID, Flow.class).parallelStream()
                            .map(Identifiable::getUuid)
                            .collect(Collectors.toList());
                    edit.getUndo().set(state.setAllocated(
                            allocated.removeAll(toRemove).add(
                                    sourceChildFunction.setTrace(targetUUID))));
                    return true;
                });
            }
            if (sourceChildFunction != null && targetChildFunction != null) {
                // Trace the source function to the parent fuction.
                // This appears as a move in the logical tree.
                result.put(TransferMode.LINK, () -> {
                    String flowType = allocated.getFlows().parallelStream()
                            .map(Flow::getType)
                            .collect(new UniqueName("New Flow"));
                    Flow flow = Flow.createNew(sourceUUID, targetUUID, flowType);
                    edit.getUndo().set(state.setAllocated(allocated.add(flow)));
                    return true;
                });
            }

            return result;
        }
    }

    public static class ItemDropHandler implements Drop {

        public ItemDropHandler(EditState edit) {
            this.edit = edit;
        }
        private final EditState edit;

        @Override
        public Map<TransferMode, BooleanSupplier> getActions(
                UndoState state, UUID sourceUUID, UUID targetUUID) {
            Map<TransferMode, BooleanSupplier> result = new HashMap<>();
            Item sourceItem = state.getAllocatedInstance(
                    sourceUUID, Item.class);
            Item targetItem = state.getAllocatedInstance(
                    targetUUID, Item.class);

            AllocatedBaseline allocated = state.getAllocated();
            if (sourceItem != null && targetItem != null) {
                // Trace the source function to the parent fuction.
                // This appears as a move in the logical tree.
                result.put(TransferMode.LINK, () -> {
                    Interface newInterface = new Interface(sourceUUID, targetUUID);
                    if (allocated.getStore().getReverse(sourceUUID, Interface.class).parallelStream()
                            .noneMatch((existing) -> newInterface.isRedundantTo(existing))) {
                        edit.getUndo().set(state.setAllocated(allocated.add(newInterface)));
                    }
                    return true;
                });
            }

            return result;
        }
    }
}
