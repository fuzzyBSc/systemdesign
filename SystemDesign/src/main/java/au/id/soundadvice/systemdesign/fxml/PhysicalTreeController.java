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
import au.id.soundadvice.systemdesign.model.IDPath;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.relation.RelationContext;
import au.id.soundadvice.systemdesign.undo.UndoBuffer;
import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javax.annotation.Nullable;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalTreeController {

    public PhysicalTreeController(EditState edit, TreeView<Item> view) {
        this.edit = edit;
        this.view = view;
        this.changed = new SingleRunnable<>(edit.getExecutor(), new Changed());
        this.updateView = new SingleRunnable<>(JFXExecutor.instance(), new UpdateView());
    }

    public void start() {
        addContextMenu();
        edit.subscribe(changed);
        changed.run();
    }

    public void stop() {
        edit.unsubscribe(changed);
    }

    public void addContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        UndoBuffer<UndoState> undo = edit.getUndo();
        MenuItem addMenuItem = new MenuItem("Add Item");
        contextMenu.getItems().add(addMenuItem);
        addMenuItem.setOnAction((ActionEvent t) -> {
            UndoState state = undo.get();
            AllocatedBaseline baseline = state.getAllocated();
            String name = baseline.getItems().parallelStream()
                    .map(Item::getName)
                    .collect(new UniqueName("New Item"));
            Item item1 = Item.newItem(
                    baseline.getIdentity().getUuid(),
                    baseline.getNextItemId(),
                    name, "");
            undo.set(state.setAllocated(baseline.add(item1)));
        });
        view.setContextMenu(contextMenu);
    }

    private static class TreeState {

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 83 * hash + Objects.hashCode(this.systemOfInterest);
            hash = 83 * hash + Objects.hashCode(this.items);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final TreeState other = (TreeState) obj;
            if (!Objects.equals(this.systemOfInterest, other.systemOfInterest)) {
                return false;
            }
            if (!Objects.equals(this.items, other.items)) {
                return false;
            }
            return true;
        }

        public TreeState(Item systemOfInterest, SortedMap<IDPath, Item> items) {
            this.systemOfInterest = systemOfInterest;
            this.items = items;
        }

        @Nullable
        private final Item systemOfInterest;
        private final SortedMap<IDPath, Item> items;

        private TreeState(UndoState state) {
            FunctionalBaseline functional = state.getFunctional();
            AllocatedBaseline allocated = state.getAllocated();
            this.systemOfInterest = functional == null ? null : functional.getSystemOfInterest();
            SortedMap<IDPath, Item> tmpItems = new TreeMap<>();
            RelationContext context = allocated.getStore();
            allocated.getItems().stream()
                    .forEach((item) -> tmpItems.put(item.getIdPath(context), item));
            this.items = Collections.unmodifiableSortedMap(tmpItems);
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
            TreeItem systemOfInterest;
            if (state.systemOfInterest == null) {
                systemOfInterest = root;
            } else {
                systemOfInterest = toNode(state.systemOfInterest);
                systemOfInterest.setExpanded(true);
                root.getChildren().add(systemOfInterest);
            }
            state.items.values().stream().forEach((item) -> {
                if (item.isExternal()) {
                    root.getChildren().add(toNode(item));
                } else {
                    systemOfInterest.getChildren().add(toNode(item));
                }
            });
            view.setRoot(root);
            view.setShowRoot(false);
            view.setEditable(true);
            view.setCellFactory(
                    (TreeView<Item> p) -> new ItemTreeCell(edit.getUndo()));
        }

        private TreeItem toNode(Item item) {
            TreeItem node = new TreeItem(item);
            return node;
        }
    }

    private final class ItemTreeCell extends TreeCell<Item> {

        private final UndoBuffer<UndoState> undo;
        private TextField textField;
        private final ContextMenu contextMenu = new ContextMenu();

        public ItemTreeCell(UndoBuffer<UndoState> tmpUndo) {
            this.undo = tmpUndo;
            MenuItem addMenuItem = new MenuItem("Add Item");
            contextMenu.getItems().add(addMenuItem);
            addMenuItem.setOnAction((ActionEvent t) -> {
                UndoState state = undo.get();
                AllocatedBaseline baseline = state.getAllocated();
                String name = baseline.getItems().parallelStream()
                        .map(Item::getName)
                        .collect(new UniqueName("New Item"));
                Item item1 = Item.newItem(
                        baseline.getIdentity().getUuid(),
                        baseline.getNextItemId(),
                        name, "");
                TreeItem newItem = new TreeItem<>(item1);
                getTreeView().getRoot().getChildren().add(newItem);
                undo.set(state.setAllocated(baseline.add(item1)));
            });
            MenuItem deleteMenuItem = new MenuItem("Delete Item");
            contextMenu.getItems().add(deleteMenuItem);
            deleteMenuItem.setOnAction((ActionEvent t) -> {
                UndoState state = undo.get();
                AllocatedBaseline baseline = state.getAllocated();
                TreeItem<Item> treeItem1 = getTreeItem();
                Item item1 = treeItem1.getValue();
                if (treeItem1.isLeaf()) {
                    undo.set(state.setAllocated(
                            state.getAllocated().remove(item1.getUuid())));
                }
            });
        }

        @Override
        public void startEdit() {
            super.startEdit();

            if (textField == null) {
                createTextField();
            }
            setText(null);
            setGraphic(textField);
            textField.selectAll();
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
        public void commitEdit(Item item) {
            UndoState state = undo.get();
            super.commitEdit(item);
            undo.set(state.setAllocated(
                    state.getAllocated().add(item)));
        }

        @Override
        public void updateItem(Item item, boolean empty) {
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

        private void createTextField() {
            textField = new TextField(getItem().getName());
            textField.setOnKeyReleased((KeyEvent t) -> {
                if (t.getCode() == KeyCode.ENTER) {
                    commitEdit(getItem().setName(textField.getText()));
                } else if (t.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                }
            });
        }

        private String getString() {
            return getItem() == null ? "" : getItem().toString();
        }
    }
    private final EditState edit;
    private final TreeView<Item> view;
    private final AtomicReference<TreeState> treeState = new AtomicReference<>();
    private final SingleRunnable<Changed> changed;
    private final SingleRunnable<UpdateView> updateView;
}
