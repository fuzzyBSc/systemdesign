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
package au.id.soundadvice.systemdesign.fxml.drawing;

import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.fxml.ContextMenus;
import au.id.soundadvice.systemdesign.fxml.Interactions;
import static au.id.soundadvice.systemdesign.fxml.drawing.DrawingOf.updateElements;
import au.id.soundadvice.systemdesign.moduleapi.drawing.Drawing;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.preferences.Modules;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javafx.scene.control.TabPane;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FXMLAllDrawings {

    public FXMLAllDrawings(
            Interactions interactions, EditState edit, ContextMenus menus,
            TabPane tabs) {
        this.interactions = interactions;
        this.edit = edit;
        this.menus = menus;
        this.tabs = tabs;
        this.onChange = new SingleRunnable(edit.getExecutor(), new OnChange());
    }

    private final SingleRunnable onChange;
    private final SingleRunnable applyChange = new SingleRunnable(
            JFXExecutor.instance(), new ApplyChange());
    private final Map<RecordID, FXMLDrawingTab> currentDrawings = new HashMap<>();
    private final AtomicReference<List<Drawing>> nextDrawings = new AtomicReference<>();

    public void start() {
        edit.subscribe(onChange);
        onChange.run();
    }

    private final Interactions interactions;
    private final EditState edit;
    private final ContextMenus menus;
    private final TabPane tabs;

    class OnChange implements Runnable {

        @Override
        public void run() {
            DiffPair<Baseline> baselines = DiffPair.get(edit.getDiffBaseline(), edit.getChild());
            List<Drawing> drawings = Modules.getModules()
                    .flatMap(module -> module.getDrawings(baselines))
                    .collect(Collectors.toList());
            nextDrawings.set(drawings);
            applyChange.run();
        }

    }

    class ApplyChange implements Runnable {

        @Override
        public void run() {
            updateElements(
                    nextDrawings.get().stream(),
                    currentDrawings,
                    state -> new FXMLDrawingTab(interactions, menus, tabs));
        }

    }
}
