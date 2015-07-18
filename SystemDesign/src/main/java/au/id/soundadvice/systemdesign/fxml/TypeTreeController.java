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
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget;
import au.id.soundadvice.systemdesign.fxml.DropHandlers.FunctionDropHandler;
import au.id.soundadvice.systemdesign.fxml.drag.DragSource;
import au.id.soundadvice.systemdesign.model.FlowType;
import java.util.Optional;
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
public class TypeTreeController {

    public TypeTreeController(
            Interactions interactions, EditState edit,
            TreeView<FlowType> view) {
        this.interactions = interactions;
        this.edit = edit;
        this.view = view;
        this.updateView = new SingleRunnable<>(JFXExecutor.instance(), new UpdateView());
        this.functionCreator = new FunctionCreator(edit);
    }

    public void start() {
        addContextMenu();
        edit.subscribe(updateView);
        updateView.run();
    }

    public void stop() {
        edit.unsubscribe(updateView);
    }

    private void addContextMenu() {
    }

    private class UpdateView implements Runnable {

        @Override
        public void run() {
            Baseline allocated = edit.getUndo().get().getAllocated();
            TreeItem root = new TreeItem();
            root.setExpanded(true);
            root.getChildren().addAll(
                    FlowType.find(allocated)
                    .sorted((left, right) -> left.getName().compareTo(right.getName()))
                    .map(flowType -> {
                        TreeItem parent = new TreeItem(flowType);
                        parent.setExpanded(true);
                        return parent;
                    }).toArray());
            view.setRoot(root);
            view.setShowRoot(false);
            view.setEditable(true);
            view.setCellFactory(view -> {
                FunctionTreeCell cell = new FunctionTreeCell();
                cell.start();
                return cell;
            });
        }
    }

    private final class FunctionTreeCell extends TreeCell<FlowType> {

        private Optional<TextField> textField = Optional.empty();
        private final ContextMenu contextMenu = new ContextMenu();

        public FunctionTreeCell() {
            MenuItem deleteMenuItem = new MenuItem("Delete Flow Type");
            contextMenu.getItems().add(deleteMenuItem);
            deleteMenuItem.setOnAction(event -> {
                edit.updateAllocated(baseline -> {
                    return getItem().removeFrom(baseline);
                });
                event.consume();
            });
            this.editableProperty().bind(this.itemProperty().isNotNull());
        }

        public void start() {
            DragSource.bind(this, () -> Optional.ofNullable(getItem()), false);
            DragTarget.bind(edit, this, () -> Optional.ofNullable(getItem()),
                    new FunctionDropHandler(interactions, edit));
        }

        @Override
        public void startEdit() {
            FlowType function = getItem();
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
                    commitEdit(getItem());
                }
            }
        }

        @Override
        public void commitEdit(FlowType function) {
            edit.updateAllocated(allocated -> {
                Optional<FlowType> current = allocated.get(function);
                if (current.isPresent()) {
                    Baseline.BaselineAnd<FlowType> result = current.get().setName(
                            allocated, textField.get().getText());
                    super.commitEdit(result.getRelation());
                    return result.getBaseline();
                } else {
                    return allocated;
                }
            });
        }

        @Override
        public void updateItem(FlowType function, boolean empty) {
            super.updateItem(function, empty);

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
        private TextField createTextField(FlowType function) {
            TextField node = new TextField(function.getName());
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
            return getItem() == null ? "(unallocated)" : getItem().toString();
        }
    }
    private final Interactions interactions;
    private final EditState edit;
    private final TreeView<FlowType> view;
    private final SingleRunnable<UpdateView> updateView;
    private final FunctionCreator functionCreator;
}
