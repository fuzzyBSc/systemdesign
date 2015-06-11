/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.files;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.beans.PropertyChangeListener;
import java.beans.PropertyEditor;
import java.util.UUID;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class UUIDEditor implements PropertyEditor {

    public UUIDEditor() {
    }
    UUID value;

    @Override
    public void setValue(Object value) {
        this.value = (UUID) value;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public boolean isPaintable() {
        return false;
    }

    @Override
    public void paintValue(Graphics gfx, Rectangle box) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getJavaInitializationString() {
        //            UUID tmp = UUID.fromString(value.toString());
        return "UUID.fromString(\"" + value.toString() + "\")";
    }

    @Override
    public String getAsText() {
        return value.toString();
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        this.value = UUID.fromString(text);
    }

    @Override
    public String[] getTags() {
        return null;
    }

    @Override
    public Component getCustomEditor() {
        return null;
    }

    @Override
    public boolean supportsCustomEditor() {
        return false;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
//    private static class EnumConverter<E extends Enum<E>> implements Converter {
//
//        public EnumConverter(Class<E> type) {
//            this.type = type;
//        }
//        private final Class<E> type;
//
//        @Override
//        public <T> T convert(Class<T> type, Object value) {
//            if (this.type.equals(type)) {
//                try {
//                    return (T) Enum.valueOf(this.type, value.toString());
//                } catch (IllegalArgumentException ex) {
//                    throw new ConversionException(ex);
//                }
//            } else {
//                throw new ConversionException("Unexpected type " + type);
//            }
//        }
//    }
