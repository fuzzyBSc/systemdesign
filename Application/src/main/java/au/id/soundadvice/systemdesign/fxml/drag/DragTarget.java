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

import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.entity.Identifiable;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.interaction.InteractionContext;
import java.util.Map;
import java.util.Optional;
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

    public static final String UUID_PREFIX = "urn:uuid:";

    public interface Drop {

        public Map<TransferMode, BooleanSupplier> getActions(
                WhyHowPair<Baseline> baselines, RecordID sourceIdentifier, RecordID targetIdentifier);
    }

    public static Optional<RecordID> toIdentifier(Dragboard dragboard) {
        return Optional.ofNullable(dragboard.getUrl())
                .flatMap(url -> {
                    if (url.startsWith(UUID_PREFIX)) {
                        return RecordID.load(url.substring(UUID_PREFIX.length()));
                    } else {
                        return RecordID.load(url);
                    }
                });
    }

    public static void bind(InteractionContext context, Node node, Identifiable target, Drop drop) {
        Optional<Identifiable> optionalTarget = Optional.of(target);
        bind(context, node, () -> optionalTarget, drop);
    }

    public static void bind(
            InteractionContext context, Node node, Supplier<Optional<? extends Identifiable>> supplier, Drop drop) {
        node.setOnDragOver(event -> {
            // Decide whether or not to accept the drop
            Optional<RecordID> sourceIdentifier = toIdentifier(event.getDragboard());
            Optional<? extends Identifiable> target = supplier.get();
            if (sourceIdentifier.isPresent() && target.isPresent()
                    && !sourceIdentifier.get().equals(target.get().getIdentifier())) {
                WhyHowPair<Baseline> state = context.getState();
                Map<TransferMode, BooleanSupplier> actions
                        = drop.getActions(state, sourceIdentifier.get(), target.get().getIdentifier());
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
            Optional<RecordID> sourceIdentifier = toIdentifier(event.getDragboard());
            Optional<? extends Identifiable> target = supplier.get();
            if (sourceIdentifier.isPresent() && target.isPresent()
                    && !sourceIdentifier.get().equals(target.get().getIdentifier())) {
                WhyHowPair<Baseline> state = context.getState();
                Map<TransferMode, BooleanSupplier> actions
                        = drop.getActions(state, sourceIdentifier.get(), target.get().getIdentifier());
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
            Optional<RecordID> sourceIdentifier = toIdentifier(event.getDragboard());
            Optional<? extends Identifiable> target = supplier.get();
            if (sourceIdentifier.isPresent() && target.isPresent()
                    && !sourceIdentifier.get().equals(target.get().getIdentifier())) {
                WhyHowPair<Baseline> state = context.getState();
                Map<TransferMode, BooleanSupplier> actions
                        = drop.getActions(state, sourceIdentifier.get(), target.get().getIdentifier());
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
