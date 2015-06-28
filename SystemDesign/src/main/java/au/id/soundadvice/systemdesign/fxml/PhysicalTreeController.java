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
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.model.IDPath;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.fxml.DropHandlers.ItemDropHandler;
import au.id.soundadvice.systemdesign.fxml.drag.DragSource;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget;
import au.id.soundadvice.systemdesign.model.ItemView;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalTreeController {

    public PhysicalTreeController(
            Interactions interactions, EditState edit,
            TreeView<Item> view) {
        this.edit = edit;
        this.view = view;
        this.changed = new SingleRunnable<>(edit.getExecutor(), new Changed());
        this.updateView = new SingleRunnable<>(JFXExecutor.instance(), new UpdateView());
        this.interactions = interactions;
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
        MenuItem addMenuItem = new MenuItem("Add Item");
        contextMenu.getItems().add(addMenuItem);
        addMenuItem.setOnAction(event -> {
            interactions.createItem(ItemView.defaultOrigin);
            event.consume();
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

        private final Optional<Item> systemOfInterest;
        private final SortedMap<IDPath, Item> items;

        private TreeState(UndoState state) {
            Baseline allocated = state.getAllocated();
            this.systemOfInterest = state.getSystemOfInterest();
            SortedMap<IDPath, Item> tmpItems = new TreeMap<>();
            allocated.getItems()
                    .forEach((item) -> tmpItems.put(item.getIdPath(allocated), item));
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
            if (state.systemOfInterest.isPresent()) {
                systemOfInterest = toNode(state.systemOfInterest.get());
                systemOfInterest.setExpanded(true);
                root.getChildren().add(systemOfInterest);
            } else {
                systemOfInterest = root;
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
            view.setCellFactory(view -> {
                ItemTreeCell cell = new ItemTreeCell();
                cell.start();
                return cell;
            });
        }

        private TreeItem toNode(Item item) {
            TreeItem node = new TreeItem(item);
            return node;
        }
    }

    private final class ItemTreeCell extends TreeCell<Item> {

        private Optional<TextField> textField = Optional.empty();
        private final ContextMenu contextMenu = new ContextMenu();

        public ItemTreeCell() {
            MenuItem addMenuItem = new MenuItem("Add Item");
            contextMenu.getItems().add(addMenuItem);
            addMenuItem.setOnAction(event -> {
                interactions.createItem(ItemView.defaultOrigin);
                event.consume();
            });
            MenuItem renameMenuItem = new MenuItem("Renumber");
            contextMenu.getItems().add(renameMenuItem);
            renameMenuItem.setOnAction(event -> {
                interactions.renumber(getItem());
                event.consume();
            });
            MenuItem deleteMenuItem = new MenuItem("Delete Item");
            contextMenu.getItems().add(deleteMenuItem);
            deleteMenuItem.setOnAction(event -> {
                edit.updateAllocated(baseline -> {
                    return getItem().removeFrom(baseline);
                });
                event.consume();
            });
        }

        public void start() {
            DragSource.bind(this, () -> Optional.ofNullable(getItem()), false);
            DragTarget.bind(edit, this, () -> Optional.ofNullable(getItem()),
                    new ItemDropHandler(interactions));
        }

        @Override
        public void startEdit() {
            super.startEdit();

            if (!textField.isPresent()) {
                textField = Optional.of(createTextField());
            }
            setText(null);
            setGraphic(textField.get());
            textField.get().selectAll();
            textField.get().requestFocus();
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
                    commitEdit(getItem());
                }
            }
        }

        @Override
        public void commitEdit(Item item) {
            edit.updateAllocated(allocated -> {
                Optional<Item> current = allocated.get(item);
                if (current.isPresent()) {
                    Baseline.BaselineAnd<Item> result = current.get().setName(
                            allocated, textField.get().getText());
                    super.commitEdit(result.getRelation());
                    return result.getBaseline();
                } else {
                    return allocated;
                }
            });
        }

        @Override
        public void updateItem(Item item, boolean empty) {
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
        private TextField createTextField() {
            TextField node = new TextField(getItem().getName());
            node.setOnKeyReleased(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    commitEdit(getItem());
                    event.consume();
                } else if (event.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                    event.consume();
                }
            });
            return node;
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
    private final Interactions interactions;
}
