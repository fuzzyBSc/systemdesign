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
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.IDPath;
import au.id.soundadvice.systemdesign.model.IDSegment;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.baselines.UndoBuffer;
import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Identity;
import au.id.soundadvice.systemdesign.model.ConnectionScope;
import au.id.soundadvice.systemdesign.model.FlowEnd;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Interactions {

    public Interactions(Window window, EditState edit) {
        this.window = window;
        this.edit = edit;
    }
    private final Window window;
    private final EditState edit;

    public Item addItem() {
        UndoBuffer<UndoState> undo = edit.getUndo();
        UndoState state = undo.get();
        AllocatedBaseline allocated = state.getAllocated();
        String name = allocated.getItems().parallel()
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
                .getReverse(item.getUuid(), Function.class).parallel()
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
            boolean isUnique = allocated.getItems().parallel()
                    .map(Item::getShortId)
                    .noneMatch((existing) -> path.equals(existing));
            if (isUnique) {
                item = item.setShortId(path);
                undo.set(state.setAllocated(allocated.add(item)));
            }
        }
    }

    void addInterface(UUID left, UUID right) {
        UndoBuffer<UndoState> undo = edit.getUndo();
        undo.set(addInterfaceImpl(undo.get(), left, right).getValue());
    }

    @CheckReturnValue
    private Pair<UUID, UndoState> addInterfaceImpl(UndoState state, UUID left, UUID right) {
        AllocatedBaseline allocated = state.getAllocated();
        ConnectionScope connectionScope = new ConnectionScope(
                left, right, Direction.Both);
        Optional<? extends Interface> existing = allocated.getStore().getReverse(left, Interface.class).parallel()
                .filter((candidate) -> connectionScope.equals(candidate.getConnectionScope()))
                .findAny();
        if (existing.isPresent()) {
            return new Pair<>(existing.get().getUuid(), state);
        } else {
            Interface newInterface = Interface.createNew(connectionScope);
            return new Pair<>(
                    newInterface.getUuid(),
                    state.setAllocated(allocated.add(newInterface)));
        }
    }

    void addFlow(UUID source, UUID target) {
        UndoBuffer<UndoState> undo = edit.getUndo();
        UndoState state = undo.get();
        ConnectionScope connectionScope = new ConnectionScope(
                source, target, Direction.Normal);

        ConnectionScope interfaceScope
                = flowScopeToInterfaceScope(state, connectionScope);
        if (interfaceScope != null) {
            Pair<UUID, UndoState> pair = addInterfaceImpl(
                    state, interfaceScope.getLeft(), interfaceScope.getRight());
            state = pair.getValue();
            UUID interfaceUUID = pair.getKey();

            AllocatedBaseline allocated = state.getAllocated();
            String flowType = allocated.getFlows().parallel()
                    .map(Flow::getType)
                    .collect(new UniqueName("New Flow"));
            Flow flow = Flow.createNew(interfaceUUID, connectionScope, flowType);
            edit.getUndo().set(state.setAllocated(allocated.add(flow)));
        }
    }

    @Nullable
    private ConnectionScope flowScopeToInterfaceScope(
            UndoState state, ConnectionScope connectionScope) {
        FlowEnd left = state.getAllocatedInstance(connectionScope.getLeft(), FlowEnd.class);
        FlowEnd right = state.getAllocatedInstance(connectionScope.getRight(), FlowEnd.class);
        if (left == null || right == null) {
            return null;
        } else {
            return new ConnectionScope(
                    left.getItemUuid(), right.getItemUuid(),
                    Direction.Both);
        }
    }

    void setFlowType(Flow flow) {
        UndoBuffer<UndoState> undo = edit.getUndo();
        if (!undo.get().getAllocated().hasRelation(flow)) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(flow.getType());
        dialog.setTitle("Enter Flow Type");
        dialog.setHeaderText("Enter Flow Type");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            UndoState state = undo.get();
            AllocatedBaseline allocated = state.getAllocated();
            RelationStore store = allocated.getStore();
            ConnectionScope pair = flow.getConnectionScope();
            UUID left = flow.getLeft().getUuid();
            UUID right = flow.getRight().getUuid();
            String type = result.get();
            Optional<Flow> existing = store.getByClass(Flow.class).parallel()
                    .filter((candidate) -> {
                        return left.equals(candidate.getLeft().getUuid())
                        && right.equals(candidate.getRight().getUuid())
                        && type.equals(candidate.getType());
                    }).findAny();
            if (existing.isPresent()) {
                allocated = allocated.remove(flow.getUuid());
                /*
                 * Preserve the flow's direction, but otherwise merge into
                 * existing flow of the correct type.
                 */
                Direction direction = flow.getDirection();
                flow = existing.get();
                flow = flow.setDirection(flow.getDirection().add(direction));
            } else {
                flow = flow.setType(type);
            }
            undo.set(state.setAllocated(allocated.add(flow)));
        }
    }

    public void navigateUp() {
        try {
            if (checkSave("Save before navigating?")) {
                edit.loadParent();
            }
        } catch (IOException ex) {
            Logger.getLogger(MainController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void navigateDown(Item item) {
        UndoState state = edit.getUndo().get();
        this.navigateDown(item.asIdentity(state.getAllocated().getStore()));
    }

    public void navigateDown(Identity identity) {
        if (checkSave("Save before navigating?")) {
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

    public void tryExit() {
        if (checkSave("Save before closing?")) {
            System.exit(0);
        }
    }

    public boolean checkSave(String description) {
        if (edit.saveNeeded()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(description);
            alert.setHeaderText(description);

            alert.getButtonTypes().setAll(
                    ButtonType.CANCEL, ButtonType.NO, ButtonType.YES);

            Optional<ButtonType> result = alert.showAndWait();
            if (ButtonType.YES.equals(result.get())) {
                return trySave();
            } else {
                // If no, success. If cancel, failed.
                return ButtonType.NO.equals(result.get());
            }
        } else {
            // Save not needed
            return true;
        }
    }

    public boolean trySave() {
        try {
            if (edit.getCurrentDirectory() == null) {
                return trySaveAs();
            } else {
                edit.save();
            }
            return true;
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Save Failed");
            alert.setHeaderText("Save Failed");
            alert.setContentText(ex.toString());

            alert.showAndWait();
            return false;
        }
    }

    public boolean trySaveAs() throws IOException {
        try {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("System Designs");
            File selectedDirectory = chooser.showDialog(window);
            if (selectedDirectory == null) {
                return false;
            } else {
                edit.saveTo(new Directory(Paths.get(selectedDirectory.getPath())));
                return true;
            }
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Save Failed");
            alert.setHeaderText("Save Failed");
            alert.setContentText(ex.toString());

            alert.showAndWait();
            return false;
        }
    }

    public boolean tryLoad(EditState edit, Directory dir) {
        try {
            edit.load(dir);
            return true;
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Load Failed");
            alert.setHeaderText("Load Failed");
            alert.setContentText(ex.toString());

            alert.showAndWait();
            return false;
        }
    }

    public boolean tryLoadChooser(Window window, EditState edit) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("System Designs");
        File selectedDirectory = chooser.showDialog(window);
        if (selectedDirectory == null) {
            return false;
        } else {
            Directory dir = new Directory(Paths.get(selectedDirectory.getPath()));
            return tryLoad(edit, dir);
        }
    }

}
