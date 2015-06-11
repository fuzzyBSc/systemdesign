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

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FlowBean implements Identifiable {

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Objects.hashCode(this.uuid);
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
        final FlowBean other = (FlowBean) obj;
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.left, other.left)) {
            return false;
        }
        if (!Objects.equals(this.right, other.right)) {
            return false;
        }
        if (this.direction != other.direction) {
            return false;
        }
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        if (!Objects.equals(this.description, other.description)) {
            return false;
        }
        return true;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
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

    public FlowDirection getDirection() {
        return direction;
    }

    public void setDirection(FlowDirection direction) {
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

    public FlowBean(UUID uuid, FlowDirection direction, UUID left, UUID right, String type, String description) {
        this.uuid = uuid;
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
    /**
     * Left function or external item.
     */
    private UUID left;
    /**
     * Right function or external item.
     */
    private UUID right;
    private FlowDirection direction;
    private String type;
    private String description;
}
