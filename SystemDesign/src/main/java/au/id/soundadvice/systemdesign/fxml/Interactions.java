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
import au.id.soundadvice.systemdesign.baselines.UndoBuffer;
import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.model.Baseline.BaselineAnd;
import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.Budget;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.FlowType;
import au.id.soundadvice.systemdesign.model.FunctionView;
import au.id.soundadvice.systemdesign.model.IDPath;
import au.id.soundadvice.systemdesign.model.Identity;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.ItemView;
import au.id.soundadvice.systemdesign.model.UndoState.StateAnd;
import au.id.soundadvice.systemdesign.relation.Reference;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.paint.Color;
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

    public Optional<String> textInput(String question, String _default) {
        TextInputDialog dialog = new TextInputDialog(_default);
        dialog.setTitle(question);
        dialog.setHeaderText(question);
        return dialog.showAndWait();
    }

    public void createBudget() {
        String name;
        String unit;
        {
            // User interaction - read only
            UndoState state = edit.getUndo().get();
            Baseline allocated = state.getAllocated();

            Optional<String> optionalName = textInput(
                    "Enter name for budget",
                    Budget.find(allocated).parallel()
                    .map(budget -> budget.getKey().getName())
                    .collect(new UniqueName("New Budget")));
            if (!optionalName.isPresent()) {
                return;
            }
            name = optionalName.get();
        }
        {
            // User interaction - read only
            Optional<String> optionalUnit = textInput(
                    "Enter unit for " + name, "units");
            if (!optionalUnit.isPresent()) {
                return;
            }
            unit = optionalUnit.get();
        }

        edit.updateAllocated(baseline -> {
            return Budget.add(baseline, new Budget.Key(name, unit)).getBaseline();
        });
    }

    void addFunctionToItem(Item item) {
        Optional<String> result;
        {
            // User interaction - read only
            UndoState state = edit.getUndo().get();
            Baseline allocated = state.getAllocated();
            if (item.isExternal() || !allocated.get(item).isPresent()) {
                return;
            }

            String name = Function.find(allocated).parallel()
                    .map(Function::getName)
                    .collect(new UniqueName("New Function"));

            TextInputDialog dialog = new TextInputDialog(name);
            dialog.setTitle("Enter name for function");
            dialog.setHeaderText("Enter name for function");

            result = dialog.showAndWait();
        }
        if (result.isPresent()) {
            edit.updateAllocated(baseline -> {
                return Function.create(
                        baseline, item, Optional.empty(), result.get(), FunctionView.defaultOrigin)
                        .getBaseline();
            });
        }
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
                boolean isUnique = Item.find(allocated).parallel()
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
                boolean isUnique = Item.find(allocated).parallel()
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
                boolean isUnique = Function.find(allocated).parallel()
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

    void setBudgetName(Budget.Key key) {
        Optional<String> result;
        {
            // User interaction - read only
            UndoBuffer<UndoState> undo = edit.getUndo();
            UndoState state = undo.get();
            Baseline allocated = state.getAllocated();
            Optional<Budget> budget = Budget.find(allocated, key).findAny();
            if (!budget.isPresent()) {
                return;
            }

            result = textInput("Rename " + key.getName(), key.getName());
        }
        if (result.isPresent()) {
            edit.update(state -> {
                Budget.Key newKey = key.setName(result.get());
                {
                    Baseline functional = state.getFunctional();
                    Iterator<Budget> it = Budget.find(functional, key).iterator();
                    while (it.hasNext()) {
                        Budget budget = it.next();
                        functional = budget.setKey(functional, newKey).getBaseline();
                    }
                    state = state.setFunctional(functional);
                }
                {
                    Baseline allocated = state.getAllocated();
                    Iterator<Budget> it = Budget.find(allocated, key).iterator();
                    while (it.hasNext()) {
                        Budget budget = it.next();
                        allocated = budget.setKey(allocated, newKey).getBaseline();
                    }
                    state = state.setAllocated(allocated);
                }
                return state;
            });
        }
    }

    void setBudgetUnit(Budget.Key key) {
        Optional<String> result;
        {
            // User interaction - read only
            UndoBuffer<UndoState> undo = edit.getUndo();
            UndoState state = undo.get();
            Baseline allocated = state.getAllocated();
            Optional<Budget> budget = Budget.find(allocated, key).findAny();
            if (!budget.isPresent()) {
                return;
            }

            result = textInput("Set " + key.getName() + " unit", key.getUnit());
        }
        if (result.isPresent()) {
            edit.update(state -> {
                Budget.Key newKey = key.setUnit(result.get());
                {
                    Baseline functional = state.getFunctional();
                    Iterator<Budget> it = Budget.find(functional, key).iterator();
                    while (it.hasNext()) {
                        Budget budget = it.next();
                        functional = budget.setKey(functional, newKey).getBaseline();
                    }
                    state = state.setFunctional(functional);
                }
                {
                    Baseline allocated = state.getAllocated();
                    Iterator<Budget> it = Budget.find(allocated, key).iterator();
                    while (it.hasNext()) {
                        Budget budget = it.next();
                        allocated = budget.setKey(allocated, newKey).getBaseline();
                    }
                    state = state.setAllocated(allocated);
                }
                return state;
            });
        }
    }

    public void color(Item item) {
        Optional<Color> result;
        {
            // User interaction - read only
            UndoState state = edit.getUndo().get();
            Baseline allocated = state.getAllocated();
            if (item.isExternal() || !allocated.get(item).isPresent()) {
                return;
            }

            ItemView view = item.getView(allocated);
            Dialog<Color> dialog = new Dialog<>();
            dialog.setTitle("Select color for item");
            dialog.setHeaderText("Select color for " + item.getDisplayName());
            ColorPicker picker = new ColorPicker(view.getColor());
            dialog.getDialogPane().setContent(picker);

            dialog.getDialogPane().getButtonTypes().addAll(
                    ButtonType.OK, ButtonType.CANCEL);

            dialog.setResultConverter(buttonType -> {
                if (buttonType.equals(ButtonType.OK)) {
                    return picker.getValue();
                } else {
                    return null;
                }
            });
            result = dialog.showAndWait();
        }
        if (result.isPresent()) {
            Color color = result.get();
            edit.updateAllocated(allocated -> {
                Optional<Item> current = allocated.get(item);
                Optional<ItemView> view = current.map(tmpItem -> tmpItem.getView(allocated));
                if (view.isPresent()) {
                    return view.get().setColor(allocated, color).getBaseline();
                } else {
                    return allocated;
                }
            });
        }
    }

    void addInterface(Item left, Item right) {
        if (!left.isExternal() || !right.isExternal()) {
            // One end has to be internal
            edit.updateAllocated(allocated -> {
                return Interface.create(allocated, left, right).getBaseline();
            });
        }
    }

    private StateAnd<FlowType> getTypeForNewFlow(
            UndoState state, Function left, Function right) {
        final Baseline functional = state.getFunctional();
        final Baseline allocated = state.getAllocated();
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
                Set<UUID> alreadyUsed = internal.getFlows(allocated).parallel()
                        .filter(flow -> {
                            return flow.otherEnd(allocated, internal).equals(external)
                            && flow.getDirectionFrom(external).contains(directionFromExternal);
                        })
                        .map(Flow::getType)
                        .map(Reference::getUuid)
                        .collect(Collectors.toSet());

                Optional<FlowType> functionalType = external.getFlows(functional)
                        .filter(flow -> {
                            return flow.hasEnd(systemFunction.get())
                            && flow.getDirectionFrom(external).contains(directionFromExternal);
                        })
                        .map(flow -> flow.getType().getTarget(functional.getContext()))
                        .filter(candidate -> !alreadyUsed.contains(candidate.getUuid()))
                        .findAny();
                if (functionalType.isPresent()) {
                    // Flow down to the allocated baseline if needed
                    Optional<FlowType> allocatedType = FlowType.find(
                            allocated, functionalType.get().getName());
                    if (allocatedType.isPresent()) {
                        return state.and(allocatedType.get());
                    } else {
                        BaselineAnd<FlowType> addedType
                                = functionalType.get().addTo(allocated);
                        return state.setAllocated(addedType.getBaseline())
                                .and(addedType.getRelation());
                    }
                }
            }
        }
        String name = FlowType.find(allocated).parallel()
                .map(FlowType::getName)
                .collect(new UniqueName("New Flow"));
        BaselineAnd<FlowType> result = FlowType.add(allocated, name);
        return state.setAllocated(result.getBaseline()).and(result.getRelation());
    }

    public void addFlow(Function source, Function target) {
        edit.update(state -> {
            Baseline allocated = state.getAllocated();
            Optional<Function> left = allocated.get(source);
            Optional<Function> right = allocated.get(target);
            if (left.isPresent() && right.isPresent()
                    // One end has to be internal
                    && (!left.get().isExternal() || !right.get().isExternal())) {
                StateAnd<FlowType> result = getTypeForNewFlow(state, left.get(), right.get());
                state = result.getState();
                allocated = state.getAllocated();
                FlowType flowType = result.getRelation();
                allocated = Flow.add(
                        allocated,
                        left.get(), right.get(),
                        flowType, Direction.Normal).getBaseline();
                return state.setAllocated(allocated);
            } else {
                return state;
            }
        });
    }

    void setFlowType(Flow flow) {
        Optional<String> interactionResult;
        {
            // User interaction, read-only
            UndoState state = edit.getUndo().get();
            Baseline allocated = state.getAllocated();
            Optional<Flow> current = allocated.get(flow);
            if (!current.isPresent()) {
                return;
            }
            FlowType type = current.get().getType().getTarget(allocated.getContext());

            TextInputDialog dialog = new TextInputDialog(type.getName());
            dialog.setTitle("Enter Flow Type");
            dialog.setHeaderText("Enter Flow Type");

            interactionResult = dialog.showAndWait();
        }
        if (interactionResult.isPresent()) {
            String typeName = interactionResult.get();
            edit.update(state -> {
                Baseline allocated = state.getAllocated();
                Optional<Flow> current = allocated.get(flow);
                FlowType currentType = current.get().getType().getTarget(allocated.getContext());
                if (current.isPresent() && !currentType.getName().equals(typeName)) {
                    Function left = current.get().getLeft().getTarget(allocated.getContext());
                    Function right = current.get().getRight().getTarget(allocated.getContext());
                    allocated = Flow.remove(allocated, left, right, currentType, flow.getDirection());
                    Optional<FlowType> newType = FlowType.find(allocated, typeName);
                    if (!newType.isPresent()) {
                        // See if we can flow the type down
                        Baseline functional = state.getFunctional();
                        newType = FlowType.find(functional, typeName);
                        if (newType.isPresent()) {
                            // Flow type down
                            BaselineAnd<FlowType> result = newType.get().addTo(allocated);
                            allocated = result.getBaseline();
                            newType = Optional.of(result.getRelation());
                        }
                        if (!newType.isPresent()) {
                            // No such type in parent: Add new type
                            BaselineAnd<FlowType> result = FlowType.add(allocated, typeName);
                            allocated = result.getBaseline();
                            newType = Optional.of(result.getRelation());
                        }
                    }
                    allocated = Flow.add(allocated, left, right, newType.get(), flow.getDirection()).getBaseline();
                    return state.setAllocated(allocated);
                } else {
                    return state;
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
