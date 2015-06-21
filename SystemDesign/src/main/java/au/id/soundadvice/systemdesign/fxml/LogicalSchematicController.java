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
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.fxml.drag.MoveHandler.Dragged;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.fxml.DropHandlers.FunctionDropHandler;
import au.id.soundadvice.systemdesign.fxml.drag.DragSource;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget;
import au.id.soundadvice.systemdesign.fxml.drag.GridSnap;
import au.id.soundadvice.systemdesign.fxml.drag.MoveHandler;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.DirectedPair;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Polygon;
import javax.annotation.Nullable;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
class LogicalSchematicController {

    private static final double root2 = Math.sqrt(2);

    private final EditState edit;
    private final Interactions interactions;
    private final Tab tab;
    @Nullable
    private final Function parentFunction;
    private final TabPane tabs;
    private final SingleRunnable onChange = new SingleRunnable(
            JFXExecutor.instance(), new OnChange());
    private final AtomicBoolean started = new AtomicBoolean(false);

    LogicalSchematicController(
            Interactions interactions, EditState edit,
            TabPane tabs,
            @Nullable Function parentFunction) {
        this.edit = edit;
        this.interactions = interactions;
        this.tab = new Tab();
        this.tabs = tabs;
        this.parentFunction = parentFunction;
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            tabs.getTabs().add(tab);
            edit.subscribe(onChange);
            onChange.run();
        }
    }

    public void stop() {
        if (started.compareAndSet(true, false)) {
            tabs.getTabs().remove(tab);
            edit.unsubscribe(onChange);
        }
    }

    private Node toNode(Function function, Item item) {
        Label label = new Label(
                function.getName() + '\n'
                + '(' + item.getDisplayName() + ')');
        label.getStyleClass().add("text");

        Ellipse ellipse = new Ellipse();
        ellipse.getStyleClass().add("outline");

        ellipse.setCenterX(0);
        ellipse.setCenterY(0);

        int insets = 5;

        label.boundsInLocalProperty().addListener((observable, oldValue, newValue) -> {
            double halfWidth = newValue.getWidth() / 2;
            double halfHeight = newValue.getHeight() / 2;
            label.setLayoutX(-halfWidth);
            label.setLayoutY(-halfHeight);
            // Calculate the relevant radii of the ellipse while maintaining
            // aspect ratio.
            // Thanks: http://stackoverflow.com/questions/433371/ellipse-bounding-a-rectangle
            ellipse.setRadiusX((halfWidth + insets) * root2);
            ellipse.setRadiusY((halfHeight + insets) * root2);
        });

        Group group = new Group(ellipse, label);
        group.getStyleClass().add("schematicFunction");
        if (function.isExternal()) {
            group.getStyleClass().add("external");
        }
        group.setLayoutX(function.getOrigin().getX());
        group.setLayoutY(function.getOrigin().getY());

        ContextMenu contextMenu = new ContextMenu();
        if (function.isExternal()) {
            MenuItem deleteMenuItem = new MenuItem("Delete External Function");
            deleteMenuItem.setOnAction(event -> {
                edit.remove(function.getUuid());
                event.consume();
            });
            contextMenu.getItems().add(deleteMenuItem);
        } else {
            group.setOnMouseClicked(event -> {
                if (event.getClickCount() > 1) {
                    interactions.navigateDown(item);
                    event.consume();
                }
            });
            MenuItem navigateMenuItem = new MenuItem("Navigate Down");
            navigateMenuItem.setOnAction(event -> {
                interactions.navigateDown(item);
                event.consume();
            });
            contextMenu.getItems().add(navigateMenuItem);
            MenuItem addMenuItem = new MenuItem("Rename Function");
            addMenuItem.setOnAction(event -> {
                interactions.rename(function);
                event.consume();
            });
            contextMenu.getItems().add(addMenuItem);
            MenuItem deleteMenuItem = new MenuItem("Delete Function");
            deleteMenuItem.setOnAction(event -> {
                edit.remove(function.getUuid());
                event.consume();
            });
            contextMenu.getItems().add(deleteMenuItem);
        }
        label.setContextMenu(contextMenu);

        return group;
    }

    private Node toNode(
            Flow flow, Direction direction,
            double radiusX, double radiusY, boolean negate) {
        int startDegrees;
        if (negate) {
            startDegrees = 0;
        } else {
            startDegrees = 180;
        }

        Arc path = new Arc(0, 0, radiusX, radiusY, startDegrees, 180);
        path.getStyleClass().add("path");

        Polygon normalArrow = new Polygon(
                5, -5,
                15, 0,
                5, 5);
        normalArrow.getStyleClass().add("arrow");
        Polygon reverseArrow = new Polygon(
                -5, -5,
                -15, 0,
                -5, 5);
        reverseArrow.getStyleClass().add("arrow");

        switch (direction) {
            case Normal:
                reverseArrow.setVisible(false);
                break;
            case Reverse:
                normalArrow.setVisible(false);
                break;
            case Both:
                // Leave both visible
                break;
            default:
                throw new AssertionError(direction.name());

        }

        Label label = new Label(flow.getType());
        label.boundsInLocalProperty().addListener((info, old, bounds) -> {
            double halfWidth = bounds.getWidth() / 2;
            double halfHeight = bounds.getHeight() / 2;
            label.setLayoutX(-halfWidth);
            normalArrow.setLayoutX(halfWidth);
            reverseArrow.setLayoutX(-halfWidth);
            double layoutY = negate ? -radiusY : radiusY;
            label.setLayoutY(-halfHeight + layoutY);
            normalArrow.setLayoutY(layoutY);
            reverseArrow.setLayoutY(layoutY);
        });
        label.getStyleClass().add("text");

        ContextMenu contextMenu = new ContextMenu();
        MenuItem typeMenuItem = new MenuItem("Set Type");
        typeMenuItem.setOnAction((event) -> {
            interactions.setFlowType(flow);
            event.consume();
        });
        contextMenu.getItems().add(typeMenuItem);
        MenuItem deleteMenuItem = new MenuItem("Delete");
        deleteMenuItem.setOnAction((event) -> {
            edit.remove(flow.getUuid());
            event.consume();
        });
        contextMenu.getItems().add(deleteMenuItem);
        label.setContextMenu(contextMenu);

        Group group = new Group(path, label, normalArrow, reverseArrow);
        group.getStyleClass().add("schematicFlow");
        return group;
    }

    private Node toNode(Function left, Function right, List<Flow> flows) {
        Point2D leftOrigin = left.getOrigin();
        Point2D rightOrigin = right.getOrigin();
        boolean reverseDirection = false;

        if (leftOrigin.getX() > rightOrigin.getX()) {
            // Flip orientation
            Point2D tmp = leftOrigin;
            leftOrigin = rightOrigin;
            rightOrigin = tmp;
            reverseDirection = true;
        }
        Point2D midpoint = leftOrigin.midpoint(rightOrigin);

        Point2D zeroDegrees = new Point2D(1, 0);
        Point2D vector = new Point2D(
                rightOrigin.getX() - leftOrigin.getX(),
                rightOrigin.getY() - leftOrigin.getY());
        double theta = zeroDegrees.angle(vector);
        if (leftOrigin.getY() > rightOrigin.getY()) {
            theta = -theta;
        }

        Group group = new Group();
        boolean negate = true;
        double radiusX = vector.magnitude() / 2;
        double radiusY = 0;
        for (int ii = 0; ii < flows.size(); ++ii) {
            Flow flow = flows.get(ii);
            Direction direction = flow.getDirection();
            if (reverseDirection) {
                direction = direction.reverse();
            }
            Node node = toNode(flow, direction, radiusX, radiusY, negate);
            group.getChildren().add(node);
            if (negate) {
                radiusY += 30;
                negate = false;
            } else {
                negate = true;
            }
        }
        group.setRotate(theta);
        group.setLayoutX(midpoint.getX());
        group.setLayoutY(midpoint.getY());

        return group;
    }

    public void populate(
            AllocatedBaseline allocated,
            Map<UUID, Function> functions,
            Map<DirectedPair, List<Flow>> flows) {
        Pane pane = new AnchorPane();
        flows.entrySet().forEach(entry -> {
            Function left = functions.get(entry.getKey().getLeft());
            Function right = functions.get(entry.getKey().getRight());
            if (left != null && right != null) {
                Node node = toNode(left, right, entry.getValue());
                pane.getChildren().add(node);
            }
        });
        functions.values().forEach(function -> {
            Item item = function.getItem().getTarget(allocated.getStore());
            Node node = toNode(function, item);

            new MoveHandler(pane, node, new Move(function.getUuid()),
                    new GridSnap(10),
                    event -> MouseButton.PRIMARY.equals(event.getButton())
                    && !event.isControlDown()).start();
            DragSource.bind(node, function, true);
            DragTarget.bind(edit, node, function,
                    new FunctionDropHandler(interactions, edit));
            pane.getChildren().add(node);
        });
        tab.setContent(pane);
    }

    private class Move implements Dragged {

        public Move(UUID uuid) {
            this.uuid = uuid;
        }
        private final UUID uuid;

        @Override
        public void dragged(Node parent, Node draggable, Point2D layoutCurrent) {
            edit.getUndo().update(state -> {
                Function toAdd = state.getAllocatedInstance(uuid, Function.class);
                if (toAdd == null) {
                    return state;
                }
                toAdd = toAdd.setOrigin(layoutCurrent);
                return state.setAllocated(state.getAllocated().add(toAdd));
            });
        }

    }

    private class OnChange implements Runnable {

        @Override
        public void run() {
            String name;
            if (parentFunction == null) {
                name = "Logical";
            } else {
                name = parentFunction.getName();
            }
            tab.setText(name);

        }
    }
}
