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
package au.id.soundadvice.systemdesign.moduleapi.interaction;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 *
 * @author fuzzy
 */
public interface MenuItems {

    interface MenuItem {

        public String getText();

        public Stream<MenuItem> getChildren();
    }

    static final MenuItems EMPTY = new MenuItems() {
        @Override
        public Stream<MenuItem> items(InteractionContext context) {
            return Stream.empty();
        }
    };

    class SingleMenuItem implements MenuItem, Consumer<MenuHints> {

        public SingleMenuItem(String text, Runnable runnable) {
            this.text = text;
            this.action = hints -> runnable.run();
        }

        public SingleMenuItem(String text, Consumer<MenuHints> action) {
            this.text = text;
            this.action = action;
        }

        @Override
        public String getText() {
            return text;
        }

        private final String text;
        private final Consumer<MenuHints> action;

        @Override
        public Stream<MenuItem> getChildren() {
            return Stream.empty();
        }

        @Override
        public void accept(MenuHints t) {
            action.accept(t);
        }
    }

    class Submenu implements MenuItem {

        public static Submenu of(String text, Stream<MenuItem> items) {
            return new Submenu(text, () -> items);
        }

        public static Submenu of(String text, Supplier<Stream<MenuItem>> items) {
            return new Submenu(text, items);
        }

        private Submenu(String text, Supplier<Stream<MenuItem>> items) {
            this.text = text;
            this.items = items;
        }

        @Override
        public String getText() {
            return text;
        }

        private final String text;
        private final Supplier<Stream<MenuItem>> items;

        @Override
        public Stream<MenuItem> getChildren() {
            return items.get();
        }
    }

    public Stream<MenuItem> items(InteractionContext context);
}
