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
import au.id.soundadvice.systemdesign.baselines.UndoState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.fxml.drag.ConnectHandler;
import au.id.soundadvice.systemdesign.fxml.drag.ConnectHandler.Connect;
import au.id.soundadvice.systemdesign.fxml.drag.DragHandler;
import au.id.soundadvice.systemdesign.fxml.drag.DragHandler.Dragged;
import au.id.soundadvice.systemdesign.fxml.drag.GridSnap;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.relation.RelationContext;
import au.id.soundadvice.systemdesign.undo.UndoBuffer;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Line;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalSchematicController {

    private final EditState edit;
    private final UndoBuffer<UndoState> undo;
    private final Pane drawing;
    private final SingleRunnable onChange = new SingleRunnable(
            JFXExecutor.instance(), new OnChange());
    private final Interactions interactions;

    PhysicalSchematicController(EditState edit, Pane drawing) {
        this.edit = edit;
        this.undo = edit.getUndo();
        this.drawing = drawing;
        this.interactions = new Interactions(edit);
    }

    void start() {
        undo.getChanged().subscribe(onChange);
        onChange.run();
    }

    private Group toNode(Item item, Collection<? extends Function> functions) {
        StringBuilder builder = new StringBuilder();
        builder.append(item);
        String functionsText = functions.stream()
                .map((function) -> function.getDisplayName())
                .sorted()
                .collect(Collectors.joining("\n+"));
        if (!functionsText.isEmpty()) {
            builder.append("\n+");
            builder.append(functionsText);
        }

        Label label = new Label(builder.toString());
        label.getStyleClass().add("text");

        Rectangle rectangle = new Rectangle();
        rectangle.getStyleClass().add("outline");
        // Why does this not work from the CSS??
//        rectangle.getStrokeDashArray().addAll(12d, 2d, 4d, 2d);
//        rectangle.setStyle("-fx-stroke-dash-array: 12 2 4 2");

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
        group.getStyleClass().add("schematicFunction");
        group.setLayoutX(item.getOrigin().getX());
        group.setLayoutY(item.getOrigin().getY());

        return group;
    }

    private class OnChange implements Runnable {

        @Override
        public void run() {
            drawing.getChildren().clear();
            AllocatedBaseline baseline = undo.get().getAllocated();
            baseline.getInterfaces().stream().forEach((iface) -> {
                addNode(drawing, baseline, iface);
            });
            baseline.getItems().stream().forEach((item) -> {
                addNode(drawing, baseline, item);
            });
        }

        private void addNode(Pane parent, AllocatedBaseline baseline, Interface iface) {
            Item left = iface.getLeft().getTarget(baseline.getStore());
            Item right = iface.getRight().getTarget(baseline.getStore());
            Line result = new Line(
                    left.getOrigin().getX(),
                    left.getOrigin().getY(),
                    right.getOrigin().getX(),
                    right.getOrigin().getY());
            result.getStyleClass().add("schematicInterface");
            parent.getChildren().add(result);
        }

        private void addNode(Pane parent, AllocatedBaseline baseline, Item item) {
            Group result = toNode(
                    item,
                    baseline.getStore().getReverse(item.getUuid(), Function.class));
            result.getStyleClass().add("schematicItem");
            if (item.isExternal()) {
                result.getStyleClass().add("external");
            }

            if (!item.isExternal()) {
                result.setOnMouseClicked((MouseEvent ev) -> {
                    if (ev.getClickCount() > 1) {
                        // Navigate down
                        if (SaveHelper.checkSave(parent.getScene().getWindow(), edit, "Save before navigating?")) {
                            try {
                                UndoState state = undo.get();
                                edit.loadChild(item.asIdentity(state.getAllocated().getStore()));
                            } catch (IOException ex) {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("Load Failed");
                                alert.setHeaderText("Load Failed");
                                alert.setContentText(ex.toString());

                                alert.showAndWait();
                            }
                        }
                        ev.consume();
                    }
                });
                ContextMenu contextMenu = new ContextMenu();
                MenuItem addMenuItem = new MenuItem("Add Function");
                contextMenu.getItems().add(addMenuItem);
                addMenuItem.setOnAction(event -> interactions.addFunctionToItem(item));
                result.getChildren().stream()
                        .filter((member) -> (member instanceof Control))
                        .forEach((member) -> ((Control) member).setContextMenu(contextMenu));
            }
            new DragHandler(parent, result, new MoveItem(item),
                    new GridSnap(10), (MouseEvent event)
                    -> MouseButton.PRIMARY.equals(event.getButton())
                    && !event.isControlDown()).start();
            ConnectHandler.register(result, item, new ConnectItems(),
                    (MouseEvent event)
                    -> MouseButton.PRIMARY.equals(event.getButton())
                    && event.isControlDown());

            parent.getChildren().add(result);
        }
    }

    private class ConnectItems implements Connect {

        @Override
        public boolean canConnect(UUID sourceId, UUID targetId) {
            RelationContext store = undo.get().getAllocated().getStore();
            try {
                Item source = store.get(sourceId, Item.class);
                Item target = store.get(targetId, Item.class);
                return source != null && target != null;
            } catch (ClassCastException ex) {
                return false;
            }
        }

        @Override
        public void connect(UUID sourceId, UUID targetId) {
            UndoState state = undo.get();
            AllocatedBaseline baseline = state.getAllocated();
            Interface newInterface = new Interface(sourceId, targetId);
            if (baseline.getStore().getReverse(sourceId, Interface.class).parallelStream()
                    .noneMatch((existing) -> newInterface.isRedundantTo(existing))) {
                undo.set(state.setAllocated(baseline.add(newInterface)));
            }
        }

    }

    private class MoveItem implements Dragged {

        public MoveItem(Item item) {
            this.item = item;
        }

        private final Item item;

        @Override
        public void dragged(Node parent, Node draggable, Point2D layoutCurrent) {
            UndoState state = undo.get();
            undo.set(state.setAllocated(state.getAllocated().add(item.setOrigin(layoutCurrent))));
        }
    }

}
