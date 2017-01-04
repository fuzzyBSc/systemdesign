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
package au.id.soundadvice.systemdesign.moduleapi.entity;

/**
 * Some vocabulary here is intentionally similar to ReqIF concepts, with the
 * intention that interchange mechanisms may be added in the future.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum References {
    /**
     * For a connection between two records: The record whose identifier is
     * lexicographically less.
     */
    left,
    /**
     * For a connection between two records: The record whose identifier is
     * lexicographically greater.
     */
    right,
    /**
     * A reference to the record that contains this record. For a view this is a
     * reference to the drawing that contains the view. For a function, this is
     * the item. For a flow this is the interface.
     */
    container,
    /**
     * For a view, a reference to the entity this is a view of.
     */
    viewOf,
    /**
     * A secondary type identifier, referring to another specific record within
     * the baseline as the type.
     */
    subtype;

    public static String PREFIX = "ref:";
}
