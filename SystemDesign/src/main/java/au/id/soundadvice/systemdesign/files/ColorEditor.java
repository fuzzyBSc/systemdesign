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
import javafx.scene.paint.Color;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class ColorEditor implements PropertyEditor {

    Color value;

    @Override
    public void setValue(Object value) {
        this.value = (Color) value;
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
        if (value == null) {
            return "null";
        } else {
            return "Color.valueOf(\"" + value.toString() + "\")";
        }
    }

    @Override
    public String getAsText() {
        return value.toString();
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        try {
            this.value = text == null ? null : Color.valueOf(text);
        } catch (IllegalArgumentException ex) {
            this.value = null;
        }
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
