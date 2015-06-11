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

import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import static javafx.scene.input.MouseEvent.MOUSE_DRAGGED;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class DragHandler {

    private final StartDrag startDrag = new StartDrag();
    private final Snap snap;
    private DragOperation operation = null;

    public void start() {
        draggable.setOnMouseDragged(startDrag);
        draggable.setOnMouseReleased(startDrag);
    }

    public interface Dragged {

        public void dragged(Node parent, Node draggable, Point2D layoutCurrent);
    }

    public DragHandler(Node parent, Node draggable, Dragged dragged, Snap snap) {
        this.parent = parent;
        this.draggable = draggable;
        this.dragged = dragged;
        this.snap = snap;
    }

    private class StartDrag implements EventHandler<MouseEvent> {

        @Override
        public void handle(MouseEvent event) {
            if (MOUSE_DRAGGED.equals(event.getEventType())) {
                if (operation == null) {
                    Point2D layoutStart = new Point2D(
                            draggable.getLayoutX(), draggable.getLayoutY());
                    Point2D mouseStart = new Point2D(event.getSceneX(), event.getSceneY());
                    operation = new DragOperation(layoutStart, mouseStart);
                } else {
                    operation.handle(event);
                }
            } else {
                if (operation != null) {
                    operation.handle(event);
                    operation.commit();
                    operation = null;
                }
            }
        }

    }

    private class DragOperation implements EventHandler<MouseEvent> {

        private final Point2D layoutStart;
        private final Point2D mouseStart;
        private Point2D layoutCurrent;

        @SuppressWarnings("LeakingThisInConstructor")
        private DragOperation(Point2D layoutStart, Point2D mouseStart) {
            this.layoutStart = layoutStart;
            this.mouseStart = mouseStart;
            this.layoutCurrent = layoutStart;

            parent.setOnMouseMoved(this);
        }

        @Override
        public void handle(MouseEvent event) {
            layoutCurrent = new Point2D(
                    layoutStart.getX() + event.getSceneX() - mouseStart.getX(),
                    layoutStart.getY() + event.getSceneY() - mouseStart.getY()
            );
            layoutCurrent = snap.snap(layoutCurrent);
            draggable.setLayoutX(layoutCurrent.getX());
            draggable.setLayoutY(layoutCurrent.getY());
        }

        private void commit() {
            dragged.dragged(parent, draggable, layoutCurrent);
        }

    }

    private final Node parent;
    private final Node draggable;
    private final Dragged dragged;
}
