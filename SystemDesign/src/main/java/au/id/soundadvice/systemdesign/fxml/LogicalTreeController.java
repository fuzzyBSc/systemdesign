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
import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.files.Identifiable;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.fxml.DropHandlers.FunctionDropHandler;
import au.id.soundadvice.systemdesign.fxml.drag.DragSource;
import au.id.soundadvice.systemdesign.model.Item;
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
        view.setContextMenu(ContextMenus.logicalTreeBackgroundMenu(functionCreator));
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
            Optional<Item> systemOfInterest = state.getSystemOfInterest();
            Baseline functional = state.getFunctional();
            Baseline allocated = state.getAllocated();

            Map<UUID, Function> parentFunctions;
            if (systemOfInterest.isPresent()) {
                parentFunctions = systemOfInterest.get().findOwnedFunctions(functional)
                        .collect(Identifiable.toMap());
            } else {
                parentFunctions = Collections.emptyMap();
            }

            Map<Function, SortedMap<String, Function>> rawAllocation = new HashMap<>();
            parentFunctions.values()
                    .forEach(parent -> rawAllocation.put(parent, new TreeMap<>()));
            // Keep orphans separate to avoid null pointers in TreeMap
            SortedMap<String, Function> rawOrphans = new TreeMap<>();
            Function.find(allocated)
                    .filter(function -> !function.isExternal())
                    .forEach(child -> {
                        // Parent may be null, ie child is orphaned
                        Optional<Function> trace = child.getTrace(functional);
                        SortedMap<String, Function> map = trace.isPresent()
                                ? rawAllocation.get(trace.get()) : rawOrphans;
                        map.put(child.getDisplayName(allocated), child);
                    });

            SortedMap<String, FunctionAllocation> tmpAllocation = new TreeMap<>();
            rawAllocation.entrySet().stream()
                    .map(entry -> new FunctionAllocation(Optional.of(entry.getKey()), Collections.unmodifiableSortedMap(entry.getValue())))
                    .forEach(alloc -> {
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
            TreeState newState = new TreeState(edit.getState());
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
                    .filter(allocation -> allocation.parent.isPresent())
                    .map(allocation -> {
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
                FunctionTreeCell cell = new FunctionTreeCell();
                cell.start();
                return cell;
            });
        }
    }

    private final class FunctionTreeCell extends TreeCell<Function> {

        private Optional<TextField> textField = Optional.empty();
        private final ContextMenu contextMenu = new ContextMenu();

        public FunctionTreeCell() {
            MenuItem addMenuItem = new MenuItem("Add Function");
            contextMenu.getItems().add(addMenuItem);
            addMenuItem.setOnAction(event -> {
                functionCreator.add(getItem());
                event.consume();
            });
            MenuItem deleteMenuItem = new MenuItem("Delete Function");
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
            } else /*
             * If the cancelEdit is due to a loss of focus, override it. Commit
             * instead.
             */ if (textField.isPresent()) {
                commitEdit(getItem());
            }
        }

        @Override
        public void commitEdit(Function function) {
            edit.updateAllocated(allocated -> {
                Optional<Function> current = allocated.get(function);
                if (current.isPresent()) {
                    Baseline.BaselineAnd<Function> result = current.get().setName(
                            allocated, textField.get().getText());
                    super.commitEdit(result.getRelation());
                    return result.getBaseline();
                } else {
                    return allocated;
                }
            });
        }

        @Override
        public void updateItem(Function function, boolean empty) {
            super.updateItem(function, empty);

            if (empty) {
                setText(null);
                setGraphic(null);
            } else if (isEditing()) {
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

        @CheckReturnValue
        private TextField createTextField(Function function) {
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
    private final TreeView<Function> view;
    private final AtomicReference<TreeState> treeState = new AtomicReference<>();
    private final SingleRunnable<Changed> changed;
    private final SingleRunnable<UpdateView> updateView;
    private final FunctionCreator functionCreator;
}
