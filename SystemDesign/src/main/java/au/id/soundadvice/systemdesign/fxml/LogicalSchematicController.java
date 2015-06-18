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
import au.id.soundadvice.systemdesign.fxml.drag.DragHandler;
import au.id.soundadvice.systemdesign.fxml.drag.DragHandler.Dragged;
import au.id.soundadvice.systemdesign.fxml.drag.GridSnap;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.baselines.UndoBuffer;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Ellipse;
import javax.annotation.Nullable;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
class LogicalSchematicController {

    private static final double root2 = Math.sqrt(2);

    private final EditState edit;
    private final Tab tab;
    @Nullable
    private final Function parentFunction;
    private final TabPane tabs;
    private final SingleRunnable onChange = new SingleRunnable(
            JFXExecutor.instance(), new OnChange());
    private final AtomicBoolean started = new AtomicBoolean(false);

    LogicalSchematicController(EditState edit, TabPane tabs, @Nullable Function parentFunction) {
        this.edit = edit;
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
                function.getDisplayName() + '\n'
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
        group.setLayoutX(function.getOrigin().getX());
        group.setLayoutY(function.getOrigin().getY());

        return group;
    }

    public void setFunctions(AllocatedBaseline baseline, Collection<Function> functions) {
        Pane pane = new AnchorPane();
        functions.forEach(function -> {
            Item item = function.getItem().getTarget(baseline.getStore());
            Node node = toNode(function, item);

            new DragHandler(pane, node, new Move(function),
                    new GridSnap(10),
                    event -> MouseButton.PRIMARY.equals(event.getButton())
                    && !event.isControlDown()).start();
            pane.getChildren().add(node);
        });
        tab.setContent(pane);
    }

    private class Move implements Dragged {

        public Move(Function function) {
            this.function = function;
        }
        private final Function function;

        @Override
        public void dragged(Node parent, Node draggable, Point2D layoutCurrent) {
            UndoBuffer<UndoState> undo = edit.getUndo();
            UndoState state = undo.get();
            AllocatedBaseline allocated = state.getAllocated();
            undo.set(state.setAllocated(allocated.add(function.setOrigin(layoutCurrent))));
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
