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
package au.id.soundadvice.systemdesign.beans;

import au.id.soundadvice.systemdesign.files.Identifiable;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class InterfaceBean implements Identifiable {

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public void setParentInterface(UUID parentInterface) {
        this.parentInterface = parentInterface;
    }

    public void setLeftItem(UUID leftItem) {
        this.leftItem = leftItem;
    }

    public void setRightItem(UUID rightItem) {
        this.rightItem = rightItem;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Objects.hashCode(this.uuid);
        hash = 89 * hash + Objects.hashCode(this.parentInterface);
        hash = 89 * hash + Objects.hashCode(this.leftItem);
        hash = 89 * hash + Objects.hashCode(this.rightItem);
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
        final InterfaceBean other = (InterfaceBean) obj;
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.parentInterface, other.parentInterface)) {
            return false;
        }
        if (!Objects.equals(this.leftItem, other.leftItem)) {
            return false;
        }
        if (!Objects.equals(this.rightItem, other.rightItem)) {
            return false;
        }
        return true;
    }

    public UUID getUuid() {
        return uuid;
    }

    public UUID getParentInterface() {
        return parentInterface;
    }

    public UUID getLeftItem() {
        return leftItem;
    }

    public UUID getRightItem() {
        return rightItem;
    }

    public String getName() {
        return name;
    }

    public InterfaceBean(UUID uuid, UUID parentInterface, UUID leftItem, UUID rightItem, String name) {
        this.uuid = uuid;
        this.parentInterface = parentInterface;
        // Normalise left/right
        if (leftItem.compareTo(rightItem) < 0) {
            this.leftItem = leftItem;
            this.rightItem = rightItem;
        } else {
            this.leftItem = rightItem;
            this.rightItem = leftItem;
        }
        this.name = name;
    }

    public InterfaceBean() {
    }

    private UUID uuid;
    @Nullable
    private UUID parentInterface;
    private UUID leftItem;
    private UUID rightItem;
    // Strictly for descriptive purposes within the file
    private String name;
}
