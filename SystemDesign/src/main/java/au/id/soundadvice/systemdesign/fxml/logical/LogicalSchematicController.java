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
package au.id.soundadvice.systemdesign.fxml.logical;

import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.fxml.drag.MoveHandler.Dragged;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.beans.Direction;
import au.id.soundadvice.systemdesign.fxml.ContextMenus;
import au.id.soundadvice.systemdesign.fxml.DropHandlers.FunctionDropHandler;
import au.id.soundadvice.systemdesign.fxml.DropHandlers.LogicalSchematicBackgroundDropHandler;
import au.id.soundadvice.systemdesign.fxml.Interactions;
import au.id.soundadvice.systemdesign.fxml.drag.DragSource;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget;
import au.id.soundadvice.systemdesign.fxml.drag.GridSnap;
import au.id.soundadvice.systemdesign.fxml.drag.MoveHandler;
import au.id.soundadvice.systemdesign.fxml.logical.LogicalTabs.FlowInfo;
import au.id.soundadvice.systemdesign.fxml.logical.LogicalTabs.FunctionInfo;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.FunctionView;
import au.id.soundadvice.systemdesign.model.ItemView;
import au.id.soundadvice.systemdesign.model.RelationDiff;
import au.id.soundadvice.systemdesign.model.RelationPair;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
class LogicalSchematicController {

    private static final double SQRT2 = Math.sqrt(2);

    private final EditState edit;
    private final Interactions interactions;
    private final Tab tab;
    private final Optional<Function> parentFunction;
    private final TabPane tabs;
    private final SingleRunnable onChange = new SingleRunnable(
            JFXExecutor.instance(), new OnChange());
    private final AtomicBoolean started = new AtomicBoolean(false);

    LogicalSchematicController(
            Interactions interactions, EditState edit,
            TabPane tabs,
            Optional<Function> parentFunction) {
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

    public void select() {
        tabs.getSelectionModel().select(tab);
    }

    private Node toNode(FunctionInfo info) {
        Function function = info.getFunction().getSample();
        RelationDiff<Function> diff = info.getFunction();
        TextFlow flow = new TextFlow();
        if (diff.isAdded()) {
            flow.getChildren().add(new Text("added\n"));
        } else if (diff.isDeleted()) {
            flow.getChildren().add(new Text("deleted\n"));
        }
        Optional<String> oldName = diff.getWasInstance()
                .map(sample -> sample.getName());
        Optional<String> newName = diff.getIsInstance()
                .map(sample -> sample.getName());
        if (oldName.isPresent() && newName.isPresent()
                && !oldName.equals(newName)) {
            Text oldText = new Text(oldName.get() + '\n');
            oldText.getStyleClass().add("deleted");
            Text newText = new Text(newName.get() + '\n');
            newText.getStyleClass().add("changed");
            flow.getChildren().addAll(oldText, newText);
        } else {
            flow.getChildren().add(new Text(
                    newName.orElseGet(() -> oldName.get()) + '\n'));
        }
        flow.getChildren().add(new Text('(' + info.getItem().getDisplayName() + ')'));
        Label label = new Label(null, flow);

        Ellipse ellipse = new Ellipse();
        ellipse.getStyleClass().add("outline");
        ellipse.setFill(info.getItemView().getColor());

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
            ellipse.setRadiusX((halfWidth + insets) * SQRT2);
            ellipse.setRadiusY((halfHeight + insets) * SQRT2);
        });

        Group group = new Group(ellipse, label);
        group.getStyleClass().add("schematicFunction");
        if (function.isExternal()) {
            group.getStyleClass().add("external");
        } else if (parentFunction.isPresent()
                && !function.isTracedTo(this.parentFunction.get())) {
            /**
             * Is this an external view (as opposed to an external function)? An
             * external view is one that is internal to the system of interest,
             * but is traced to a parent function other than this drawing's
             * parent function.
             */
            group.getStyleClass().add("viewExternal");
        }
        if (diff.isDeleted()) {
            group.getStyleClass().add("deleted");
        } else if (diff.isAdded() || diff.isChanged()) {
            group.getStyleClass().add("changed");
        }
        group.setLayoutX(info.getView().getOrigin().getX());
        group.setLayoutY(info.getView().getOrigin().getY());

        if (!info.getFunction().isDeleted()) {
            ContextMenu contextMenu = ContextMenus.functionContextMenu(
                    info.getItem(), parentFunction, function, info.getView(), interactions, edit);
            label.setContextMenu(contextMenu);
        }

        if (!function.isExternal()) {
            group.setOnMouseClicked(event -> {
                if (event.getClickCount() > 1) {
                    PreferredTab.set(Optional.of(function));
                    interactions.navigateDown(info.getItem());
                    event.consume();
                }
            });
        }

