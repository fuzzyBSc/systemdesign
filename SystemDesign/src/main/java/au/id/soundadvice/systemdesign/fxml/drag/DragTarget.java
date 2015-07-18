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

import au.id.soundadvice.systemdesign.model.baselines.EditState;
import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.files.Identifiable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class DragTarget {

    public static final String uuidPrefix = "urn:uuid:";

    public interface Drop {

        public Map<TransferMode, BooleanSupplier> getActions(
                UndoState state, UUID sourceUUID, UUID targetUUID);
    }

    public static Optional<UUID> toUUID(Dragboard dragboard) {
        Optional<String> optionalURL = Optional.ofNullable(dragboard.getUrl());
        Optional<String> optionalUUID = optionalURL.map(url -> {
            if (url.startsWith(uuidPrefix)) {
                return url.substring(uuidPrefix.length());
            } else {
                return url;
            }
        });
        try {
            return optionalUUID.map(UUID::fromString);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static void bind(EditState edit, Node node, Identifiable target, Drop drop) {
        Optional<Identifiable> optionalTarget = Optional.of(target);
        bind(edit, node, () -> optionalTarget, drop);
    }

    public static void bind(
            EditState edit, Node node, Supplier<Optional<? extends Identifiable>> supplier, Drop drop) {
        node.setOnDragOver(event -> {
            // Decide whether or not to accept the drop
            Optional<UUID> sourceUUID = toUUID(event.getDragboard());
            Optional<? extends Identifiable> target = supplier.get();
            if (sourceUUID.isPresent() && target.isPresent()
                    && !sourceUUID.get().equals(target.get().getUuid())) {
                UndoState state = edit.getUndo().get();
                Map<TransferMode, BooleanSupplier> actions
                        = drop.getActions(state, sourceUUID.get(), target.get().getUuid());
                if (!actions.isEmpty()) {
                    node.getStyleClass().add("dragTarget");
                    event.acceptTransferModes(
                            actions.keySet().toArray(new TransferMode[0]));
                    event.consume();
                }
            }
        });
        node.setOnDragEntered(event -> {
            // Show drag acceptance visually
            Optional<UUID> sourceUUID = toUUID(event.getDragboard());
            Optional<? extends Identifiable> target = supplier.get();
            if (sourceUUID.isPresent() && target.isPresent()
                    && !sourceUUID.get().equals(target.get().getUuid())) {
                UndoState state = edit.getUndo().get();
                Map<TransferMode, BooleanSupplier> actions
                        = drop.getActions(state, sourceUUID.get(), target.get().getUuid());
                if (!actions.isEmpty()) {
                    node.getStyleClass().add("dragTarget");
                    event.consume();
                }
            }
        });
        node.setOnDragExited(event -> {
            // Unshow drag acceptance
            node.getStyleClass().remove("dragTarget");
            event.consume();
        });
        node.setOnDragDropped(event -> {
            // Handle drop
            Optional<UUID> sourceUUID = toUUID(event.getDragboard());
            Optional<? extends Identifiable> target = supplier.get();
            if (sourceUUID.isPresent() && target.isPresent()
                    && !sourceUUID.get().equals(target.get().getUuid())) {
                UndoState state = edit.getUndo().get();
                Map<TransferMode, BooleanSupplier> actions
                        = drop.getActions(state, sourceUUID.get(), target.get().getUuid());
                BooleanSupplier action = actions.get(event.getAcceptedTransferMode());
                if (action == null) {
                    event.setDropCompleted(false);
                } else {
                    event.setDropCompleted(action.getAsBoolean());
                }
                event.consume();
            }
        });
    }
}
