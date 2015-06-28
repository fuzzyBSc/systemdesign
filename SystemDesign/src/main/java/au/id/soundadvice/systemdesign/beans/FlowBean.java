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
import java.util.UUID;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FlowBean implements Identifiable {

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getInterface() {
        return iface;
    }

    public void setInterface(UUID uuid) {
        this.iface = uuid;
    }

    public UUID getLeft() {
        return left;
    }

    public void setLeft(UUID left) {
        this.left = left;
    }

    public UUID getRight() {
        return right;
    }

    public void setRight(UUID right) {
        this.right = right;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public FlowBean(UUID uuid, UUID iface, Direction direction, UUID left, UUID right, String type, String description) {
        this.uuid = uuid;
        this.iface = iface;
        // Normalise left/right
        if (left.compareTo(right) < 0) {
            this.left = left;
            this.right = right;
            this.direction = direction;
        } else {
            this.left = right;
            this.right = left;
            this.direction = direction.reverse();
        }
        this.type = type;
        this.description = description;
    }

    public FlowBean() {
    }

    private UUID uuid;
    private UUID iface;
    /**
     * Left function or external item.
     */
    private UUID left;
    /**
     * Right function or external item.
     */
    private UUID right;
    private Direction direction;
    private String type;
    private String description;
}
