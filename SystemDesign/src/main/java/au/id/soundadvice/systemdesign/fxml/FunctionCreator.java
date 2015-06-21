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
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.baselines.UndoBuffer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javafx.scene.control.ChoiceDialog;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FunctionCreator {

    public FunctionCreator(EditState edit) {
        this.edit = edit;
    }

    private final EditState edit;
    private final AtomicReference<UUID> mostRecentItem = new AtomicReference<>();

    void add(Function nearby) {
        Optional<FunctionalBaseline> functional = edit.getUndo().get().getFunctional();
        if (functional.isPresent() && functional.get().hasRelation(nearby)) {
            addToParent();
        } else {
            addToChild();
        }
    }

    void addToParent() {
        UndoBuffer<UndoState> undo = edit.getUndo();
        UndoState state = undo.get();
        Optional<FunctionalBaseline> functional = state.getFunctional();
        if (functional.isPresent()) {
            UUID item = functional.get().getSystemOfInterest().getUuid();
            Function function = createFunction(functional.get().getContext(), item);
            undo.set(state.setFunctional(functional.get().add(function)));
        }
    }

    void addToChild() {
        UndoBuffer<UndoState> undo = edit.getUndo();
        UndoState state = undo.get();
        AllocatedBaseline allocated = state.getAllocated();
        Optional<UUID> item = chooseItem(allocated);
        if (item.isPresent()) {
            Function function = createFunction(allocated, item.get());
            undo.set(state.setAllocated(allocated.add(function)));
        }
    }

    private Optional<UUID> chooseItem(AllocatedBaseline baseline) {
        List<Item> choices = baseline.getItems().parallel()
                .filter(item -> !item.isExternal())
                .sorted((left, right) -> left.toString().compareTo(right.toString()))
                .collect(Collectors.toList());
        if (!choices.isEmpty()) {
            Item selected = choices.get(0);
            if (choices.size() == 1) {
                return Optional.of(selected.getUuid());
            } else {
                Optional<Item> mostRecent = baseline.getStore().get(
                        mostRecentItem.get(), Item.class);
                if (mostRecent.isPresent()) {
                    selected = mostRecent.get();
                }

                ChoiceDialog<Item> dialog = new ChoiceDialog<>(selected, choices);
                dialog.setTitle("Choose Item");
                dialog.setHeaderText("Select an Item to implement the function");

                Optional<Item> result = dialog.showAndWait();
                if (result.isPresent()) {
                    UUID uuid = result.get().getUuid();
                    mostRecentItem.set(uuid);
                    return Optional.of(uuid);
                }
            }
        }
        return Optional.empty();
    }

    private Function createFunction(AllocatedBaseline baseline, UUID item) {
        String name = baseline.getFunctions().parallel()
                .map(Function::getName)
                .collect(new UniqueName("New Function"));
        return Function.createNew(item, name);
    }
}
