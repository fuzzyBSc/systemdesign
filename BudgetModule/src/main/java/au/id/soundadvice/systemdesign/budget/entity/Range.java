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
package au.id.soundadvice.systemdesign.budget.entity;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Objects;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Range {

    private static final char PLUS_MINUS = '\u00b1';

    public static Range valueOf(String text) throws ParseException {
        int splitPos = text.indexOf(PLUS_MINUS);
        int splitEndPos = splitPos < 0 ? -1 : splitPos + 1;
        if (splitPos < 0) {
            splitPos = text.indexOf("+/-");
            splitEndPos = splitPos < 0 ? -1 : splitPos + 3;
        }

        DecimalFormat parser = (DecimalFormat) NumberFormat.getNumberInstance();
        parser.setParseBigDecimal(true);
        if (splitPos >= 0) {
            return Range.fromValueWithError(
                    (BigDecimal) parser.parseObject(text.substring(0, splitPos)),
                    (BigDecimal) parser.parseObject(text.substring(splitEndPos)));
        } else {
            return Range.fromExact((BigDecimal) parser.parseObject(text));
        }
    }

    public boolean isPositive() {
        return getValue().compareTo(BigDecimal.ZERO) >= 0;
    }

    public boolean isExactZero() {
        return minimum.compareTo(BigDecimal.ZERO) == 0
                && maximum.compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 59 * hash + Objects.hashCode(this.minimum);
        hash = 59 * hash + Objects.hashCode(this.maximum);
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
        final Range other = (Range) obj;
        if (!Objects.equals(this.minimum, other.minimum)) {
            return false;
        }
        if (!Objects.equals(this.maximum, other.maximum)) {
            return false;
        }
        return true;
    }

    public static Range ZERO = fromExact(BigDecimal.ZERO);

    @Override
    public String toString() {
        if (isExact()) {
            return maximum.toString();
        } else {
            return "" + getValue() + PLUS_MINUS + getError();
        }
    }

    public static Range fromExact(BigDecimal value) {
        return new Range(value, value);
    }

    public static Range fromRange(BigDecimal minimum, BigDecimal maximum) {
        if (minimum.compareTo(maximum) < 0) {
            return new Range(minimum, maximum);
        } else {
            // Reverse
            return new Range(maximum, minimum);
        }
    }

    public static Range fromValueWithError(BigDecimal value, BigDecimal error) {
        // Only permit positive error value
        error = error.abs();
        return new Range(value.subtract(error), value.add(error));
    }

    public boolean isExact() {
        return minimum.compareTo(maximum) == 0;
    }

    public BigDecimal getMinimum() {
        return minimum;
    }

    public BigDecimal getMaximum() {
        return maximum;
    }

    public BigDecimal getValue() {
        return minimum.add(maximum).divide(BigDecimal.valueOf(2));
    }

    public BigDecimal getError() {
        return maximum.subtract(getMinimum()).divide(BigDecimal.valueOf(2));
    }

    @CheckReturnValue
    public Range setMinimum(BigDecimal minimum) {
        if (this.minimum.compareTo(minimum) == 0) {
            return this;
        } else {
            return fromRange(minimum, maximum);
        }
    }

    @CheckReturnValue
    public Range setMaximum(BigDecimal maximum) {
        if (this.maximum.compareTo(maximum) == 0) {
            return this;
        } else {
            return fromRange(minimum, maximum);
        }
    }

    @CheckReturnValue
    public Range setValue(BigDecimal value) {
        BigDecimal error = getError();
        return fromValueWithError(value, error);
    }

    @CheckReturnValue
    public Range setError(BigDecimal error) {
        BigDecimal value = getValue();
        return fromValueWithError(value, error);
    }

    private Range(BigDecimal minimum, BigDecimal maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public Range abs() {
        if (isPositive()) {
            return this;
        } else {
            return this.negate();
        }
    }

    public Range negate() {
        return fromRange(minimum.negate(), maximum.negate());
    }

    public Range add(Range other) {
        BigDecimal max = this.maximum.add(other.maximum);
        BigDecimal min = this.minimum.add(other.minimum);
        return fromRange(min, max);
    }

    public Range subtract(Range other) {
        BigDecimal max = this.maximum.subtract(other.maximum);
        BigDecimal min = this.minimum.subtract(other.minimum);
        return fromRange(min, max);
    }

    private final BigDecimal minimum;
    private final BigDecimal maximum;
}
