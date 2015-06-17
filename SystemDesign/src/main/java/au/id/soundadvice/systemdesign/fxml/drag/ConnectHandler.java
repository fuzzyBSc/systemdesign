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
import java.util.UUID;
import javafx.scene.Node;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javax.annotation.Nullable;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ConnectHandler {

    public static final String uuidPrefix = "urn:uuid:";

    public interface Connect {

        public boolean canConnect(UUID source, UUID target);

        public void connect(UUID source, UUID target);
    }

    @Nullable
    public static UUID toUUID(Dragboard dragboard) {
        String url = dragboard.getUrl();
        if (url == null || !url.startsWith(uuidPrefix)) {
            return null;
        } else {
            try {
                return UUID.fromString(url.substring(uuidPrefix.length()));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    public static void startDrag(Node node, Identifiable identifiable) {
        // Start dragging
        Dragboard db = node.startDragAndDrop(TransferMode.LINK, TransferMode.MOVE);
        ClipboardContent content = new ClipboardContent();
        content.put(DataFormat.PLAIN_TEXT, identifiable.toString());
        content.put(DataFormat.URL, uuidPrefix + identifiable.getUuid());
        db.setContent(content);
        node.getStyleClass().add("dragSource");
    }

    public static void dragDone(Node node) {
        node.getStyleClass().remove("dragSource");
    }

    public static void register(
            Node target, Identifiable identifiable, Connect connect, MouseFilter filter) {
        target.setOnDragDetected(event -> {
            if (filter.matches(event)) {
                startDrag(target, identifiable);
                event.consume();
            }
        });
        target.setOnDragOver(event -> {
            // Decide whether or not to accept the drop
            UUID sourceUUID = toUUID(event.getDragboard());
            if (connect.canConnect(sourceUUID, identifiable.getUuid())) {
                event.acceptTransferModes(TransferMode.LINK);
            }

            event.consume();
        });
        target.setOnDragEntered(event -> {
            // Show drag acceptance visually
            UUID sourceUUID = toUUID(event.getDragboard());
            if (connect.canConnect(sourceUUID, identifiable.getUuid())) {
                target.getStyleClass().add("dragTarget");
            }

            event.consume();
        });
        target.setOnDragExited(event -> {
            // Unshow drag acceptance
            target.getStyleClass().remove("dragTarget");

            event.consume();
        });
        target.setOnDragDropped(event -> {
            // Handle drop
            UUID sourceUUID = toUUID(event.getDragboard());
            UUID targetUUID = identifiable.getUuid();
            boolean ok;
            if (connect.canConnect(sourceUUID, targetUUID)) {
                connect.connect(sourceUUID, targetUUID);
                ok = true;
            } else {
                ok = false;
            }
            event.setDropCompleted(ok);

            event.consume();
        });
        target.setOnDragDone(event -> {
            dragDone(target);
            event.consume();
        });
    }
}
