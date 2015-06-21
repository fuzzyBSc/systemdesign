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
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.baselines.UndoBuffer;
import au.id.soundadvice.systemdesign.fxml.DropHandlers.FunctionDropHandler;
import au.id.soundadvice.systemdesign.fxml.drag.DragSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalTreeController {

    public LogicalTreeController(
            Interactions interactions, EditState edit,
            TreeView<Function> view) {
        this.interactions = interactions;
        this.edit = edit;
        this.view = view;
        this.changed = new SingleRunnable<>(edit.getExecutor(), new Changed());
        this.updateView = new SingleRunnable<>(JFXExecutor.instance(), new UpdateView());
        this.functionCreator = new FunctionCreator(edit);
    }

    public void start() {
        addContextMenu();
        edit.subscribe(changed);
        changed.run();
    }

    public void stop() {
        edit.unsubscribe(changed);
    }

    private void addContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem addMenuItem = new MenuItem("Add Function");
        contextMenu.getItems().add(addMenuItem);
        addMenuItem.setOnAction(event -> functionCreator.addToChild());
        view.setContextMenu(contextMenu);
    }

    public static final class FunctionAllocation {

        public FunctionAllocation(Optional<Function> parent, SortedMap<String, Function> children) {
            this.parent = parent;
            this.children = children;
        }

        public String getDisplayName() {
            return parent.map(Function::getName).orElse("Logical");
        }

        private final Optional<Function> parent;
        private final SortedMap<String, Function> children;

        private boolean isEmpty() {
            return children.isEmpty();
        }
    }

    private static class TreeState {

        private final SortedMap<String, FunctionAllocation> allocation;
        private final FunctionAllocation orphans;

        private TreeState(UndoState state) {
            Optional<FunctionalBaseline> functional = state.getFunctional();
            AllocatedBaseline allocated = state.getAllocated();

            Map<UUID, Function> parentFunctions;
            if (functional.isPresent()) {
                UUID systemUuid = functional.get().getSystemOfInterest().getUuid();
                parentFunctions = new HashMap<>();
                functional.get().getStore().getReverse(systemUuid, Function.class)
                        .forEach((function) -> parentFunctions.put(function.getUuid(), function));
            } else {
                parentFunctions = Collections.emptyMap();
            }

            Map<Function, SortedMap<String, Function>> rawAllocation = new HashMap<>();
            parentFunctions.values()
                    .forEach((parent) -> rawAllocation.put(parent, new TreeMap<>()));
            // Keep orphans separate to avoid null pointers in TreeMap
            SortedMap<String, Function> rawOrphans = new TreeMap<>();
            allocated.getFunctions()
                    .forEach(child -> {
                        // Parent may be null, ie child is orphaned
                        Optional<UUID> trace = child.getTrace();
                        Optional<Function> parent = trace.map(uuid -> parentFunctions.get(uuid));
                        SortedMap<String, Function> map = parent.isPresent()
                                ? rawAllocation.get(parent.get()) : rawOrphans;
                        map.put(child.getDisplayName(allocated.getStore()), child);
                    });

            SortedMap<String, FunctionAllocation> tmpAllocation = new TreeMap<>();
            rawAllocation.entrySet().stream()
                    .map((entry) -> new FunctionAllocation(Optional.of(entry.getKey()), Collections.unmodifiableSortedMap(entry.getValue())))
                    .forEach((alloc) -> {
                        tmpAllocation.put(alloc.getDisplayName(), alloc);
                    });
            this.allocation = Collections.unmodifiableSortedMap(tmpAllocation);
            this.orphans = new FunctionAllocation(
                    Optional.empty(), Collections.unmodifiableSortedMap(rawOrphans));
        }
    }

    private class Changed implements Runnable {

        @Override
        public void run() {
            TreeState newState = new TreeState(edit.getUndo().get());
            TreeState oldState = treeState.getAndSet(newState);
            if (!newState.equals(oldState)) {
                updateView.run();
            }
        }

    }

    private class UpdateView implements Runnable {

        @Override
        public void run() {
            TreeState state = treeState.get();
            TreeItem root = new TreeItem();
            root.setExpanded(true);
            root.getChildren().addAll(
                    state.allocation.values().stream()
                    .filter((allocation) -> allocation.parent.isPresent())
                    .map((allocation) -> {
                        TreeItem parent = new TreeItem(allocation.parent.get());
                        parent.setExpanded(true);
                        parent.getChildren().addAll(allocation.children.values().stream()
                                .map((child) -> new TreeItem(child))
                                .toArray());
                        return parent;
                    }).toArray());
            // Add orphans at the end
            FunctionAllocation orphans = state.orphans;
            if (!orphans.isEmpty()) {
                TreeItem parent;
                if (root.getChildren().isEmpty()) {
                    parent = root;
                } else {
                    parent = new TreeItem();
                    parent.setExpanded(true);
                    root.getChildren().add(parent);
                }
                parent.getChildren().addAll(orphans.children.values().stream()
                        .map((child) -> new TreeItem(child))
                        .toArray());
            }
            view.setRoot(root);
            view.setShowRoot(false);
            view.setEditable(true);
            view.setCellFactory(view -> {
                FunctionTreeCell cell = new FunctionTreeCell(edit.getUndo());
                cell.start();
                return cell;
            });
        }
    }

    private final class FunctionTreeCell extends TreeCell<Function> {

        private final UndoBuffer<UndoState> undo;
        private Optional<TextField> textField = Optional.empty();
        private final ContextMenu contextMenu = new ContextMenu();

        public FunctionTreeCell(UndoBuffer<UndoState> tmpUndo) {
            this.undo = tmpUndo;
            MenuItem addMenuItem = new MenuItem("Add Function");
            contextMenu.getItems().add(addMenuItem);
            addMenuItem.setOnAction(event -> functionCreator.add(getItem()));
            MenuItem deleteMenuItem = new MenuItem("Delete Function");
            contextMenu.getItems().add(deleteMenuItem);
            deleteMenuItem.setOnAction(event -> edit.remove(getItem().getUuid()));
            this.editableProperty().bind(this.itemProperty().isNotNull());
        }

        public void start() {
            DragSource.bind(this, () -> Optional.ofNullable(getItem()), false);
            DragTarget.bind(edit, this, () -> Optional.ofNullable(getItem()),
                    new FunctionDropHandler(interactions, edit));
        }

        @Override
        public void startEdit() {
            Function function = getItem();
            if (function != null) {
                super.startEdit();

                if (!textField.isPresent()) {
                    textField = Optional.of(createTextField(getItem()));
                }
                setText(null);
                setGraphic(textField.get());
                textField.get().selectAll();
                textField.get().requestFocus();
            }
        }

        @Override
        public void cancelEdit() {
            if (isFocused()) {
                super.cancelEdit();

                setText(getString());
                setGraphic(getTreeItem().getGraphic());
            } else {
                /*
                 * If the cancelEdit is due to a loss of focus, override it.
                 * Commit instead.
                 */
                if (textField.isPresent()) {
                    commitEdit(getItem().setName(textField.get().getText()));
                }
            }
        }

        @Override
        public void commitEdit(Function function) {
            super.commitEdit(function);
            edit.getUndo().update(state -> {
                Optional<FunctionalBaseline> functional = state.getFunctional();
                if (functional.isPresent() && functional.get().hasRelation(function)) {
                    // This function is a member of the functional baseline
                    return state.setFunctional(functional.get().add(function));
                } else {
                    return state.setAllocated(
                            state.getAllocated().add(function));
                }
            });
        }

        @Override
        public void updateItem(Function item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    setText(null);
                    if (textField.isPresent()) {
                        textField.get().setText(getString());
                        setGraphic(textField.get());
                    }
                } else {
                    setText(getString());
                    setGraphic(getTreeItem().getGraphic());
                    setContextMenu(contextMenu);
                }
            }
        }

        @CheckReturnValue
        private TextField createTextField(Function function) {
            TextField node = new TextField(function.getName());
            node.setOnKeyReleased((KeyEvent t) -> {
                if (t.getCode() == KeyCode.ENTER) {
                    commitEdit(function.setName(node.getText()));
                } else if (t.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                }
            });
            return node;
        }

        private String getString() {
            return getItem() == null ? "(unallocated)" : getItem().toString();
        }
    }
    private final Interactions interactions;
    private final EditState edit;
    private final TreeView<Function> view;
    private final AtomicReference<TreeState> treeState = new AtomicReference<>();
    private final SingleRunnable<Changed> changed;
    private final SingleRunnable<UpdateView> updateView;
    private final FunctionCreator functionCreator;
}
