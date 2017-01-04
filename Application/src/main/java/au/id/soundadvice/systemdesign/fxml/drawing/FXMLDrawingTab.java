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

import au.id.soundadvice.systemdesign.fxml.drag.EntityDropHandler;
import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.fxml.Interactions;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget;
import static au.id.soundadvice.systemdesign.fxml.drawing.DrawingOf.updateElements;
import static au.id.soundadvice.systemdesign.fxml.drawing.DrawingOf.updateScopes;
import au.id.soundadvice.systemdesign.moduleapi.entity.ConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.drawing.Drawing;
import au.id.soundadvice.systemdesign.moduleapi.drawing.DrawingConnector;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.Group;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FXMLDrawingTab implements DrawingOf<Drawing> {

    public FXMLDrawingTab(Interactions interactions, EditState edit, TabPane tabs) {
        this.interactions = interactions;
        this.edit = edit;
        this.tabs = tabs;
        this.tab = new Tab();
        // Control rendering order by placing different nodes into ordered layers
        this.pane = new AnchorPane(
                deletedConnectorsGroup,
                deletedEntitiesGroup,
                otherConnectorsGroup,
                otherEntitiesGroup);
        ScrollPane scrollPane = new ScrollPane(pane);
        scrollPane.viewportBoundsProperty().addListener((info, old, bounds) -> {
            pane.setMinWidth(bounds.getWidth());
            pane.setMinHeight(bounds.getHeight());
        });
        this.tab.setContent(scrollPane);
    }

    public void select() {
        tabs.getSelectionModel().select(tab);
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            tabs.getTabs().add(tab);
        }
    }

    @Override
    public void stop() {
        if (started.compareAndSet(true, false)) {
            tabs.getTabs().remove(tab);
        }
    }

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Map<String, FXMLDrawingNode> currentNodes = new HashMap<>();
    private final Map<ConnectionScope, FXMLDrawingConnectorScope> currentConnectors = new HashMap<>();

    private final Interactions interactions;
    private final EditState edit;
    private final TabPane tabs;
    private final Tab tab;
    private final AnchorPane pane;
    private final Group deletedEntitiesGroup = new Group();
    private final Group otherEntitiesGroup = new Group();
    private final Group deletedConnectorsGroup = new Group();
    private final Group otherConnectorsGroup = new Group();

    @Override
    public void setState(Drawing state) {
        updateElements(state.getEntities(), currentNodes, entity -> {
            Group parent = entity.isDeleted()
                    ? deletedEntitiesGroup
                    : otherEntitiesGroup;
            return new FXMLDrawingNode(interactions, edit, parent);
        });
        updateScopes(state.getConnectors(), currentConnectors, (scope, list) -> {
            boolean allDeleted = list.stream()
                    .allMatch(DrawingConnector::isDeleted);
            Group parent = allDeleted
                    ? deletedConnectorsGroup
                    : otherConnectorsGroup;
            return new FXMLDrawingConnectorScope(interactions, edit, scope, currentNodes, parent);
        });
        Optional<Record> dragObject = state.getDragDropObject();
        if (dragObject.isPresent()) {
            //    DragSource.bind(node, dragObject.get(), true);
            DragTarget.bind(edit, pane, dragObject.get(), new EntityDropHandler(edit));
        }
    }
}
