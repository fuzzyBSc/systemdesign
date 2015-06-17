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
import au.id.soundadvice.systemdesign.files.Identifiable;
import au.id.soundadvice.systemdesign.fxml.drag.ConnectHandler;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.undo.UndoBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalTreeController {

    public LogicalTreeController(EditState edit, TreeView<Function> view) {
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

        public FunctionAllocation(Function parent, SortedMap<String, Function> children) {
            this.parent = parent;
            this.children = children;
        }

        public String getDisplayName() {
            return parent.getDisplayName();
        }

        private final Function parent;
        private final SortedMap<String, Function> children;

        private boolean isEmpty() {
            return children.isEmpty();
        }
    }

    private static class TreeState {

        private final SortedMap<String, FunctionAllocation> allocation;
        private final FunctionAllocation orphans;

        private TreeState(UndoState state) {
            FunctionalBaseline functional = state.getFunctional();
            AllocatedBaseline allocated = state.getAllocated();

            Map<UUID, Function> parentFunctions;
            if (functional == null) {
                parentFunctions = Collections.emptyMap();
            } else {
                UUID systemUuid = functional.getSystemOfInterest().getUuid();
                parentFunctions = new HashMap<>();
                functional.getStore().getReverse(systemUuid, Function.class).stream()
                        .forEach((function) -> parentFunctions.put(function.getUuid(), function));
            }

            Map<Function, SortedMap<String, Function>> rawAllocation = new HashMap<>();
            parentFunctions.values().stream()
                    .forEach((parent) -> rawAllocation.put(parent, new TreeMap<>()));
            // Keep orphans separate to avoid null pointers in TreeMap
            SortedMap<String, Function> rawOrphans = new TreeMap<>();
            allocated.getFunctions().stream()
                    .forEach((child) -> {
                        // Parent may be null, ie child is orphaned
                        UUID trace = child.getTrace();
                        Function parent = trace == null ? null : parentFunctions.get(trace);
                        SortedMap<String, Function> map = parent == null ? rawOrphans : rawAllocation.get(parent);
                        map.put(child.getDisplayName(), child);
                    });

            SortedMap<String, FunctionAllocation> tmpAllocation = new TreeMap<>();
            rawAllocation.entrySet().stream()
                    .map((entry) -> new FunctionAllocation(entry.getKey(), Collections.unmodifiableSortedMap(entry.getValue())))
                    .forEach((alloc) -> {
                        tmpAllocation.put(alloc.getDisplayName(), alloc);
                    });
            this.allocation = Collections.unmodifiableSortedMap(tmpAllocation);
            this.orphans = new FunctionAllocation(
                    null, Collections.unmodifiableSortedMap(rawOrphans));
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
                    .filter((allocation) -> allocation.parent != null)
                    .map((allocation) -> {
                        TreeItem parent = new TreeItem(allocation.parent);
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
            view.setCellFactory(
                    (TreeView<Function> p) -> new FunctionTreeCell(edit.getUndo()));
        }
    }

    private final class FunctionTreeCell extends TreeCell<Function> {

        private final UndoBuffer<UndoState> undo;
        private TextField textField;
        private final ContextMenu contextMenu = new ContextMenu();

        public FunctionTreeCell(UndoBuffer<UndoState> tmpUndo) {
            this.undo = tmpUndo;
            MenuItem addMenuItem = new MenuItem("Add Function");
            contextMenu.getItems().add(addMenuItem);
            addMenuItem.setOnAction(event -> {
                Function relation = getItem();
                FunctionalBaseline functional = undo.get().getFunctional();
                if (functional != null && functional.hasRelation(relation)) {
                    functionCreator.addToParent();
                } else {
                    functionCreator.addToChild();
                }
            });
            MenuItem deleteMenuItem = new MenuItem("Delete Function");
            contextMenu.getItems().add(deleteMenuItem);
            deleteMenuItem.setOnAction(event -> {
                UndoState state = undo.get();
                TreeItem<Function> treeItem1 = getTreeItem();
                Function relation = treeItem1.getValue();
                if (treeItem1.isLeaf()) {
                    undo.set(state.setAllocated(
                            state.getAllocated().remove(relation.getUuid())));
                } else {
                    FunctionalBaseline functional = state.getFunctional();
                    if (functional != null) {
                        undo.set(state.setFunctional(functional.remove(relation.getUuid())));
                    }
                }
            });
            this.itemProperty().addListener(value -> this.setEditable(value != null));

            this.setOnDragDetected(event -> {
                Function function = getItem();
                UndoState state = undo.get();
                if (state.getFunctional() != null
                        && state.getAllocated().hasRelation(function)) {
                    // Start dragging
                    Dragboard db = this.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.put(DataFormat.PLAIN_TEXT, function.toString());
                    content.put(DataFormat.URL, ConnectHandler.uuidPrefix + function.getUuid());
                    db.setContent(content);
                    this.getStyleClass().add("dragSource");

                    event.consume();
                }
            });
            this.setOnDragOver(event -> {
                // Decide whether or not to accept the drop
                UndoState state = undo.get();
                Function source = state.getAllocated().getStore().get(
                        ConnectHandler.toUUID(event.getDragboard()), Function.class);
                if (canConnect(state, source, getItem())) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    event.consume();
                }
            });
            this.setOnDragEntered(event -> {
                // Show drag acceptance visually
                UndoState state = undo.get();
                Function source = state.getAllocated().getStore().get(
                        ConnectHandler.toUUID(event.getDragboard()), Function.class);
                if (canConnect(state, source, getItem())) {
                    this.getStyleClass().add("dragTarget");
                    event.consume();
                }
            });
            this.setOnDragExited(event -> {
                // Unshow drag acceptance
                this.getStyleClass().remove("dragTarget");
                event.consume();
            });
            this.setOnDragDropped(event -> {
                // Handle drop
                UndoState state = undo.get();
                AllocatedBaseline allocated = state.getAllocated();
                Function source = allocated.getStore().get(
                        ConnectHandler.toUUID(event.getDragboard()), Function.class);
                Function target = getItem();
                boolean ok;
                if (source != null && canConnect(state, source, target)) {
                    List<UUID> toRemove
                            = allocated.getStore().getReverse(source.getUuid(), Flow.class).parallelStream()
                            .map(Identifiable::getUuid)
                            .collect(Collectors.toList());
                    source = source.setTrace(target.getUuid());
                    undo.set(state.setAllocated(allocated.removeAll(toRemove).add(source)));
                    ok = true;
                } else {
                    ok = false;
                }
                event.setDropCompleted(ok);

                event.consume();
            });
            this.setOnDragDone(event -> {
                this.getStyleClass().remove("dragSource");
                event.consume();
            });
        }

        public boolean canConnect(UndoState state, Function source, Function target) {
            FunctionalBaseline functional = state.getFunctional();
            boolean targetOK = functional != null && functional.hasRelation(target);
            if (targetOK) {
                boolean sourceOK = source != null && !target.getUuid().equals(source.getTrace());
                return sourceOK;
            } else {
                return false;
            }
        }

        @Override
        public void startEdit() {
            Function function = getItem();
            if (function != null) {
                super.startEdit();

                if (textField == null) {
                    createTextField(getItem());
                }
                setText(null);
                setGraphic(textField);
                textField.selectAll();
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
                commitEdit(getItem().setName(textField.getText()));
            }
        }

        @Override
        public void commitEdit(Function function) {
            UndoState state = undo.get();
            super.commitEdit(function);
            FunctionalBaseline functional = state.getFunctional();
            if (functional != null && functional.hasRelation(function)) {
                // This function is a member of the functional baseline
                undo.set(state.setFunctional(functional.add(function)));
            } else {
                undo.set(state.setAllocated(
                        state.getAllocated().add(function)));
            }
        }

        @Override
        public void updateItem(Function item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (textField != null) {
                        textField.setText(getString());
                    }
                    setText(null);
                    setGraphic(textField);
                } else {
                    setText(getString());
                    setGraphic(getTreeItem().getGraphic());
                    setContextMenu(contextMenu);
                }
            }
        }

        private void createTextField(Function function) {
            textField = new TextField(function.getName());
            textField.setOnKeyReleased((KeyEvent t) -> {
                if (t.getCode() == KeyCode.ENTER) {
                    commitEdit(function.setName(textField.getText()));
                } else if (t.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                }
            });
        }

        private String getString() {
            return getItem() == null ? "(unallocated)" : getItem().toString();
        }
    }
    private final EditState edit;
    private final TreeView<Function> view;
    private final AtomicReference<TreeState> treeState = new AtomicReference<>();
    private final SingleRunnable<Changed> changed;
    private final SingleRunnable<UpdateView> updateView;
    private final FunctionCreator functionCreator;
}
