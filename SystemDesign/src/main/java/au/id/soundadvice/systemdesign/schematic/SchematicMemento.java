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
package au.id.soundadvice.systemdesign.schematic;

import au.id.soundadvice.systemdesign.model.Item;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class SchematicMemento {

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 13 * hash + Objects.hashCode(this.context);
        hash = 13 * hash + Objects.hashCode(this.items);
        hash = 13 * hash + Objects.hashCode(this.interfaces);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SchematicMemento other = (SchematicMemento) obj;
        if (!Objects.equals(this.context, other.context)) {
            return false;
        }
        if (!Objects.equals(this.items, other.items)) {
            return false;
        }
        if (!Objects.equals(this.interfaces, other.interfaces)) {
            return false;
        }
        return true;
    }

    public Item getContext() {
        return context;
    }

    public List<SchematicItemMemento> getItems() {
        return items;
    }

    public List<SchematicInterfaceMemento> getInterfaces() {
        return interfaces;
    }

    public SchematicMemento(Item context, List<SchematicItemMemento> items, List<SchematicInterfaceMemento> interfaces) {
        this.context = context;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.interfaces = Collections.unmodifiableList(new ArrayList<>(interfaces));
    }

    private final Item context;
    private final List<SchematicItemMemento> items;
    private final List<SchematicInterfaceMemento> interfaces;
}
