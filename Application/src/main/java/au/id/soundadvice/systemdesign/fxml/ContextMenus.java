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

import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.logical.Flow;
import au.id.soundadvice.systemdesign.logical.FlowType;
import au.id.soundadvice.systemdesign.logical.Function;
import au.id.soundadvice.systemdesign.logical.FunctionView;
import au.id.soundadvice.systemdesign.moduleapi.entity.Baseline;
import au.id.soundadvice.systemdesign.physical.Interface;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.physical.ItemView;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.event.ActionEvent;
import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ContextMenus {

    public static <T> void initPerInstanceSubmenu(
            Menu menu,
            Supplier<Stream<T>> supplier,
            java.util.function.Function<T, MenuItem> menufier,
            BiConsumer<ActionEvent, T> action,
            Optional<MenuItem> extra) {
        MenuItem dummy = new MenuItem("dummy");
        dummy.setVisible(false);
        menu.getItems().add(dummy);
        menu.setOnShowing(showEvent -> {
            // Populate the menu
            menu.getItems().clear();
            menu.getItems().addAll(
                    supplier.get()
                    .map(instance -> {
                        MenuItem menuItem = menufier.apply(instance);
                        menuItem.setOnAction(actionEvent -> action.accept(actionEvent, instance));
                        return menuItem;
                    })
                    .collect(Collectors.toList()));
            if (extra.isPresent()) {
                menu.getItems().add(extra.get());
            } else if (menu.getItems().isEmpty()) {
                MenuItem noItems = new MenuItem("None Found");
                noItems.setDisable(true);
                menu.getItems().add(noItems);
            }
        });
    }

    public static ContextMenu itemContextMenu(Item item, Interactions interactions, EditState edit) {
        ContextMenu contextMenu = new ContextMenu();
        if (item.isExternal()) {
            MenuItem deleteMenuItem = new MenuItem("Delete External Item");
            deleteMenuItem.setOnAction(event -> {
                edit.updateAllocated(baseline -> item.removeFrom(baseline));
                event.consume();
            });
            contextMenu.getItems().add(deleteMenuItem);
        } else {
            MenuItem addMenuItem = new MenuItem("Add Function");
            addMenuItem.setOnAction(event -> {
                interactions.addFunctionToItem(item, Optional.empty(), FunctionView.DEFAULT_ORIGIN);
                event.consume();
            });
            contextMenu.getItems().add(addMenuItem);
            MenuItem renameMenuItem = new MenuItem("Rename Item");
            renameMenuItem.setOnAction(event -> {
                interactions.rename(item);
                event.consume();
            });
            contextMenu.getItems().add(renameMenuItem);
            MenuItem colorMenuItem = new MenuItem("Set Color");
            colorMenuItem.setOnAction(event -> {
                interactions.color(item);
                event.consume();
            });
            contextMenu.getItems().add(colorMenuItem);
            MenuItem navigateMenuItem = new MenuItem("Navigate Down");
            navigateMenuItem.setOnAction(event -> {
                interactions.navigateDown(item);
                event.consume();
            });
            contextMenu.getItems().add(navigateMenuItem);
            MenuItem deleteMenuItem = new MenuItem("Delete Item");
            deleteMenuItem.setOnAction(event -> {
                edit.updateAllocated(baseline -> item.removeFrom(baseline));
                event.consume();
            });
            contextMenu.getItems().add(deleteMenuItem);
        }
        return contextMenu;
    }

    public static ContextMenu deletedItemContextMenu(
            Baseline was, Item item, Interactions interactions, EditState edit) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem restoreMenuItem = new MenuItem("Restore Item");
        restoreMenuItem.setOnAction(event -> {
            edit.restoreDeleted(item);
            event.consume();
        });
        contextMenu.getItems().add(restoreMenuItem);
        return contextMenu;
    }

    public static ContextMenu interfaceContextMenu(
            Interface iface, Interactions interactions, EditState edit) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteMenuItem = new MenuItem("Delete Interface");
        deleteMenuItem.setOnAction(event -> {
            edit.updateAllocated(baseline -> iface.removeFrom(baseline));
            event.consume();
        });
        contextMenu.getItems().add(deleteMenuItem);
        return contextMenu;
    }

    public static ContextMenu deletedInterfaceContextMenu(
            Baseline was, Interface iface, Interactions interactions, EditState edit) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem restoreMenuItem = new MenuItem("Restore Interface");
        restoreMenuItem.setOnAction(event -> {
            // Restore dependencies
            Item wasLeftItem = iface.getLeft(was);
            Item wasRightItem = iface.getRight(was);
            edit.restoreDeleted(wasLeftItem, wasRightItem, iface);
            event.consume();
        });
        contextMenu.getItems().add(restoreMenuItem);
        return contextMenu;
    }

    public static ContextMenu functionContextMenu(
            Item item,
            Optional<Function> drawing, Function function, FunctionView view,
            Interactions interactions, EditState edit) {
        ContextMenu contextMenu = new ContextMenu();
        if (function.isExternal()) {
            MenuItem deleteMenuItem = new MenuItem("Delete External Function");
            deleteMenuItem.setOnAction(event -> {
                edit.updateAllocated(baseline -> function.removeFrom(baseline));
                event.consume();
            });
            contextMenu.getItems().add(deleteMenuItem);
        } else if (drawing.isPresent()
                && !function.isTracedTo(drawing.get())) {
            MenuItem deleteMenuItem = new MenuItem("Delete View");
            deleteMenuItem.setOnAction(event -> {
                edit.updateAllocated(baseline -> view.removeFrom(baseline));
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
                edit.updateAllocated(baseline -> function.removeFrom(baseline));
                event.consume();
            });
            contextMenu.getItems().add(deleteMenuItem);
        }
        return contextMenu;
    }

    public static ContextMenu deletedFunctionContextMenu(
            Baseline was, Function function, Interactions interactions, EditState edit) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem restoreMenuItem = new MenuItem("Restore Function");
        restoreMenuItem.setOnAction(event -> {
            Item wasItem = function.getItem(was);
            edit.restoreDeleted(wasItem, function);
            event.consume();
        });
        contextMenu.getItems().add(restoreMenuItem);
        return contextMenu;
    }

    public static ContextMenu flowContextMenu(Flow flow, Interactions interactions, EditState edit) {
        ContextMenu contextMenu = new ContextMenu();
        Menu typeMenu = new Menu("Set Type");
        MenuItem typeMenuItem = new MenuItem("New Type");
        typeMenuItem.setOnAction((event) -> {
            interactions.setFlowType(flow);
            event.consume();
        });
        initPerInstanceSubmenu(
                typeMenu,
                () -> FlowType.find(edit.getChild())
                .sorted((a, b) -> a.getName().compareTo(b.getName())),
                type -> new MenuItem(type.getName()),
                (e, type) -> edit.updateAllocated(allocated -> {
                    Optional<Flow> flowSample = allocated.get(flow);
                    Optional<FlowType> typeSample = allocated.get(type);
                    if (flowSample.isPresent() && typeSample.isPresent()) {
                        allocated = flowSample.get().setType(allocated, typeSample.get()).getKey();
                    }
                    return allocated;
                }),
                Optional.of(typeMenuItem));
        contextMenu.getItems().add(typeMenu);
        MenuItem deleteMenuItem = new MenuItem("Delete");
        deleteMenuItem.setOnAction((event) -> {
            edit.updateAllocated(baseline -> flow.removeFrom(baseline));
            event.consume();
        });
        contextMenu.getItems().add(deleteMenuItem);
        return contextMenu;
    }

    public static ContextMenu deletedFlowContextMenu(
            Baseline was, Flow flow, Interactions interactions, EditState edit) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem restoreMenuItem = new MenuItem("Restore Flow");
        restoreMenuItem.setOnAction(event -> {
            // Restore dependencies
            Function wasLeftFunction = flow.getLeft(was);
            Function wasRightFunction = flow.getRight(was);
            FlowType wasFlowType = flow.getType(was);
            edit.restoreDeleted(wasLeftFunction, wasRightFunction, wasFlowType, flow);

            event.consume();
        });
        contextMenu.getItems().add(restoreMenuItem);
        return contextMenu;
    }

    public static ContextMenu logicalTreeBackgroundMenu(
            Interactions interactions, EditState edit) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem addMenuItem = ContextMenus.addFunctionMenu(
                interactions,
                edit,
                Optional.empty(),
                () -> FunctionView.DEFAULT_ORIGIN);
        contextMenu.getItems().add(addMenuItem);
        return contextMenu;
    }

    static ContextMenu budgetTreeBackgroundMenu(Interactions interactions) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem addMenuItem = new MenuItem("Add Budget");
        contextMenu.getItems().add(addMenuItem);
        addMenuItem.setOnAction(event -> {
            interactions.createBudget();
            event.consume();
        });
        return contextMenu;
    }

    public static Menu addFunctionMenu(
            Interactions interactions,
            EditState edit,
            Optional<Function> parentFunction,
            Supplier<Point2D> originSupplier) {
        Menu addFunctionMenu = new Menu("Add Function to...");
        MenuItem newItemMenuItem = new MenuItem("New Item");
        newItemMenuItem.setOnAction(e -> {
            Optional<Item> item = interactions.createItem(ItemView.DEFAULT_ORIGIN);
            if (item.isPresent()) {
                Point2D origin = originSupplier.get();
                interactions.addFunctionToItem(item.get(), parentFunction, origin);
            }
        });
        ContextMenus.initPerInstanceSubmenu(
                addFunctionMenu,
                () -> Item.find(edit.getChild())
                .filter(item -> !item.isExternal())
                .sorted((a, b) -> a.getShortId().compareTo(b.getShortId())),
                item -> new MenuItem(item.getDisplayName()),
                (e, item) -> {
                    Point2D origin = originSupplier.get();
                    interactions.addFunctionToItem(item, parentFunction, origin);
                },
                Optional.of(newItemMenuItem));
        return addFunctionMenu;
    }

}
