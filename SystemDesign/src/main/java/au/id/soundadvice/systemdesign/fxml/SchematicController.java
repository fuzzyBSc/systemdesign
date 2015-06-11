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
import au.id.soundadvice.systemdesign.fxml.DragHandler.Dragged;
import au.id.soundadvice.systemdesign.model.Item;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class SchematicController {

    private final EditState state;
    private final AnchorPane drawing;
    private final SingleRunnable onChange = new SingleRunnable(
            JFXExecutor.instance(), new OnChange());

    SchematicController(EditState state, AnchorPane drawing) {
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
            for (Item item : baseline.getItems()) {
                addNode(drawing, item);
            }
        }

        void addNode(AnchorPane parent, Item item) {
            Point2D origin = item.getOrigin();
            Label result = new Label();
            result.setText(item.toString());
            result.setLayoutX(origin.getX());
            result.setLayoutY(origin.getY());
            result.getStyleClass().add("schematicItem");

            new DragHandler(parent, result, new MoveItem(item)).start();

            parent.getChildren().add(result);
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
                    state.getUndo().get().addItem(item.setOrigin(layoutCurrent)));
        }
    }

}
