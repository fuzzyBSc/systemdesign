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

import au.id.soundadvice.systemdesign.fxml.ContextMenus;
import au.id.soundadvice.systemdesign.fxml.drag.EntityDropHandler;
import au.id.soundadvice.systemdesign.fxml.drag.DragTarget;
import static au.id.soundadvice.systemdesign.fxml.drawing.DrawingOf.updateElements;
import static au.id.soundadvice.systemdesign.fxml.drawing.DrawingOf.updateScopes;
import au.id.soundadvice.systemdesign.moduleapi.entity.ConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.drawing.Drawing;
import au.id.soundadvice.systemdesign.moduleapi.drawing.DrawingConnector;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.interaction.InteractionContext;
import au.id.soundadvice.systemdesign.moduleapi.interaction.MenuHints;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javax.annotation.Nullable;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FXMLDrawingTab implements DrawingOf<Drawing> {

    private final AtomicReference<ContextMenu> contextMenu = new AtomicReference<>();
    private final AtomicReference<ContextMenuEvent> lastContextMenuClick = new AtomicReference<>();

    public FXMLDrawingTab(InteractionContext context, ContextMenus menus, TabPane tabs) {
        this.context = context;
        this.menus = menus;
        this.tabs = tabs;
        this.tab = new Tab();
        // Control rendering order by placing different nodes into ordered layers
        this.pane = new AnchorPane(
                deletedConnectorsGroup,
                deletedEntitiesGroup,
                otherConnectorsGroup,
                otherEntitiesGroup);
        pane.getStyleClass().add("drawingArea");
        this.scrollPane = new ScrollPane(pane);
        scrollPane.viewportBoundsProperty().addListener((info, old, bounds) -> {
            pane.setMinWidth(bounds.getWidth());
            pane.setMinHeight(bounds.getHeight());
        });
        this.tab.setContent(scrollPane);
        pane.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            lastContextMenuClick.set(event);
            @Nullable
            ContextMenu menu = contextMenu.get();
            if (menu != null) {
                menu.show(pane, event.getScreenX(), event.getScreenY());
            }
            event.consume();
        });
        pane.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            @Nullable
            ContextMenu menu = contextMenu.get();
            if (menu != null) {
                menu.hide();
            }
        });
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
    private final Map<RecordID, FXMLDrawingNode> currentNodes = new HashMap<>();
    private final Map<ConnectionScope, FXMLDrawingConnectorScope> currentConnectors = new HashMap<>();

    private final InteractionContext context;
    private final ContextMenus menus;
    private final TabPane tabs;
    private final Tab tab;
    private final AnchorPane pane;
    private final ScrollPane scrollPane;
    private final Group deletedEntitiesGroup = new Group();
    private final Group otherEntitiesGroup = new Group();
    private final Group deletedConnectorsGroup = new Group();
    private final Group otherConnectorsGroup = new Group();

    private MenuHints getHints() {
        Optional<ContextMenuEvent> event = Optional.ofNullable(lastContextMenuClick.get());
        return new MenuHints(event.map(e -> new Point2D(e.getX(), e.getY())));
    }

    @Override
    public void setState(Drawing state) {
        pane.getStyleClass().add(state.getClass().getSimpleName());
        tab.setText(state.getTitle());
        Optional<ContextMenu> menu = state.getContextMenu(context).map(menuItems -> menus.getMenu(menuItems, () -> getHints()));
        if (menu.isPresent()) {
            ContextMenu newMenu = menu.get();
            @Nullable
            ContextMenu old = contextMenu.getAndSet(newMenu);
            if (old != null) {
                old.hide();
            }
            scrollPane.setContextMenu(menu.get());
        }
        updateElements(state.getEntities(), currentNodes, entity -> {
            Group parent = entity.isDeleted()
                    ? deletedEntitiesGroup
                    : otherEntitiesGroup;
            return new FXMLDrawingNode(context, menus, parent);
        });
        updateScopes(state.getConnectors(), currentConnectors, (scope, list) -> {
            boolean allDeleted = list.stream()
                    .allMatch(DrawingConnector::isDeleted);
            Group parent = allDeleted
                    ? deletedConnectorsGroup
                    : otherConnectorsGroup;
            return new FXMLDrawingConnectorScope(context, menus, scope, currentNodes, parent);
        });
        Optional<Record> dragObject = state.getDragDropObject();
        if (dragObject.isPresent()) {
            //    DragSource.bind(node, dragObject.get(), true);
            DragTarget.bind(context, pane, dragObject.get(), new EntityDropHandler(context));
        }

        Optional<RecordID> preferredTab = PreferredTab.get();
        if (preferredTab.isPresent() && state.getIdentifier().equals(preferredTab.get())) {
            SingleSelectionModel<Tab> selectionModel = tabs.getSelectionModel();
            selectionModel.select(tab);
            PreferredTab.clear();
        }
    }
}
