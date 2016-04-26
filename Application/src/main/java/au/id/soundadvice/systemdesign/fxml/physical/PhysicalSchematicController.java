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
package au.id.soundadvice.systemdesign.fxml.physical;

import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.fxml.ContextMenus;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget;
import au.id.soundadvice.systemdesign.fxml.drag.MoveHandler;
import au.id.soundadvice.systemdesign.fxml.drag.MoveHandler.Dragged;
import au.id.soundadvice.systemdesign.fxml.drag.GridSnap;
import au.id.soundadvice.systemdesign.physical.Interface;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.fxml.DropHandlers.ItemDropHandler;
import au.id.soundadvice.systemdesign.fxml.Interactions;
import au.id.soundadvice.systemdesign.fxml.drag.DragSource;
import au.id.soundadvice.systemdesign.logical.Function;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.physical.ItemView;
import au.id.soundadvice.systemdesign.state.RelationDiff;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalSchematicController {

    private final EditState edit;
    private final Tab tab;
    private final Pane pane;
    private final SingleRunnable onChange = new SingleRunnable(
            JFXExecutor.instance(), new OnChange());
    private final Interactions interactions;

    public PhysicalSchematicController(
            Interactions interactions, EditState edit,
            Tab tab) {
        this.edit = edit;
        this.tab = tab;
        ScrollPane scrollPane = (ScrollPane) tab.getContent();
        this.pane = (Pane) scrollPane.getContent();
        this.interactions = interactions;

        scrollPane.viewportBoundsProperty().addListener((info, old, bounds) -> {
            pane.setMinWidth(bounds.getWidth());
            pane.setMinHeight(bounds.getHeight());
        });
    }

    public void start() {
        edit.subscribe(onChange);
        onChange.run();

        ContextMenu contextMenu = new ContextMenu();
        MenuItem addMenuItem = new MenuItem("Add Item");
        contextMenu.getItems().add(addMenuItem);
        AtomicReference<Optional<ContextMenuEvent>> lastContextMenuClick
                = new AtomicReference<>(Optional.empty());
        addMenuItem.setOnAction(event -> {
            Point2D origin = lastContextMenuClick.get()
                    .map(evt -> new Point2D(evt.getX(), evt.getY()))
                    .orElse(ItemView.DEFAULT_ORIGIN);
            interactions.createItem(origin);
            event.consume();
        });
        pane.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            lastContextMenuClick.set(Optional.of(event));
            contextMenu.show(pane, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        pane.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            contextMenu.hide();
        });
    }

    private class OnChange implements Runnable {

        @Override
        public void run() {
            pane.getChildren().clear();
            Optional<Relations> optionalWasBaseline = edit.getDiffBaseline();
            Relations isBaseline = edit.getAllocated();
            Identity identity = Identity.find(isBaseline);
            if (identity.getIdPath().isEmpty()) {
                tab.setText("Physical");
            } else {
                tab.setText(identity.toString());
            }
            // Put deleted items behind other items
            Group deleted = new Group();
            Group other = new Group();

            if (optionalWasBaseline.isPresent()) {
                // Show deleted items and interfaces.
                // Make sure these are added first, and thus are underneath.
                Relations wasBaseline = optionalWasBaseline.get();

                Interface.find(wasBaseline)
                        .map(sample -> RelationDiff.get(optionalWasBaseline, isBaseline, sample))
                        .filter(RelationDiff::isDeleted)
                        .forEach(diff -> {
                            addInterfaceNode(deleted, diff);
                        });
                Item.find(wasBaseline)
                        .map(sample -> RelationDiff.get(optionalWasBaseline, isBaseline, sample))
                        .filter(RelationDiff::isDeleted)
                        .forEach(diff -> {
                            addItemNode(deleted, diff);
                        });
            }
            // Now add the reguilar instances
            Interface.find(isBaseline)
                    .map(sample -> RelationDiff.get(optionalWasBaseline, isBaseline, sample))
                    .forEach(diff -> {
                        addInterfaceNode(other, diff);
                    });
            Item.find(isBaseline)
                    .map(sample -> RelationDiff.get(optionalWasBaseline, isBaseline, sample))
                    .forEach(diff -> {
                        addItemNode(other, diff);
                    });

            pane.getChildren().add(deleted);
            pane.getChildren().add(other);
        }

        private void addInterfaceNode(
                Group parent, RelationDiff<Interface> diff) {
            /*
             * Note we have to be careful about which baseline we mean. We want
             * to draw the interface to the "is" baseline ItemView if it still
             * exists.
             */
            Interface iface = diff.getSample();
            Optional<Relations> was = diff.getWasBaseline();
            Relations is = diff.getIsBaseline();
            Item left = RelationDiff.get(was, is, iface.getLeft().getKey(), Item.class)
                    .getSample();
            Item right = RelationDiff.get(was, is, iface.getRight().getKey(), Item.class)
                    .getSample();
            ItemView leftView = left.findViews(is).findAny()
                    .orElseGet(() -> left.getView(was.get()));
            ItemView rightView = right.findViews(is).findAny()
                    .orElseGet(() -> right.getView(was.get()));

            Line line = new Line(
                    leftView.getOrigin().getX(),
                    leftView.getOrigin().getY(),
                    rightView.getOrigin().getX(),
                    rightView.getOrigin().getY());

            TextFlow flow = new TextFlow();
            if (diff.isAdded()) {
                flow.getChildren().add(new Text("added\n"));
                flow.getStyleClass().add("changed");
            } else if (diff.isDeleted()) {
                flow.getChildren().add(new Text("deleted\n"));
                flow.getStyleClass().add("deleted");
            }
            String id = diff.getIsInstance()
                    .map(sample -> sample.getLongID(diff.getIsBaseline()))
                    .orElseGet(() -> diff.getWasInstance().get().getLongID(diff.getWasBaseline().get()));
            flow.getChildren().add(new Text(id));
            Label label = new Label(null, flow);
            Point2D midpoint = leftView.getOrigin().midpoint(rightView.getOrigin());
            label.setLayoutX(midpoint.getX());
            label.setLayoutY(midpoint.getY());

            if (diff.isDeleted()) {
                ContextMenu contextMenu = ContextMenus.deletedInterfaceContextMenu(
                        diff.getWasBaseline().get(), iface,
                        interactions, edit);
                label.setContextMenu(contextMenu);
            } else {
                ContextMenu contextMenu = ContextMenus.interfaceContextMenu(
                        iface, interactions, edit);
                label.setContextMenu(contextMenu);
            }

            Group result = new Group();
            result.getStyleClass().add("schematicInterface");

            result.getChildren().addAll(line, label);
            parent.getChildren().add(result);
        }

        private void addItemNode(Group parent, RelationDiff<Item> diff) {
            Optional<Relations> was = diff.getWasBaseline();
            Relations is = diff.getIsBaseline();
            Item item = diff.getSample();
            ItemView view = item.findViews(is).findAny()
                    .orElseGet(() -> item.getView(was.get()));
            TextFlow flow = new TextFlow();
            if (diff.isAdded()) {
                flow.getChildren().add(new Text("added\n"));
            } else if (diff.isDeleted()) {
                flow.getChildren().add(new Text("deleted\n"));
            }
            Optional<String> oldName = diff.getWasInstance()
                    .map(sample -> sample.getDisplayName());
            Optional<String> newName = diff.getIsInstance()
                    .map(sample -> sample.getDisplayName());
            if (oldName.isPresent() && newName.isPresent()
                    && !oldName.equals(newName)) {
                Text oldText = new Text(oldName.get() + '\n');
                oldText.getStyleClass().add("deleted");
                Text newText = new Text(newName.get());
                newText.getStyleClass().add("changed");
                flow.getChildren().addAll(oldText, newText);
            } else {
                flow.getChildren().add(new Text(newName.orElseGet(() -> oldName.get())));
            }
            if (was.isPresent()) {
                // Add deleted functions
                flow.getChildren().addAll(
                        was.get()
                        .findReverse(item.getIdentifier(), Function.class)
                        .map(function -> RelationDiff.get(was, is, function))
                        .filter(RelationDiff::isDeleted)
                        .map(functionDiff -> {
                            Text text = new Text(
                                    "\n+ " + functionDiff.getWasInstance().get().getName());
                            text.getStyleClass().add("deleted");
                            return text;
                        })
                        .collect(Collectors.toList()));
            }
            // Add remainder
            flow.getChildren().addAll(
                    is
                    .findReverse(item.getIdentifier(), Function.class)
                    .map(function -> RelationDiff.get(was, is, function))
                    .flatMap(functionDiff -> {
                        Optional<String> wasName = functionDiff.getWasInstance()
                                .map(Function::getName);
                        String isName = functionDiff.getIsInstance().get().getName();
                        if (wasName.isPresent() && !isName.equals(wasName.get())) {
                            Text wasText = new Text("\n+ " + wasName.get());
                            wasText.getStyleClass().add("deleted");
                            Text isText = new Text("\n+ " + isName);
                            isText.getStyleClass().add("changed");
                            return Stream.of(wasText, isText);
                        } else {
                            Text isText = new Text(
                                    "\n+ " + functionDiff.getIsInstance().get().getName());
                            if (functionDiff.isDiff()) {
                                if (!wasName.isPresent() || !isName.equals(wasName.get())) {
                                    isText.getStyleClass().add("changed");
                                }
                            }
                            return Stream.of(isText);
                        }
                    })
                    .collect(Collectors.toList()));
            Label label = new Label(null, flow);

            Rectangle rectangle = new Rectangle();
            rectangle.getStyleClass().add("outline");
            rectangle.setFill(view.getColor());

            int insets = 5;

            label.boundsInLocalProperty().addListener((observable, oldValue, newValue) -> {
                double halfWidth = Math.ceil(newValue.getWidth() / 2);
                double halfHeight = Math.ceil(newValue.getHeight() / 2);
                label.setLayoutX(-halfWidth);
                label.setLayoutY(-halfHeight);
                rectangle.setLayoutX(-halfWidth - insets);
                rectangle.setLayoutY(-halfHeight - insets);
                rectangle.setWidth((halfWidth + insets) * 2);
                rectangle.setHeight((halfHeight + insets) * 2);

            });

            Group group = new Group(rectangle, label);
            group.getStyleClass().add("schematicItem");
            if (diff.isDeleted()) {
                group.getStyleClass().add("deleted");
            } else if (diff.isAdded() || diff.isChanged()) {
                group.getStyleClass().add("changed");
            }
            if (item.isExternal()) {
                group.getStyleClass().add("external");
            }
            group.setLayoutX(view.getOrigin().getX());
            group.setLayoutY(view.getOrigin().getY());

            if (diff.isDeleted()) {
                ContextMenu contextMenu = ContextMenus.deletedItemContextMenu(
                        diff.getWasBaseline().get(), item,
                        interactions, edit);
                label.setContextMenu(contextMenu);
            } else {
                ContextMenu contextMenu = ContextMenus.itemContextMenu(item, interactions, edit);
                label.setContextMenu(contextMenu);

                if (!item.isExternal()) {
                    group.setOnMouseClicked(event -> {
                        if (event.getClickCount() > 1) {
                            interactions.navigateDown(item);
                            event.consume();
                        }
                    });
                }

                new MoveHandler(parent, group,
                        new MoveItem(view),
                        new GridSnap(10), (MouseEvent event)
                        -> MouseButton.PRIMARY.equals(event.getButton())
                        && !event.isControlDown()).start();
                DragSource.bind(group, item, true);
                DragTarget.bind(edit, group, item,
                        new ItemDropHandler(interactions));
            }

            parent.getChildren().add(group);
        }

    }

    private class MoveItem implements Dragged {

        public MoveItem(ItemView view) {
            this.view = view;
        }

        private final ItemView view;

        @Override
        public void dragged(Node parent, Node draggable, Point2D layoutCurrent) {
            edit.updateAllocated(allocated -> {
                Optional<ItemView> current = allocated.get(view);
                if (current.isPresent()) {
                    return current.get().setOrigin(allocated, layoutCurrent).getKey();
                } else {
                    return allocated;
                }
            });
        }
    }

}
