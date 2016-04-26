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

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class InterfaceBean implements Identifiable {

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public void setLeftItem(String leftItem) {
        this.leftItem = leftItem;
    }

    public void setRightItem(String rightItem) {
        this.rightItem = rightItem;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    public String getLeftItem() {
        return leftItem;
    }

    public String getRightItem() {
        return rightItem;
    }

    public String getDescription() {
        return description;
    }

    public InterfaceBean(String identifier, String leftItem, String rightItem, String description) {
        this.identifier = identifier;
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

    private String identifier;
    private String leftItem;
    private String rightItem;
    private String description;
}