        return group;
    }

    private Node toNode(
            Flow flow, String flowName, Direction direction,
            boolean deleted, boolean added,
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

        TextFlow textFlow = new TextFlow();
        Text text = new Text(flowName);
        if (deleted) {
            text.getStyleClass().add("deleted");
        }
        if (added) {
            text.getStyleClass().add("changed");
        }
        textFlow.getChildren().add(text);
        Label label = new Label(null, textFlow);

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

        ContextMenu contextMenu = ContextMenus.flowContextMenu(flow, interactions, edit);
        label.setContextMenu(contextMenu);

        Group group = new Group(path, label, normalArrow, reverseArrow);
        group.getStyleClass().add("schematicFlow");
        return group;
    }

    private Node toNode(
            RelationPair<FunctionView> views, List<FlowInfo> flows) {
        Point2D leftOrigin = views.getLeft().getOrigin();
        Point2D rightOrigin = views.getRight().getOrigin();
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
        for (FlowInfo flowInfo : flows) {
            Flow flow = flowInfo.getFlow();
            Direction direction = flowInfo.getDirectionBetweenViews();
            if (reverseDirection) {
                direction = direction.reverse();
            }
            Node node = toNode(
                    flow, flowInfo.getTypeName(), direction,
                    flowInfo.isDeleted(), flowInfo.isAdded(),
                    radiusX, radiusY, negate);
            group.getChildren().add(node);
            if (negate) {
                radiusY += 35;
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
            List<FunctionInfo> drawingFunctions,
            Map<RelationPair<FunctionView>, List<FlowInfo>> flows) {
        Pane pane = new AnchorPane();
        Group deleted = new Group();
        Group other = new Group();
        flows.entrySet().forEach(entry -> {
            Node node = toNode(entry.getKey(), entry.getValue());
            other.getChildren().add(node);
        });
        drawingFunctions.forEach(info -> {
            Node node = toNode(info);

            if (!info.getFunction().isDeleted()) {
                new MoveHandler(pane, node, new Move(info.getView()),
                        new GridSnap(10),
                        event -> MouseButton.PRIMARY.equals(event.getButton())
                        && !event.isControlDown()).start();
            }
            DragSource.bind(node, info.getView(), true);
            DragTarget.bind(edit, node, info.getView(),
                    new FunctionDropHandler(interactions, edit));
            if (info.getFunction().isDeleted()) {
                deleted.getChildren().add(node);
            } else {
                other.getChildren().add(node);
            }
        });
        pane.getChildren().add(deleted);
        pane.getChildren().add(other);

        if (parentFunction.isPresent()) {
            DragTarget.bind(edit, pane, parentFunction.get(),
                    new LogicalSchematicBackgroundDropHandler(edit)
            );
        }

        ContextMenu contextMenu = new ContextMenu();
        AtomicReference<Optional<ContextMenuEvent>> lastContextMenuClick
                = new AtomicReference<>(Optional.empty());
        Menu addMenuItem = new Menu("Add Function to...");
        MenuItem newItemMenuItem = new MenuItem("New Item");
        newItemMenuItem.setOnAction(e -> {
            Optional<Item> item = interactions.createItem(ItemView.defaultOrigin);
            if (item.isPresent()) {
                Point2D origin = lastContextMenuClick.get()
                        .map(evt -> new Point2D(evt.getX(), evt.getY()))
                        .orElse(ItemView.defaultOrigin);
                interactions.addFunctionToItem(item.get(), parentFunction, origin);
            }
        });
        ContextMenus.initPerInstanceSubmenu(
                addMenuItem,
                () -> Item.find(edit.getAllocated())
                .filter(item -> !item.isExternal())
                .sorted((a, b) -> a.getShortId().compareTo(b.getShortId())),
                Item::getDisplayName,
                (e, item) -> {
                    Point2D origin = lastContextMenuClick.get()
                    .map(evt -> new Point2D(evt.getX(), evt.getY()))
                    .orElse(ItemView.defaultOrigin);
                    interactions.addFunctionToItem(item, parentFunction, origin);
                },
                Optional.of(newItemMenuItem));
        contextMenu.getItems().add(addMenuItem);
        pane.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            lastContextMenuClick.set(Optional.of(event));
            contextMenu.show(pane, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        pane.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            contextMenu.hide();
        });

        ScrollPane scrollPane = new ScrollPane(pane);
        scrollPane.viewportBoundsProperty().addListener((info, old, bounds) -> {
            pane.setMinWidth(bounds.getWidth());
            pane.setMinHeight(bounds.getHeight());
        });
        tab.setContent(scrollPane);
    }

    private class Move implements Dragged {

        public Move(FunctionView view) {
            this.view = view;
        }
        private final FunctionView view;

        @Override
        public void dragged(Node parent, Node draggable, Point2D layoutCurrent) {
            edit.updateAllocated(allocated -> {
                Optional<FunctionView> existing = allocated.get(view);
                if (!existing.isPresent()) {
                    return allocated;
                }
                return existing.get().setOrigin(allocated, layoutCurrent).getBaseline();
            });
        }

    }

    private class OnChange implements Runnable {

        @Override
        public void run() {
            String name;
            if (parentFunction.isPresent()) {
                name = parentFunction.get().getName();
            } else {
                name = "Unallocated Functions";
            }
            tab.setText(name);
        }
    }
}
