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
public class FunctionBean implements Identifiable {

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getTrace() {
        return trace;
    }

    public void setTrace(UUID trace) {
        this.trace = trace;
    }

    public boolean isExternal() {
        return external;
    }

    public void setExternal(boolean value) {
        this.external = value;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public void setItem(UUID item) {
        this.item = item;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public UUID getItem() {
        return item;
    }

    public String getName() {
        return name;
    }

    public FunctionBean(UUID uuid, UUID item, String description, Optional<UUID> trace, boolean external, String name) {
        this.uuid = uuid;
        this.item = item;
        this.description = description;
        this.trace = trace.orElse(null);
        this.external = external;
        this.name = name;
    }

    public FunctionBean() {
    }

    private UUID uuid;
    private UUID item;
    private String description;
    private UUID trace;
    private boolean external;
    private String name;
}
