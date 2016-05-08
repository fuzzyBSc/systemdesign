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
package au.id.soundadvice.systemdesign.fxml.drawing;

import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.fxml.drag.MoveHandler.Dragged;
import au.id.soundadvice.systemdesign.fxml.ContextMenus;
import au.id.soundadvice.systemdesign.fxml.Interactions;
import au.id.soundadvice.systemdesign.fxml.drag.DragSource;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget;
import au.id.soundadvice.systemdesign.fxml.drag.GridSnap;
import au.id.soundadvice.systemdesign.fxml.drag.MoveHandler;
import au.id.soundadvice.systemdesign.fxml.logical.PreferredTab;
import static au.id.soundadvice.systemdesign.logical.Function.function;
import au.id.soundadvice.systemdesign.moduleapi.drawing.DrawingEntity;
import au.id.soundadvice.systemdesign.moduleapi.entity.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import static jdk.nashorn.internal.runtime.PropertyMap.diff;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
class FXMLDrawingNode implements DrawingOf<DrawingEntity> {

    private static final double SQRT2 = Math.sqrt(2);

    private final EditState edit;
    private final Interactions interactions;
    private final Group parent;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Group group;
    private Optional<DrawingEntity> previous = Optional.empty();

    FXMLDrawingNode(
            Interactions interactions, EditState edit,
            Group parent) {
        this.edit = edit;
        this.interactions = interactions;
        this.parent = parent;
        this.group = new Group();
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            parent.getChildren().add(group);
        }
    }

    @Override
    public void stop() {
        if (started.compareAndSet(true, false)) {
            parent.getChildren().remove(group);
        }
    }

    public Point2D getOrigin() {
        return previous.map(DrawingEntity::getOrigin).orElse(Point2D.ZERO);
    }

    @Override
    public void setState(DrawingEntity state) {
        Optional<DrawingEntity> next = Optional.of(state);
        if (next.equals(previous)) {
            return;
        }
        previous = next;
        Node node = toNode(state);
        if (!state.isDeleted()) {
            new MoveHandler(this.group, node, new Move(state),
                    new GridSnap(10),
                    event -> MouseButton.PRIMARY.equals(event.getButton())
                    && !event.isControlDown()).start();
        }
        Optional<Record> dragObject = state.getDragDropObject();
        if (dragObject.isPresent()) {
            DragSource.bind(node, dragObject.get(), true);
            DragTarget.bind(edit, node, dragObject.get(), new EntityDropHandler(edit));
        }
        this.group.getChildren().clear();
        this.group.getChildren().add(node);
    }

    private void getTextNodes(TextFlow flow, boolean first, DiffPair<String> line) {
        String sep = first ? "" : "\n";

        if (line.isDeleted() || line.isChanged()) {
            Text oldText = new Text(sep + line.getWasInstance().get());
            oldText.getStyleClass().add("deleted");
            flow.getChildren().add(oldText);
            sep = "\n";
        }
        if (line.isAdded() || line.isChanged()) {
            Text newText = new Text(sep + line.getIsInstance().get());
            newText.getStyleClass().add("changed");
            flow.getChildren().add(newText);
            sep = "\n";
        }
        if (!line.isAdded() && !line.isDeleted() && !line.isChanged()) {
            flow.getChildren().add(new Text(sep + line.getSample()));
        }
    }

    private Node toNode(DrawingEntity entity) {
        TextFlow flow = new TextFlow();
        boolean first = true;
        if (entity.isAdded()) {
            flow.getChildren().add(new Text("added\n"));
            first = false;
        } else if (entity.isDeleted()) {
            flow.getChildren().add(new Text("deleted\n"));
            first = false;
        }
        getTextNodes(flow, first, entity.getTitle());
        entity.getBody().sequential().forEachOrdered(
                line -> getTextNodes(flow, false, line));
        Label label = new Label(null, flow);

        Group nodeGroup = encloseLabel(entity, label);
        nodeGroup.getStyleClass().add("schematicFunction");
        if (entity.isExternal()) {
            nodeGroup.getStyleClass().add("external");
        } else if (entity.isExternalView()) {
            /**
             * Is this an external view (as opposed to an external function)? An
             * external view is one that is internal to the system of interest,
             * but is traced to a parent function other than this drawing's
             * parent function.
             */
            nodeGroup.getStyleClass().add("viewExternal");
        }
        if (entity.isDeleted()) {
            nodeGroup.getStyleClass().add("deleted");
        } else if (entity.isAdded() || entity.isChanged()) {
            nodeGroup.getStyleClass().add("changed");
        }
        nodeGroup.setLayoutX(entity.getOrigin().getX());
        nodeGroup.setLayoutY(entity.getOrigin().getY());

        if (entity.isDeleted()) {
            ContextMenu contextMenu = ContextMenus.deletedFunctionContextMenu(
                    diff.getWasBaseline().get(), function, interactions, edit);
            label.setContextMenu(contextMenu);
        } else {
            ContextMenu contextMenu = ContextMenus.functionContextMenu(
                    entity.getItem(), drawingSupplier, function, entity.getView(), interactions, edit);
            label.setContextMenu(contextMenu);
        }

        if (!function.isExternal()) {
            nodeGroup.setOnMouseClicked(event -> {
                if (event.getClickCount() > 1) {
                    PreferredTab.set(Optional.of(function));
                    interactions.navigateDown(entity.getItem());
                    event.consume();
                }
            });
        }

        return nodeGroup;
    }

    private Group encloseLabel(DrawingEntity entity, Label label) {
        switch (entity.getStyle().getShape()) {
            case Rectangle:
                return encloseLabelWithRectangle(entity, label);
            case Oval:
                return encloseLabelWithOval(entity, label);
            default:
                throw new AssertionError(entity.getStyle().getShape().name());
        }
    }

    private Group encloseLabelWithRectangle(DrawingEntity entity, Label label) {
        Rectangle rectangle = new Rectangle();
        rectangle.getStyleClass().add("outline");
        rectangle.setFill(entity.getColor());

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

        return new Group(rectangle, label);
    }

    private Group encloseLabelWithOval(DrawingEntity entity, Label label) {
        Ellipse ellipse = new Ellipse();
        ellipse.getStyleClass().add("outline");
        ellipse.setFill(entity.getColor());

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

        return new Group(ellipse, label);
    }

    private class Move implements Dragged {

        public Move(DrawingEntity view) {
            this.view = view;
        }
        private final DrawingEntity view;

        @Override
        public void dragged(Node parent, Node draggable, Point2D layoutCurrent) {
            edit.updateAllocated(allocated -> view.setOrigin(allocated, ISO8601.now(), layoutCurrent));
        }
    }
}
