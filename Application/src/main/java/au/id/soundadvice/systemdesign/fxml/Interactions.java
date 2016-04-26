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

import au.id.soundadvice.systemdesign.budget.Budget;
import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.moduleapi.Direction;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.logical.Function;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.logical.Flow;
import au.id.soundadvice.systemdesign.logical.FlowType;
import au.id.soundadvice.systemdesign.physical.IDPath;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.physical.Interface;
import au.id.soundadvice.systemdesign.physical.ItemView;
import au.id.soundadvice.systemdesign.moduleapi.RelationPair;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.preferences.RecentFiles;
import au.id.soundadvice.systemdesign.versioning.VersionInfo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.EmptyStackException;
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
import javafx.util.Pair;

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

    public Optional<Item> createItem(Point2D origin) {
        AtomicReference<Item> result = new AtomicReference<>();
        String defaultName = Item.find(edit.getAllocated()).parallel()
                .filter(item -> !item.isExternal())
                .map(Item::getName)
                .collect(new UniqueName("New Item"));
        Optional<String> name = textInput("New Item", "Enter name for item", defaultName);
        if (name.isPresent()) {
            edit.updateState(state -> {
                Relations allocated = state.getAllocated();
                Color color = Identity.getSystemOfInterest(state)
                        .map(item -> item.getView(state.getFunctional()).getColor())
                        .orElse(Color.LIGHTYELLOW);
                // Shift color
                // Adjust hue by +/- 128 out of the 256 range
                double hueShift = Math.random() * 128 - 64;
                // Adjust saturation by +/- 30%
                double saturationMultiplier = Math.random() * .6 + .7;
                // Adjust brightness by +/- 20%
                double brightnessMultiplier = Math.random() * .4 + .8;
                double opacityMultiplier = 1;
                color = color.deriveColor(
                        hueShift,
                        saturationMultiplier,
                        brightnessMultiplier,
                        opacityMultiplier);

                Pair<Relations, Item> createResult = Item.create(
                        allocated, name.get(), origin, color);
                allocated = createResult.getKey();
                result.set(createResult.getValue());
                return state.setAllocated(allocated);
            });
        }
        return Optional.ofNullable(result.get());
    }

    public Optional<String> textInput(String action, String question, String _default) {
        TextInputDialog dialog = new TextInputDialog(_default);
        dialog.setTitle(action);
        dialog.setHeaderText(question);
        return dialog.showAndWait();
    }

    public void createBudget() {
        String name;
        String unit;
        {
            // User interaction - read only
            Relations allocated = edit.getAllocated();

            Optional<String> optionalName = textInput(
                    "Create Budget",
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
                    "Create Budget",
                    "Enter unit for " + name, "units");
            if (!optionalUnit.isPresent()) {
                return;
            }
            unit = optionalUnit.get();
        }

        edit.updateAllocated(baseline -> {
            return Budget.add(baseline, new Budget.Key(name, unit)).getKey();
        });
    }

    public void addFunctionToItem(Item item, Optional<Function> trace, Point2D origin) {
        Optional<String> result;
        {
            // User interaction - read only
            Relations allocated = edit.getAllocated();
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
                return Function.create(baseline, item, trace, result.get(), origin)
                        .getKey();
            });
        }
    }

    public void renumber(Item item) {
        Optional<String> result;
        {
            // User interaction - read only
            if (item.isExternal() || !edit.getAllocated().get(item).isPresent()) {
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
                    return item.setShortId(allocated, path).getKey();
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
            if (item.isExternal() || !edit.getAllocated().get(item).isPresent()) {
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
                    return item.setName(allocated, name).getKey();
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
            Relations allocated = edit.getAllocated();
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
                    return function.setName(allocated, name).getKey();
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
            Relations allocated = edit.getAllocated();
            Optional<Budget> budget = Budget.find(allocated, key).findAny();
            if (!budget.isPresent()) {
                return;
            }

            result = textInput(
                    "Rename Budget",
                    "Enter Name for Budget", key.getName());
        }
        if (result.isPresent()) {
            edit.updateState(state -> {
                Budget.Key newKey = key.setName(result.get());
                {
                    Relations functional = state.getFunctional();
                    Iterator<Budget> it = Budget.find(functional, key).iterator();
                    while (it.hasNext()) {
                        Budget budget = it.next();
                        functional = budget.setKey(functional, newKey).getKey();
                    }
                    state = state.setFunctional(functional);
                }
                {
                    Relations allocated = state.getAllocated();
                    Iterator<Budget> it = Budget.find(allocated, key).iterator();
                    while (it.hasNext()) {
                        Budget budget = it.next();
                        allocated = budget.setKey(allocated, newKey).getKey();
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
            Relations allocated = edit.getAllocated();
            Optional<Budget> budget = Budget.find(allocated, key).findAny();
            if (!budget.isPresent()) {
                return;
            }

            result = textInput(
                    "Set Budget Unit",
                    "Enter Unit for Budget " + key.getName(),
                    key.getUnit());
        }
        if (result.isPresent()) {
            edit.updateState(state -> {
                Budget.Key newKey = key.setUnit(result.get());
                {
                    Relations functional = state.getFunctional();
                    Iterator<Budget> it = Budget.find(functional, key).iterator();
                    while (it.hasNext()) {
                        Budget budget = it.next();
                        functional = budget.setKey(functional, newKey).getKey();
                    }
                    state = state.setFunctional(functional);
                }
                {
                    Relations allocated = state.getAllocated();
                    Iterator<Budget> it = Budget.find(allocated, key).iterator();
                    while (it.hasNext()) {
                        Budget budget = it.next();
                        allocated = budget.setKey(allocated, newKey).getKey();
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
            Relations allocated = edit.getAllocated();
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
                    return view.get().setColor(allocated, color).getKey();
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
                return Interface.create(allocated, left, right).getKey();
            });
        }
    }

    private Pair<UndoState, FlowType> getTypeForNewFlow(
            UndoState state, RelationPair<Function> endpoints) {
        final Relations functional = state.getFunctional();
        final Relations allocated = state.getAllocated();
        /*
         * A stream of functional baseline types that have flows in the required
         * direction
         */
        // See if we can pick out a likely type
        Optional<Function> leftTrace = endpoints.getLeft().getTrace(functional);
        Optional<Function> rightTrace = endpoints.getRight().getTrace(functional);
        Optional<FlowType> suggestion;
        if (leftTrace.isPresent() && rightTrace.isPresent()
                && !leftTrace.equals(rightTrace)) {
            RelationPair<Function> endpointTraces = new RelationPair<>(
                    leftTrace.get(), rightTrace.get(), endpoints.getDirection());

            Set<String> alreadyUsed = Flow.find(allocated).parallel()
                    .filter(flow -> {
                        Optional<Function> existingLeftTrace = flow.getLeft(allocated).getTrace(functional);
                        Optional<Function> existingRightTrace = flow.getRight(allocated).getTrace(functional);
                        if (existingLeftTrace.isPresent() && existingRightTrace.isPresent()
                                && !existingLeftTrace.equals(existingRightTrace)) {
                            RelationPair<Function> existingEndpointTraces = new RelationPair<>(
                                    existingLeftTrace.get(), existingRightTrace.get(), flow.getDirection());
                            return existingEndpointTraces.contains(endpointTraces);
                        } else {
                            return false;
                        }
                    })
                    .map(flow -> flow.getType().getKey())
                    .collect(Collectors.toSet());

            suggestion = endpointTraces.getLeft().findFlows(functional)
                    // Flows to the right function in the right direction
                    .filter(flow -> {
                        if (flow.otherEnd(functional, endpointTraces.getLeft())
                                != endpointTraces.getRight()) {
                            return false;
                        }
                        return flow.getDirectionFrom(endpointTraces.getLeft())
                                .contains(endpointTraces.getDirection());
                    })
                    .map(flow -> flow.getType(functional))
                    .filter(type -> !alreadyUsed.contains(type.getIdentifier()))
                    .findAny();
        } else {
            suggestion = Optional.empty();
        }

        if (suggestion.isPresent()) {
            // Flow down to the allocated baseline if needed
            Optional<FlowType> allocatedType = FlowType.find(
                    allocated, suggestion.get().getName());
            if (allocatedType.isPresent()) {
                return state.and(allocatedType.get());
            } else {
                return state.setAllocated(allocated.add(suggestion.get()))
                        .and(suggestion.get());
            }
        }
        String name = FlowType.find(allocated).parallel()
                .map(FlowType::getName)
                .collect(new UniqueName("New Flow"));
        Optional<FlowType> trace = FlowType.find(functional, name);
        Pair<Relations, FlowType> result = FlowType.add(allocated, trace, name);
        return state.setAllocated(result.getKey()).and(result.getValue());
    }

    public void addFlow(Function source, Function target) {
        edit.updateState(state -> {
            Relations allocated = state.getAllocated();
            Optional<Function> left = allocated.get(source);
            Optional<Function> right = allocated.get(target);
            if (left.isPresent() && right.isPresent()
                    // One end has to be internal
                    && (!left.get().isExternal() || !right.get().isExternal())) {
                RelationPair<Function> endpoints = new RelationPair<>(
                        source, target, Direction.Normal);
                Pair<UndoState, FlowType> result = getTypeForNewFlow(state, endpoints);
                state = result.getKey();
                allocated = state.getAllocated();
                FlowType flowType = result.getValue();
                allocated = Flow.add(allocated, endpoints, flowType).getKey();
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
            Relations allocated = edit.getAllocated();
            Optional<Flow> current = allocated.get(flow);
            if (!current.isPresent()) {
                return;
            }
            FlowType type = current.get().getType().getTarget(allocated);

            TextInputDialog dialog = new TextInputDialog(type.getName());
            dialog.setTitle("New Flow Type");
            dialog.setHeaderText("Enter Flow Type");

            interactionResult = dialog.showAndWait();
        }
        if (interactionResult.isPresent()) {
            String typeName = interactionResult.get();
            edit.updateState(state -> {
                Relations allocated = state.getAllocated();
                Optional<Flow> current = allocated.get(flow);
                if (!current.isPresent()) {
                    return state;
                }
                FlowType currentType = current.get().getType().getTarget(allocated);
                if (currentType.getName().equals(typeName)) {
                    return state;
                }
                Optional<FlowType> newType = FlowType.find(allocated, typeName);
                if (!newType.isPresent()) {
                    // See if we can flow the type down
                    Relations functional = state.getFunctional();
                    Optional<FlowType> trace = FlowType.find(functional, typeName);
                    Pair<Relations, FlowType> result = FlowType.add(allocated, trace, typeName);
                    allocated = result.getKey();
                    newType = Optional.of(result.getValue());
                }
                allocated = current.get().setType(allocated, newType.get()).getKey();
                return state.setAllocated(allocated);
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

    public void navigateDown() {
        try {
            navigateDown(edit.getLastChild());
        } catch (EmptyStackException ex) {
            // Nowhere to navigate down to
        }
    }

    public void navigateDown(Item item) {
        this.navigateDown(item.asIdentity(edit.getAllocated()));
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
                edit.saveTo(Directory.forPath(Paths.get(selectedDirectory.getPath())));
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
            RecentFiles.addRecentFile(dir.getPath());
            Optional<VersionInfo> baseline = edit.getVersionControl().getDefaultBaseline();
            if (baseline.isPresent()) {
                // Open default diff baseline
                edit.setDiffVersion(baseline);
            }
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
        Optional<Directory> current = edit.getCurrentDirectory();
        if (current.isPresent()) {
            chooser.setInitialDirectory(current.get().getPath().toFile());
        }
        File selectedDirectory = chooser.showDialog(window);
        if (selectedDirectory == null) {
            return false;
        } else {
            Directory dir = Directory.forPath(Paths.get(selectedDirectory.getPath()));
            return tryLoad(edit, dir);
        }
    }
}
