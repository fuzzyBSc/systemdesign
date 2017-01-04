/*
 * To change this license header, choose License Headers in Project Properties.
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
package au.id.soundadvice.systemdesign.moduleapi;

import au.id.soundadvice.systemdesign.moduleapi.entity.Direction;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class DirectionTest {

    /**
     * Test of stream method, of class Direction.
     */
    @Test
    public void testStream() {
        System.out.println("stream");
        List<Direction> list;
        list = Direction.None.stream().collect(Collectors.toList());
        assertTrue(list.isEmpty());
        list = Direction.Forward.stream().collect(Collectors.toList());
        assertEquals(1, list.size());
        assertTrue(list.contains(Direction.Forward));
        list = Direction.Reverse.stream().collect(Collectors.toList());
        assertEquals(1, list.size());
        assertTrue(list.contains(Direction.Reverse));
        list = Direction.Both.stream().collect(Collectors.toList());
        assertEquals(2, list.size());
        assertTrue(list.contains(Direction.Forward));
        assertTrue(list.contains(Direction.Reverse));
    }

    /**
     * Test of reverse method, of class Direction.
     */
    @Test
    public void testReverse() {
        assertEquals(Direction.None, Direction.None.reverse());
        assertEquals(Direction.Forward, Direction.Reverse.reverse());
        assertEquals(Direction.Reverse, Direction.Forward.reverse());
        assertEquals(Direction.Both, Direction.Both.reverse());
    }

    /**
     * Test of add method, of class Direction.
     */
    @Test
    public void testAdd() {
        assertEquals(Direction.None, Direction.None.add(Direction.None));
        assertEquals(Direction.Forward, Direction.None.add(Direction.Forward));
        assertEquals(Direction.Reverse, Direction.None.add(Direction.Reverse));
        assertEquals(Direction.Both, Direction.None.add(Direction.Both));

        assertEquals(Direction.Forward, Direction.Forward.add(Direction.None));
        assertEquals(Direction.Forward, Direction.Forward.add(Direction.Forward));
        assertEquals(Direction.Both, Direction.Forward.add(Direction.Reverse));
        assertEquals(Direction.Both, Direction.Forward.add(Direction.Both));

        assertEquals(Direction.Reverse, Direction.Reverse.add(Direction.None));
        assertEquals(Direction.Both, Direction.Reverse.add(Direction.Forward));
        assertEquals(Direction.Reverse, Direction.Reverse.add(Direction.Reverse));
        assertEquals(Direction.Both, Direction.Reverse.add(Direction.Both));

        assertEquals(Direction.Both, Direction.Both.add(Direction.None));
        assertEquals(Direction.Both, Direction.Both.add(Direction.Forward));
        assertEquals(Direction.Both, Direction.Both.add(Direction.Reverse));
        assertEquals(Direction.Both, Direction.Both.add(Direction.Both));
    }

    /**
     * Test of remove method, of class Direction.
     */
    @Test
    public void testRemove() {
        assertEquals(Direction.None, Direction.None.remove(Direction.None));
        assertEquals(Direction.None, Direction.None.remove(Direction.Forward));
        assertEquals(Direction.None, Direction.None.remove(Direction.Reverse));
        assertEquals(Direction.None, Direction.None.remove(Direction.Both));

        assertEquals(Direction.Forward, Direction.Forward.remove(Direction.None));
        assertEquals(Direction.None, Direction.Forward.remove(Direction.Forward));
        assertEquals(Direction.Forward, Direction.Forward.remove(Direction.Reverse));
        assertEquals(Direction.None, Direction.Forward.remove(Direction.Both));

        assertEquals(Direction.Reverse, Direction.Reverse.remove(Direction.None));
        assertEquals(Direction.Reverse, Direction.Reverse.remove(Direction.Forward));
        assertEquals(Direction.None, Direction.Reverse.remove(Direction.Reverse));
        assertEquals(Direction.None, Direction.Reverse.remove(Direction.Both));

        assertEquals(Direction.Both, Direction.Both.remove(Direction.None));
        assertEquals(Direction.Reverse, Direction.Both.remove(Direction.Forward));
        assertEquals(Direction.Forward, Direction.Both.remove(Direction.Reverse));
        assertEquals(Direction.None, Direction.Both.remove(Direction.Both));
    }

    /**
     * Test of contains method, of class Direction.
     */
    @Test
    public void testContains() {
        assertTrue(Direction.None.contains(Direction.None));
        assertFalse(Direction.None.contains(Direction.Forward));
        assertFalse(Direction.None.contains(Direction.Reverse));
        assertFalse(Direction.None.contains(Direction.Both));

        assertTrue(Direction.Forward.contains(Direction.None));
        assertTrue(Direction.Forward.contains(Direction.Forward));
        assertFalse(Direction.Forward.contains(Direction.Reverse));
        assertFalse(Direction.Forward.contains(Direction.Both));

        assertTrue(Direction.Reverse.contains(Direction.None));
        assertFalse(Direction.Reverse.contains(Direction.Forward));
        assertTrue(Direction.Reverse.contains(Direction.Reverse));
        assertFalse(Direction.Reverse.contains(Direction.Both));

        assertTrue(Direction.Both.contains(Direction.None));
        assertTrue(Direction.Both.contains(Direction.Forward));
        assertTrue(Direction.Both.contains(Direction.Reverse));
        assertTrue(Direction.Both.contains(Direction.Both));
    }

}
