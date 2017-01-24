/*
 * To change this license header, choose License Headers in Project Properties.
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
package au.id.soundadvice.systemdesign.fxml.tree;

import au.id.soundadvice.systemdesign.fxml.ContextMenus;
import au.id.soundadvice.systemdesign.fxml.Interactions;
import au.id.soundadvice.systemdesign.fxml.drag.DragSource;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget;
import au.id.soundadvice.systemdesign.fxml.drawing.DrawingOf;
import au.id.soundadvice.systemdesign.fxml.drag.EntityDropHandler;
import au.id.soundadvice.systemdesign.moduleapi.interaction.MenuItems;
import au.id.soundadvice.systemdesign.moduleapi.tree.Tree;
import au.id.soundadvice.systemdesign.moduleapi.tree.TreeNode;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
import javafx.scene.control.Accordion;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FXMLTree implements DrawingOf<Tree> {

    static final Point2D DEFAULT_ORIGIN = new Point2D(200, 200);

    public FXMLTree(
            Interactions interactions, ContextMenus menus,
            Accordion tabs,
            TitledPane tab,
            TreeView<TreeNode> view) {
        this.interactions = interactions;
        this.menus = menus;
        this.tabs = tabs;
        this.tab = tab;
        this.view = view;
    }

    @Override
    public void start() {
        tabs.getPanes().add(tab);
    }

    @Override
    public void stop() {
        tabs.getPanes().remove(tab);
    }

    private void addContextMenu(Tree tree) {
        Optional<MenuItems> menu = tree.getContextMenu();
        if (menu.isPresent()) {
            view.setContextMenu(menus.getMenu(menu.get()));
        } else {
            view.setContextMenu(null);
        }
    }

    private void updateTreeItem(TreeItem<TreeNode> target, Stream<TreeNode> values) {
        target.getChildren().clear();
        target.getChildren().addAll(
                values.map(node -> {
                    TreeItem<TreeNode> result = new TreeItem<>(node);
                    result.setExpanded(true);
                    updateTreeItem(result, node.getChildren());
                    return result;
                })
                .toArray(TreeItem[]::new));
    }

    @Override
    public void setState(Tree state) {
        TreeItem<TreeNode> root = new TreeItem<>();
        root.setExpanded(true);
        updateTreeItem(root, state.getChildren());
        view.setRoot(root);
        view.setShowRoot(false);
        view.setEditable(true);
        view.setCellFactory(value -> {
            TreeNodeCell cell = new TreeNodeCell();
            DragSource.bind(cell, () -> Optional.ofNullable(cell.getItem()), false);
            DragTarget.bind(interactions, cell, () -> Optional.ofNullable(cell.getItem()),
                    new EntityDropHandler(interactions));
            return cell;
        });
        addContextMenu(state);
    }

    private final class TreeNodeCell extends TreeCell<TreeNode> {

        private Optional<TextField> textField = Optional.empty();

        public TreeNodeCell() {
            this.editableProperty().bind(this.itemProperty().isNotNull());
        }

        @Override
        public void startEdit() {
            TreeNode node = getItem();
            if (node != null) {
                super.startEdit();

                if (!textField.isPresent()) {
                    createTextField(getItem());
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
            } else if (textField.isPresent()) {
                /*
                 * If the cancelEdit is due to a loss of focus, override it.
                 * Commit instead.
                 */
                commitEdit(getItem());
            }
        }

        @Override
        public void commitEdit(TreeNode node) {
            interactions.updateState(baselines -> node.setLabel(
                    baselines, ISO8601.now(), textField.get().getText()));
        }

        @Override
        public void updateItem(TreeNode node, boolean empty) {
            super.updateItem(node, empty);

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
                addContextMenu(node);
            }
        }

        private void addContextMenu(TreeNode node) {
            Optional<MenuItems> menu = node.getContextMenu();
            if (menu.isPresent()) {
                setContextMenu(menus.getMenu(menu.get()));
            } else {
                setContextMenu(null);
            }
        }

        private void createTextField(TreeNode treeNode) {
            textField = Optional.of(new TextField(treeNode.toString()));
            textField.get().setOnKeyReleased(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    commitEdit(getItem());
                    event.consume();
                } else if (event.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                    event.consume();
                }
            });
        }

        private String getString() {
            TreeNode tmp = getItem();
            return tmp == null ? "(unallocated)" : tmp.toString();
        }
    }
    private final Interactions interactions;
    private final ContextMenus menus;
    private final Accordion tabs;
    private final TitledPane tab;
    private final TreeView<TreeNode> view;
}
