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
package au.id.soundadvice.systemdesign.logical.beans;

import au.id.soundadvice.systemdesign.moduleapi.Identifiable;
import java.util.Optional;
import java.util.UUID;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FunctionViewBean implements Identifiable {

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getOriginX() {
        return originX;
    }

    public void setOriginX(int originX) {
        this.originX = originX;
    }

    public int getOriginY() {
        return originY;
    }

    public void setOriginY(int originY) {
        this.originY = originY;
    }

    public UUID getDrawing() {
        return drawing;
    }

    public void setDrawing(UUID drawing) {
        this.drawing = drawing;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public void setFunction(UUID function) {
        this.function = function;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public UUID getFunction() {
        return function;
    }

    public FunctionViewBean(UUID uuid, UUID function, String description, Optional<UUID> drawing, int originX, int originY) {
        this.uuid = uuid;
        this.function = function;
        this.description = description;
        this.drawing = drawing.orElse(null);
        this.originX = originX;
        this.originY = originY;
    }

    public FunctionViewBean() {
    }

    private UUID uuid;
    private UUID function;
    private UUID drawing;
    private int originX;
    private int originY;
    private String description;
}
