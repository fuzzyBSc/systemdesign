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
import au.id.soundadvice.systemdesign.moduleapi.entity.Direction;
import au.id.soundadvice.systemdesign.fxml.ContextMenus;
import au.id.soundadvice.systemdesign.fxml.Interactions;
import au.id.soundadvice.systemdesign.moduleapi.entity.ConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.drawing.DrawingConnector;
import au.id.soundadvice.systemdesign.moduleapi.collection.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
class FXMLDrawingConnectorScope implements DrawingOf<List<DrawingConnector>> {

    private final EditState edit;
    private final Interactions interactions;
    private final Group parent;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Group group;
    private final ConnectionScope scope;
    private final Map<RecordID, FXMLDrawingNode> currentNodes;
    private Optional<Point2D> previousLeftPoint = Optional.empty();
    private Optional<Point2D> previousRightPoint = Optional.empty();
    private Optional<List<DrawingConnector>> previousConnectors = Optional.empty();

    FXMLDrawingConnectorScope(
            Interactions interactions, EditState edit,
            ConnectionScope scope,
            Map<RecordID, FXMLDrawingNode> currentNodes,
            Group parent) {
        this.edit = edit;
        this.interactions = interactions;
        this.parent = parent;
        this.scope = scope;
        this.currentNodes = currentNodes;
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

    @Override
    public void setState(List<DrawingConnector> state) {
        Optional<Point2D> nextLeftPoint
                = Optional.ofNullable(currentNodes.get(scope.getLeft()))
                .map(FXMLDrawingNode::getOrigin);
        Optional<Point2D> nextRightPoint
                = Optional.ofNullable(currentNodes.get(scope.getRight()))
                .map(FXMLDrawingNode::getOrigin);
        Optional<List<DrawingConnector>> nextConnectors = Optional.of(state);
        if (nextConnectors.equals(previousConnectors)
                && nextLeftPoint.equals(previousLeftPoint)
                && nextRightPoint.equals(previousRightPoint)) {
            return;
        }
        previousLeftPoint = nextLeftPoint;
        previousRightPoint = nextRightPoint;
        previousConnectors = nextConnectors;
        Node node = toNode(nextLeftPoint.get(), nextRightPoint.get(), state);
        this.group.getChildren().clear();
        this.group.getChildren().add(node);
    }

    private Node toNode(
            DrawingConnector connector, boolean reverseDirection,
            double radiusX, double radiusY, boolean negate) {
        DiffPair<String> flowName = connector.getLabel();
        ConnectionScope connectorScope = connector.getScope();
        Direction direction = connectorScope.getDirection();
        if (reverseDirection) {
            direction = direction.reverse();
        }
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
            case Forward:
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
        Text text = new Text(flowName.getSample().replaceAll("\\s+", "\n"));
        if (flowName.isDeleted()) {
            text.getStyleClass().add("deleted");
        }
        if (flowName.isAdded() || flowName.isChanged()) {
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

        if (connector.isDeleted()) {
            ContextMenu contextMenu = ContextMenus.deletedFlowContextMenu(
                    wasBaseline.get(), connector, interactions, edit);
            label.setContextMenu(contextMenu);
        } else {
            ContextMenu contextMenu = ContextMenus.flowContextMenu(connector, interactions, edit);
            label.setContextMenu(contextMenu);
        }

        Group singleConnectorGroup = new Group(path, label, normalArrow, reverseArrow);
        singleConnectorGroup.getStyleClass().add("schematicFlow");
        return singleConnectorGroup;
    }

    private Node toNode(
            Point2D leftOrigin, Point2D rightOrigin, List<DrawingConnector> connectors) {
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

        Group allConnectorsGroup = new Group();
        boolean negate = true;
        double radiusX = vector.magnitude() / 2;
        double radiusY = 0;
        for (DrawingConnector connector : connectors) {
            Node node = toNode(
                    connector, reverseDirection,
                    radiusX, radiusY, negate);
            allConnectorsGroup.getChildren().add(node);
            if (negate) {
                radiusY += 35;
                negate = false;
            } else {
                negate = true;
            }
        }
        allConnectorsGroup.setRotate(theta);
        allConnectorsGroup.setLayoutX(midpoint.getX());
        allConnectorsGroup.setLayoutY(midpoint.getY());

        return allConnectorsGroup;
    }
}
