/*
 * To change this license header, choose License Headers in Project Properties.
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
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.event.EventDispatcher;
import au.id.soundadvice.systemdesign.state.EditState;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import javafx.scene.input.TransferMode;
import javafx.util.Pair;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class EntityDropHandler implements DragTarget.Drop {

    private final EditState edit;

    public EntityDropHandler(EditState edit) {
        this.edit = edit;
    }

    @Override
    public Map<TransferMode, BooleanSupplier> getActions(WhyHowPair<Baseline> baselines, RecordID sourceIdentifier, RecordID targetIdentifier) {
        Map<TransferMode, BooleanSupplier> result = new HashMap<>();

        Optional<Record> from = baselines.getChild().getAnyType(sourceIdentifier);
        Optional<Record> to = baselines.getChild().getAnyType(targetIdentifier);
        if (from.isPresent() && to.isPresent()) {
            Optional<BiFunction<WhyHowPair<Baseline>, Pair<Record, Record>, WhyHowPair<Baseline>>> copyOperation
                    = EventDispatcher.INSTANCE.getCopyOperation(from.get().getType(), to.get().getType());
            Optional<BiFunction<WhyHowPair<Baseline>, Pair<Record, Record>, WhyHowPair<Baseline>>> moveOperation
                    = EventDispatcher.INSTANCE.getMoveOperation(from.get().getType(), to.get().getType());
            Optional<BiFunction<WhyHowPair<Baseline>, Pair<Record, Record>, WhyHowPair<Baseline>>> linkOperation
                    = EventDispatcher.INSTANCE.getLinkOperation(from.get().getType(), to.get().getType());

            if (copyOperation.isPresent()) {
                result.put(TransferMode.COPY, buildAction(copyOperation.get(), from.get(), to.get()));
            }
            if (moveOperation.isPresent()) {
                result.put(TransferMode.MOVE, buildAction(moveOperation.get(), from.get(), to.get()));
            }
            if (linkOperation.isPresent()) {
                result.put(TransferMode.LINK, buildAction(linkOperation.get(), from.get(), to.get()));
            }
        }
        return result;
    }

    private BooleanSupplier buildAction(
            BiFunction<WhyHowPair<Baseline>, Pair<Record, Record>, WhyHowPair<Baseline>> action,
            Record from, Record to) {
        return () -> {
            edit.updateState(baselines -> {
                return action.apply(baselines, new Pair<>(from, to));
            });
            return true;
        };
    }
}
