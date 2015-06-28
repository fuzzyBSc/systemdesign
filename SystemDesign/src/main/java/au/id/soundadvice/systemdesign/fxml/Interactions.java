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

import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.baselines.EditState;
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.IDPath;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.baselines.UndoBuffer;
import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Identity;
import au.id.soundadvice.systemdesign.model.Interface;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Interactions {

    private static final Logger LOG = Logger.getLogger(Interactions.class.getName());

    public Interactions(Window window, EditState edit) {
        this.window = window;
        this.edit = edit;
    }
    private final Window window;
    private final EditState edit;

    public Item createItem(Point2D origin) {
        AtomicReference<Item> result = new AtomicReference<>();
        edit.updateAllocated(baseline -> {
            return Item.create(baseline, origin).getBaseline();
        });
        return result.get();
    }

    void addFunctionToItem(Item item) {
        edit.updateAllocated(baseline -> {
            return Function.create(baseline, item).getBaseline();
        });
    }

    void renumber(Item item) {
        Optional<String> result;
        {
            // User interaction - read only
            UndoBuffer<UndoState> undo = edit.getUndo();
            if (item.isExternal() || !undo.get().getAllocated().get(item).isPresent()) {
                return;
            }

            TextInputDialog dialog = new TextInputDialog(
                    item.getShortId().toString());
            dialog.setTitle("Enter name for item");
            dialog.setHeaderText("Enter name for " + item.getDisplayName());

            result = dialog.showAndWait();
        }
        if (result.isPresent()) {
            IDPath path = IDPath.valueOfSegment(result.get());
            edit.updateAllocated(allocated -> {
                boolean isUnique = allocated.getItems().parallel()
                        .map(Item::getShortId)
                        .noneMatch((existing) -> path.equals(existing));
                if (isUnique) {
                    return item.setShortId(allocated, path).getBaseline();
                } else {
                    return allocated;
                }
            });
        }
    }

    public void rename(Item item) {
        Optional<String> result;
        {
            // User interaction - read only
            UndoBuffer<UndoState> undo = edit.getUndo();
            if (item.isExternal() || !undo.get().getAllocated().get(item).isPresent()) {
                return;
            }

            TextInputDialog dialog = new TextInputDialog(item.getName());
            dialog.setTitle("Enter name for item");
            dialog.setHeaderText("Enter name for " + item.getDisplayName());

            result = dialog.showAndWait();
        }
        if (result.isPresent()) {
            String name = result.get();
            edit.updateAllocated(allocated -> {
                boolean isUnique = allocated.getItems().parallel()
                        .map(Item::getName)
                        .noneMatch((existing) -> name.equals(existing));
                if (isUnique) {
                    return item.setName(allocated, name).getBaseline();
                } else {
                    return allocated;
                }
            });
        }
    }

    public void rename(Function function) {
        Optional<String> result;
        {
            // User interaction - read only
            UndoBuffer<UndoState> undo = edit.getUndo();
            UndoState state = undo.get();
            Baseline allocated = state.getAllocated();
            if (function.isExternal() || !allocated.get(function).isPresent()) {
                return;
            }

            TextInputDialog dialog = new TextInputDialog(function.getName());
            dialog.setTitle("Enter number for function");
            dialog.setHeaderText("Enter number for "
                    + function.getDisplayName(allocated));

            result = dialog.showAndWait();
        }
        if (result.isPresent()) {
            String name = result.get();
            edit.updateAllocated(allocated -> {
                boolean isUnique = allocated.getFunctions().parallel()
                        .map(Function::getName)
                        .noneMatch((existing) -> name.equals(existing));
                if (isUnique) {
                    return function.setName(allocated, name).getBaseline();
                } else {
                    return allocated;
                }
            });
        }
    }

    void addInterface(Item left, Item right) {
        edit.updateAllocated(allocated -> {
            return Interface.create(allocated, left, right).getBaseline();
        });
    }

    private String getTypeForNewFlow(UndoState state, Function left, Function right) {
        Baseline functional = state.getFunctional();
        Baseline allocated = state.getAllocated();
        Optional<Item> systemOfInterest = state.getSystemOfInterest();
        if (systemOfInterest.isPresent()) {
            // See if we can pick out a likely type
            Function internal;
            Optional<Function> systemFunction;
            Function external;
            Direction directionFromExternal;
            if (left.isExternal()) {
                internal = right;
                external = left;
                systemFunction = internal.getTrace(functional);
                directionFromExternal = Direction.Normal;
            } else if (right.isExternal()) {
                internal = left;
                external = right;
                systemFunction = internal.getTrace(functional);
                directionFromExternal = Direction.Reverse;
            } else {
                internal = null;
                external = null;
                systemFunction = Optional.empty();
                directionFromExternal = Direction.None;
            }
            if (internal != null && systemFunction.isPresent() && external != null) {
                Set<String> alreadyUsed = internal.getFlows(allocated).parallel()
                        .filter(flow -> flow.otherEnd(allocated, internal).equals(external))
                        .map(Flow::getType)
                        .collect(Collectors.toSet());

                Map<Boolean, List<String>> types = external.getFlows(functional)
                        .filter(flow -> {
                            return flow.hasEnd(systemFunction.get())
                            && flow.getDirectionFrom(external).contains(directionFromExternal);
                        })
                        .map(Flow::getType)
                        .collect(Collectors.groupingBy(type -> alreadyUsed.contains(type)));
                // Prefer types not already used
                for (Boolean key : new Boolean[]{Boolean.FALSE, Boolean.TRUE}) {
                    List<String> list = types.get(key);
                    if (list != null && !list.isEmpty()) {
                        return list.get(0);
                    }
                }
            }
        }
        return allocated.getFlows().parallel()
                .map(Flow::getType)
                .collect(new UniqueName("New Flow"));
    }

    public void addFlow(Function source, Function target) {
        edit.update(state -> {
            Baseline allocated = state.getAllocated();
            Optional<Function> left = allocated.get(source);
            Optional<Function> right = allocated.get(target);
            if (left.isPresent() && right.isPresent()) {
                String flowType = getTypeForNewFlow(state, left.get(), right.get());
                allocated = Flow.add(allocated, left.get(), right.get(), flowType, Direction.Normal).getBaseline();
                return state.setAllocated(allocated);
            } else {
                return state;
            }
        });
    }

    void setFlowType(Flow flow) {
        Optional<String> result;
        {
            // User interaction, read-only
            UndoBuffer<UndoState> undo = edit.getUndo();
            if (!undo.get().getAllocated().get(flow).isPresent()) {
                return;
            }

            TextInputDialog dialog = new TextInputDialog(flow.getType());
            dialog.setTitle("Enter Flow Type");
            dialog.setHeaderText("Enter Flow Type");

            result = dialog.showAndWait();
        }
        if (result.isPresent()) {
            edit.updateAllocated(allocated -> {
                Optional<Flow> current = allocated.get(flow);
                if (current.isPresent() && !current.get().getType().equals(result.get())) {
                    Function left = current.get().getLeft().getTarget(allocated.getContext());
                    Function right = current.get().getRight().getTarget(allocated.getContext());
                    allocated = Flow.remove(allocated, left, right, flow.getType(), flow.getDirection());
                    allocated = Flow.add(allocated, left, right, result.get(), flow.getDirection()).getBaseline();
                    return allocated;
                } else {
                    return allocated;
                }
            });
        }
    }

    public void navigateUp() {
        try {
            if (checkSave("Save before navigating?")) {
                edit.loadParent();
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    public void navigateDown(Item item) {
        UndoState state = edit.getUndo().get();
        this.navigateDown(item.asIdentity(state.getAllocated()));
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
            if (edit.getCurrentDirectory().isPresent()) {
                edit.save();
            } else {
                return trySaveAs();
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
