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
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.baselines.FunctionalBaseline;
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
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;
import javax.annotation.Nullable;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalTreeController {

    public PhysicalTreeController(EditState state, TreeView<Item> view) {
        this.editState = state;
        this.view = view;
        this.changed = new SingleRunnable<>(editState.getExecutor(), new Changed());
        this.updateView = new SingleRunnable<>(JFXExecutor.instance(), new UpdateView());
    }

    public void start() {
        editState.subscribe(changed);
        changed.run();
    }

    public void stop() {
        editState.unsubscribe(changed);
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

        private TreeState(FunctionalBaseline functional, AllocatedBaseline allocated) {
            this.systemOfInterest = functional == null ? null : functional.getSystemOfInterest();
            SortedMap<IDPath, Item> tmpItems = new TreeMap<>();
            RelationContext context = allocated.getStore();
            for (Item item : allocated.getItems()) {
                if (!item.isExternal()) {
                    tmpItems.put(item.getIdPath(context), item);
                }
            }
            this.items = Collections.unmodifiableSortedMap(tmpItems);
        }
    }

    private class Changed implements Runnable {

        @Override
        public void run() {
            TreeState newState = new TreeState(
                    editState.getFunctionalBaseline(), editState.getUndo().get());
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
            TreeItem root;
            if (state.systemOfInterest == null) {
                root = new TreeItem();
            } else {
                root = toNode(state.systemOfInterest);
            }
            for (Item item : state.items.values()) {
                root.getChildren().add(toNode(item));
            }
            root.setExpanded(true);
            view.setRoot(root);
            view.setShowRoot(state.systemOfInterest != null);
            view.setEditable(true);
            view.setCellFactory(new Callback<TreeView<Item>, TreeCell<Item>>() {
                @Override
                public TreeCell<Item> call(TreeView<Item> p) {
                    return new ItemTreeCell(editState.getUndo());
                }
            });
        }

        private TreeItem toNode(Item item) {
            TreeItem node = new TreeItem(item);
            return node;
        }
    }

    private final class ItemTreeCell extends TreeCell<Item> {

        private final UndoBuffer<AllocatedBaseline> undo;
        private TextField textField;
        private final ContextMenu addMenu = new ContextMenu();

        public ItemTreeCell(UndoBuffer<AllocatedBaseline> tmpUndo) {
            this.undo = tmpUndo;
            MenuItem addMenuItem = new MenuItem("Add Item");
            addMenu.getItems().add(addMenuItem);
            addMenuItem.setOnAction(new EventHandler() {
                @Override
                public void handle(Event t) {
                    AllocatedBaseline baseline = undo.get();
                    Item item = Item.newItem(
                            baseline.getIdentity().getUuid(),
                            baseline.getNextItemId(),
                            "New Item", "");
                    TreeItem newItem = new TreeItem<>(item);
                    getTreeView().getRoot().getChildren().add(newItem);
                    undo.set(undo.get().addItem(item));
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
            super.commitEdit(item);
            undo.set(undo.get().addItem(item));
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
                    setContextMenu(addMenu);
                }
            }
        }

        private void createTextField() {
            textField = new TextField(getItem().getName());
            textField.setOnKeyReleased(new EventHandler<KeyEvent>() {

                @Override
                public void handle(KeyEvent t) {
                    if (t.getCode() == KeyCode.ENTER) {
                        commitEdit(getItem().setName(textField.getText()));
                    } else if (t.getCode() == KeyCode.ESCAPE) {
                        cancelEdit();
                    }
                }
            });
        }

        private String getString() {
            return getItem() == null ? "" : getItem().toString();
        }
    }
    private final EditState editState;
    private final TreeView<Item> view;
    private final AtomicReference<TreeState> treeState = new AtomicReference<>();
    private final SingleRunnable<Changed> changed;
    private final SingleRunnable<UpdateView> updateView;
}
