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
import au.id.soundadvice.systemdesign.model.IDPath;
import au.id.soundadvice.systemdesign.model.IDSegment;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.baselines.UndoBuffer;
import au.id.soundadvice.systemdesign.model.Identity;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Interactions {

    public Interactions(EditState edit) {
        this.edit = edit;
    }
    private final EditState edit;

    public Item addItem() {
        UndoBuffer<UndoState> undo = edit.getUndo();
        UndoState state = undo.get();
        FunctionalBaseline functional = state.getFunctional();
        AllocatedBaseline allocated = state.getAllocated();
        Collection<Item> items = allocated.getItems();
        String name = items.parallelStream()
                .filter(item -> !item.isExternal())
                .map(Item::getName)
                .collect(new UniqueName("New Item"));
        Item item = Item.newItem(
                allocated.getIdentity().getUuid(),
                allocated.getNextItemId(),
                name, "", false);
        undo.set(state.setAllocated(allocated.add(item)));
        return item;
    }

    void addFunctionToItem(Item item) {
        UndoBuffer<UndoState> undo = edit.getUndo();
        UndoState state = undo.get();
        AllocatedBaseline allocated = state.getAllocated();
        String name = allocated.getStore()
                .getReverse(item.getUuid(), Function.class).parallelStream()
                .map(Function::getName)
                .collect(new UniqueName("New Function"));
        Function function = Function.create(item.getUuid(), name);
        undo.set(state.setAllocated(allocated.add(function)));
    }

    void renumber(Item item) {
        UndoBuffer<UndoState> undo = edit.getUndo();
        if (item.isExternal() || !undo.get().getAllocated().hasRelation(item)) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(
                item.getShortId().toString());
        dialog.setTitle("Enter new number for item");
        dialog.setHeaderText("Enter new number for " + item.getDisplayName());

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            IDPath path = IDPath.valueOf(
                    Collections.singletonList(new IDSegment(result.get())));
            UndoState state = undo.get();
            AllocatedBaseline allocated = state.getAllocated();
            boolean isUnique = allocated.getItems().parallelStream()
                    .map(Item::getShortId)
                    .noneMatch((existing) -> path.equals(existing));
            if (isUnique) {
                item = item.setShortId(path);
                undo.set(state.setAllocated(allocated.add(item)));
            }
        }
    }

    public void navigateUp(Window window) {
        try {
            if (SaveHelper.checkSave(window, edit, "Save before navigating?")) {
                edit.loadParent();
            }
        } catch (IOException ex) {
            Logger.getLogger(MainController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void navigateDown(Window window, Item item) {
        UndoState state = edit.getUndo().get();
        this.navigateDown(window, item.asIdentity(state.getAllocated().getStore()));
    }

    public void navigateDown(Window window, Identity identity) {
        if (SaveHelper.checkSave(window, edit, "Save before navigating?")) {
            try {
                edit.loadChild(identity);
            } catch (IOException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Load Failed");
                alert.setHeaderText("Load Failed");
                alert.setContentText(ex.toString());

                alert.showAndWait();
            }
        }
    }
}
