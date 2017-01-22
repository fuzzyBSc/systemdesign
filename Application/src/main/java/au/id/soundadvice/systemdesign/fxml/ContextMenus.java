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

import au.id.soundadvice.systemdesign.moduleapi.interaction.MenuItems;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ContextMenus {

    public ContextMenus(Interactions interactions) {
        this.interactions = interactions;
    }

    private final Interactions interactions;

    public ContextMenu getMenu(MenuItems target) {
        ContextMenu result = new ContextMenu();
        fillMenu(result.getItems(), target.items(interactions));
        return result;
    }

    private void fillMenu(List<MenuItem> result, Stream<MenuItems.MenuItem> target) {
        result.clear();
        result.addAll(
                target
                .map((MenuItems.MenuItem ourMenuItem) -> {
                    MenuItem fxmlMenuItem;
                    if (ourMenuItem instanceof MenuItems.Submenu) {
                        Menu fxmlSubmenu = new Menu(ourMenuItem.getText());
                        MenuItem dummy = new MenuItem("dummy");
                        dummy.setVisible(false);
                        fxmlSubmenu.getItems().add(dummy);
                        fxmlSubmenu.setOnShowing(showEvent -> {
                            // Populate the menu
                            fillMenu(fxmlSubmenu.getItems(), ourMenuItem.getChildren());
                            if (fxmlSubmenu.getItems().isEmpty()) {
                                MenuItem noItems = new MenuItem("None Found");
                                noItems.setDisable(true);
                                fxmlSubmenu.getItems().add(noItems);
                            }
                            showEvent.consume();
                        });
                        fxmlMenuItem = fxmlSubmenu;
                    } else if (ourMenuItem instanceof Runnable) {
                        fxmlMenuItem = new MenuItem(ourMenuItem.getText());
                        fxmlMenuItem.setOnAction(e -> {
                            ((Runnable) ourMenuItem).run();
                            e.consume();
                        });
                    } else {
                        fxmlMenuItem = new MenuItem(ourMenuItem.getText());
                        fxmlMenuItem.setDisable(true);
                    }
                    return fxmlMenuItem;
                })
                .collect(Collectors.toList()));
    }

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
}
