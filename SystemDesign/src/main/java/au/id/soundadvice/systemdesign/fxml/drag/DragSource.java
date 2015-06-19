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
package au.id.soundadvice.systemdesign.fxml.drag;

import au.id.soundadvice.systemdesign.files.Identifiable;
import static au.id.soundadvice.systemdesign.fxml.drag.DragTarget.uuidPrefix;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javafx.scene.Node;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class DragSource {

    public static void bind(
            Node node, Identifiable source, boolean requireControlDown) {
        bind(node, () -> source, requireControlDown);
    }

    public static void bind(
            Node node, Supplier<? extends Identifiable> supplier, boolean requireControlDown) {
        node.setOnDragDetected(event -> {
            Identifiable source = supplier.get();
            if (source != null && !requireControlDown || event.isControlDown()) {
                startDrag(node, source);
                event.consume();
            }
        });
        node.setOnDragDone(event -> {
            dragDone(node);
            event.consume();
        });
    }

    public static void startDrag(Node node, Identifiable source) {
        // Start dragging
        Dragboard db = node.startDragAndDrop(TransferMode.ANY);
        ClipboardContent content = new ClipboardContent();
        content.put(DataFormat.PLAIN_TEXT, source.toString());
        content.put(DataFormat.URL, uuidPrefix + source.getUuid());
        db.setContent(content);
        node.getStyleClass().add("dragSource");
    }

    public static void dragDone(Node node) {
        node.getStyleClass().remove("dragSource");
    }

}