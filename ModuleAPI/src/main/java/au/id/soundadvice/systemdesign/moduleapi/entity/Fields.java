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
public enum Fields {
    /**
     * The date and time this record was last modified (iso8601 format).
     */
    lastChange,
    /**
     * The identifier of this record's trace (optional). This field identifies
     * record in the parent baseline that defines the "why" of this record's
     * existence. If no parent baseline exists this field will be blank.
     */
    trace,
    /**
     * This record's short identifier, for example the IDPath of an Item.
     */
    shortName,
    /**
     * This record's human-entered name (optional).
     */
    longName,
    /**
     * A long description of the record. Record types may use this field for
     * human-entered descriptive text. If automatically filled the record type
     * should prefix the text with a token indicating this.
     */
    desc,
    /**
     * For a connection between two records: The direction of the connection.
     */
    direction,
    /**
     * This entity is either partially or wholly external to the system of
     * interest.
     */
    external,
    /**
     * The intrinsic color of this entity.
     */
    color,
    /**
     * For a view occupying a definite point, the X coordinate within the
     * drawing.
     */
    originX,
    /**
     * For a view occupying a definite point, the X coordinate within the
     * drawing.
     */
    originY,
}
