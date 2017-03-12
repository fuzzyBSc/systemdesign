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
package au.id.soundadvice.systemdesign.physical.interactions;

import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.interaction.InteractionContext;
import java.util.stream.Stream;
import au.id.soundadvice.systemdesign.moduleapi.interaction.MenuItems;
import au.id.soundadvice.systemdesign.physical.entity.ItemView;
import java.util.Optional;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalContextMenus {

    public PhysicalContextMenus(PhysicalInteractions physicalInteractions) {
        this.physicalInteractions = physicalInteractions;
    }

    private final PhysicalInteractions physicalInteractions;

    public MenuItems getItemContextMenu(Record item) {
        return new ItemContextMenu(item);
    }

    public MenuItems getDeletedItemContextMenu(Record item) {
        return new ItemContextMenu(item);
    }

    public MenuItems getInterfaceContextMenu(Record iface) {
        return new InterfaceContextMenu(iface);
    }

    public MenuItems getDeletedInterfaceContextMenu(Record iface) {
        return new InterfaceContextMenu(iface);
    }

    public MenuItems getPhysicalBackgroundMenu() {
        return new ItemBackgroundContextMenu();
    }

    class ItemBackgroundContextMenu implements MenuItems {

        @Override
        public Stream<MenuItems.MenuItem> items(InteractionContext context) {
            return Stream.of(new MenuItems.SingleMenuItem(
                    "New Item...",
                    hints -> {
                        physicalInteractions.createItem(
                                context,
                                hints.getLocationHint().orElse(ItemView.DEFAULT_ORIGIN));
                    }));
        }
    }

    class ItemContextMenu implements MenuItems {

        public ItemContextMenu(Record item) {
            this.item = item;
        }

        private final Record item;

        @Override
        public Stream<MenuItems.MenuItem> items(InteractionContext context) {
            if (item.isExternal()) {
                return Stream.of(new MenuItems.SingleMenuItem(
                        "Delete External Item",
                        () -> {
                            context.updateChild(child -> child.remove(item.getIdentifier()));
                        }));
            } else {
                return Stream.of(
                        //                        new MenuItems.SingleMenuItem(
                        //                                "Add Function",
                        //                                () -> {
                        //                                    // context.updateChild(child -> child.remove(item.getIdentifier()));
                        //                                }),
                        new MenuItems.SingleMenuItem(
                                "Rename Item...",
                                () -> {
                                    physicalInteractions.setItemName(context, item);
                                }),
                        new MenuItems.SingleMenuItem(
                                "Set Color...",
                                () -> {
                                    physicalInteractions.setItemColor(context, item);
                                }),
                        new MenuItems.SingleMenuItem(
                                "Navigate Down",
                                () -> {
                                    context.navigateDown(item, Optional.empty());
                                }),
                        new MenuItems.SingleMenuItem(
                                "Delete Item",
                                () -> {
                                    context.updateChild(child -> child.remove(item.getIdentifier()));
                                }));

            }
        }
    }

    class DeletedItemContextMenu implements MenuItems {

        public DeletedItemContextMenu(Record item) {
            this.item = item;
        }

        private final Record item;

        @Override
        public Stream<MenuItems.MenuItem> items(InteractionContext context) {
            return Stream.of(new MenuItems.SingleMenuItem(
                    "Restore Item",
                    () -> {
                        context.restoreDeleted(item);
                    }));
        }
    }

    class InterfaceContextMenu implements MenuItems {

        public InterfaceContextMenu(Record iface) {
            this.iface = iface;
        }

        private final Record iface;

        @Override
        public Stream<MenuItems.MenuItem> items(InteractionContext context) {
            return Stream.of(new MenuItems.SingleMenuItem(
                    "Delete Interface",
                    () -> {
                        context.updateChild(child -> child.remove(iface.getIdentifier()));
                    }));
        }
    }

    class DeletedInterfaceContextMenu implements MenuItems {

        public DeletedInterfaceContextMenu(Record iface) {
            this.iface = iface;
        }

        private final Record iface;

        @Override
        public Stream<MenuItems.MenuItem> items(InteractionContext context) {
            return Stream.of(new MenuItems.SingleMenuItem(
                    "Restore Interface",
                    () -> context.restoreDeleted(iface)));
        }
    }
}
