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

import au.id.soundadvice.systemdesign.baselines.EditState;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Item;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ContextMenus {

    public static ContextMenu itemContextMenu(Item item, Interactions interactions, EditState edit) {

        ContextMenu contextMenu = new ContextMenu();
        if (item.isExternal()) {
            MenuItem deleteMenuItem = new MenuItem("Delete External Item");
            deleteMenuItem.setOnAction(event -> {
                edit.removeAllocatedRelation(item.getUuid());
                event.consume();
            });
            contextMenu.getItems().add(deleteMenuItem);
        } else {
            MenuItem addMenuItem = new MenuItem("Add Function");
            addMenuItem.setOnAction(event -> {
                interactions.addFunctionToItem(item);
                event.consume();
            });
            contextMenu.getItems().add(addMenuItem);
            MenuItem renameMenuItem = new MenuItem("Rename Item");
            renameMenuItem.setOnAction(event -> {
                interactions.rename(item);
                event.consume();
            });
            contextMenu.getItems().add(renameMenuItem);
            MenuItem navigateMenuItem = new MenuItem("Navigate Down");
            navigateMenuItem.setOnAction(event -> {
                interactions.navigateDown(item);
                event.consume();
            });
            contextMenu.getItems().add(navigateMenuItem);
            MenuItem deleteMenuItem = new MenuItem("Delete Item");
            deleteMenuItem.setOnAction(event -> {
                edit.removeAllocatedRelation(item.getUuid());
                event.consume();
            });
            contextMenu.getItems().add(deleteMenuItem);
        }
        return contextMenu;
    }

    public static ContextMenu functionContextMenu(Item item, Function function, Interactions interactions, EditState edit) {
        ContextMenu contextMenu = new ContextMenu();
        if (function.isExternal()) {
            MenuItem deleteMenuItem = new MenuItem("Delete External Function");
            deleteMenuItem.setOnAction(event -> {
                edit.removeAllocatedRelation(function.getUuid());
                event.consume();
            });
            contextMenu.getItems().add(deleteMenuItem);
        } else {
            MenuItem addMenuItem = new MenuItem("Rename Function");
            addMenuItem.setOnAction(event -> {
                interactions.rename(function);
                event.consume();
            });
            contextMenu.getItems().add(addMenuItem);
            MenuItem navigateMenuItem = new MenuItem("Navigate Down");
            navigateMenuItem.setOnAction(event -> {
                interactions.navigateDown(item);
                event.consume();
            });
            contextMenu.getItems().add(navigateMenuItem);
            MenuItem deleteMenuItem = new MenuItem("Delete Function");
            deleteMenuItem.setOnAction(event -> {
                edit.removeAllocatedRelation(function.getUuid());
                event.consume();
            });
            contextMenu.getItems().add(deleteMenuItem);
        }
        return contextMenu;
    }

    public static ContextMenu flowContextMenu(Flow flow, Interactions interactions, EditState edit) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem typeMenuItem = new MenuItem("Set Type");
        typeMenuItem.setOnAction((event) -> {
            interactions.setFlowType(flow);
            event.consume();
        });
        contextMenu.getItems().add(typeMenuItem);
        MenuItem deleteMenuItem = new MenuItem("Delete");
        deleteMenuItem.setOnAction((event) -> {
            edit.removeAllocatedRelation(flow.getUuid());
            event.consume();
        });
        contextMenu.getItems().add(deleteMenuItem);
        return contextMenu;
    }

    static ContextMenu logicalTreeBackgroundMenu(FunctionCreator functionCreator) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem addMenuItem = new MenuItem("Add Function");
        contextMenu.getItems().add(addMenuItem);
        addMenuItem.setOnAction(event -> {
            functionCreator.addToChild();
            event.consume();
        });
        return contextMenu;
    }

}
