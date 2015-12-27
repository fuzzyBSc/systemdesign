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
package au.id.soundadvice.systemdesign.physical.beans;

import au.id.soundadvice.systemdesign.moduleapi.Identifiable;
import java.util.UUID;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class InterfaceBean implements Identifiable {

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public void setLeftItem(UUID leftItem) {
        this.leftItem = leftItem;
    }

    public void setRightItem(UUID rightItem) {
        this.rightItem = rightItem;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public UUID getLeftItem() {
        return leftItem;
    }

    public UUID getRightItem() {
        return rightItem;
    }

    public String getDescription() {
        return description;
    }

    public InterfaceBean(UUID uuid, UUID leftItem, UUID rightItem, String description) {
        this.uuid = uuid;
        // Normalise left/right
        if (leftItem.compareTo(rightItem) < 0) {
            this.leftItem = leftItem;
            this.rightItem = rightItem;
        } else {
            this.leftItem = rightItem;
            this.rightItem = leftItem;
        }
        this.description = description;
    }

    public InterfaceBean() {
    }

    private UUID uuid;
    private UUID leftItem;
    private UUID rightItem;
    private String description;
}
