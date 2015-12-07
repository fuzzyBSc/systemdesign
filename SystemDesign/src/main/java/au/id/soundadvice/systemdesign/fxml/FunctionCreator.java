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

import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Item;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javafx.scene.control.ChoiceDialog;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FunctionCreator {

    public FunctionCreator(EditState edit) {
        this.edit = edit;
    }

    private final EditState edit;
    private final AtomicReference<Item> mostRecentItem = new AtomicReference<>();

    public void add(Function nearby) {
        Baseline functional = edit.getFunctional();
        if (functional.get(nearby).isPresent()) {
            addToParent();
        } else {
            addToChild();
        }
    }

    void addToParent() {
        Optional<Item> systemOfInterest = edit.getSystemOfInterest();
        if (systemOfInterest.isPresent()) {
            edit.updateFunctional(
                    functional -> Function.create(
                            functional, systemOfInterest.get()).getBaseline());
        }
    }

    void addToChild() {
        Baseline allocated = edit.getAllocated();
        Optional<Item> item = chooseItem(allocated);
        if (item.isPresent()) {
            edit.updateAllocated(
                    functional -> Function.create(
                            functional, item.get()).getBaseline());
        }
    }

    private Optional<Item> chooseItem(Baseline baseline) {
        List<Item> choices = Item.find(baseline).parallel()
                .filter(item -> !item.isExternal())
                .sorted((left, right) -> left.toString().compareTo(right.toString()))
                .collect(Collectors.toList());
        if (!choices.isEmpty()) {
            Item selected = choices.get(0);
            if (choices.size() == 1) {
                return Optional.of(selected);
            } else {
                Optional<Item> mostRecent = Optional.ofNullable(mostRecentItem.get())
                        .flatMap(item -> baseline.get(item));
                if (mostRecent.isPresent()) {
                    selected = mostRecent.get();
                }

                ChoiceDialog<Item> dialog = new ChoiceDialog<>(selected, choices);
                dialog.setTitle("Choose Item");
                dialog.setHeaderText("Select an Item to implement the function");

                Optional<Item> result = dialog.showAndWait();
                if (result.isPresent()) {
                    mostRecentItem.set(result.get());
                    return result;
                }
            }
        }
        return Optional.empty();
    }
}
