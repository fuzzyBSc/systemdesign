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
import au.id.soundadvice.systemdesign.fxml.drag.ConnectHandler;
import au.id.soundadvice.systemdesign.fxml.drag.ConnectHandler.Connect;
import au.id.soundadvice.systemdesign.fxml.drag.DragHandler.Dragged;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.relation.RelationContext;
import java.util.UUID;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalSchematicController {

    private final EditState state;
    private final AnchorPane drawing;
    private final SingleRunnable onChange = new SingleRunnable(
            JFXExecutor.instance(), new OnChange());

    PhysicalSchematicController(EditState state, AnchorPane drawing) {
        this.state = state;
        this.drawing = drawing;
    }

    void start() {
        state.subscribe(onChange);
        onChange.run();
    }

    private class OnChange implements Runnable {

        @Override
        public void run() {
            drawing.getChildren().clear();
            AllocatedBaseline baseline = state.getUndo().get();
            baseline.getInterfaces().stream().forEach((iface) -> {
                addNode(drawing, baseline, iface);
            });
            baseline.getItems().stream().forEach((item) -> {
                addNode(drawing, item);
            });
        }

        private void addNode(AnchorPane parent, AllocatedBaseline baseline, Interface iface) {
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

        private void addNode(Pane parent, Item item) {
            Point2D origin = item.getOrigin();
            Label result = new Label();
            result.setText(item.toString());
            result.setLayoutX(origin.getX());
            result.setLayoutY(origin.getY());
            result.getStyleClass().add("schematicItem");

//            new DragHandler(parent, result, new MoveItem(item),
//                    new GridSnap(10), MouseButton.PRIMARY).start();
            ConnectHandler.register(result, item, new ConnectItems(), MouseButton.PRIMARY);

            parent.getChildren().add(result);
        }
    }

    private class ConnectItems implements Connect {

        @Override
        public boolean canConnect(UUID sourceId, UUID targetId) {
            RelationContext store = state.getUndo().get().getStore();
            Item source = store.get(sourceId, Item.class);
            Item target = store.get(targetId, Item.class);
            return source != null && target != null;
        }

        @Override
        public void connect(UUID sourceId, UUID targetId) {
            AllocatedBaseline baseline = state.getUndo().get();
            Interface newInterface = new Interface(sourceId, targetId);
            for (Interface existing : baseline.getStore().getReverse(sourceId, Interface.class)) {
                if (newInterface.isRedundantTo(existing)) {
                    return;
                }
            }
            state.getUndo().set(baseline.add(newInterface));
        }

    }

    private class MoveItem implements Dragged {

        public MoveItem(Item item) {
            this.item = item;
        }

        private final Item item;

        @Override
        public void dragged(Node parent, Node draggable, Point2D layoutCurrent) {
            state.getUndo().set(
                    state.getUndo().get().add(item.setOrigin(layoutCurrent)));
        }
    }

}