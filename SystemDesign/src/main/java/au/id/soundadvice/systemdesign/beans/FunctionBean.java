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
public class FunctionBean implements Identifiable {

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public void setItem(UUID item) {
        this.item = item;
    }

    public void setVerb(String verb) {
        this.verb = verb;
    }

    public void setNoun(String noun) {
        this.noun = noun;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 83 * hash + Objects.hashCode(this.uuid);
        hash = 83 * hash + Objects.hashCode(this.item);
        hash = 83 * hash + Objects.hashCode(this.verb);
        hash = 83 * hash + Objects.hashCode(this.noun);
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
        final FunctionBean other = (FunctionBean) obj;
        if (!Objects.equals(this.uuid, other.uuid)) {
            return false;
        }
        if (!Objects.equals(this.item, other.item)) {
            return false;
        }
        if (!Objects.equals(this.verb, other.verb)) {
            return false;
        }
        if (!Objects.equals(this.noun, other.noun)) {
            return false;
        }
        return true;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    public UUID getItem() {
        return item;
    }

    public String getVerb() {
        return verb;
    }

    public String getNoun() {
        return noun;
    }

    public FunctionBean(UUID uuid, UUID item, String verb, String noun) {
        this.uuid = uuid;
        this.item = item;
        this.verb = verb;
        this.noun = noun;
    }

    public FunctionBean() {
    }

    private UUID uuid;
    private UUID item;
    private String verb;
    private String noun;
}
