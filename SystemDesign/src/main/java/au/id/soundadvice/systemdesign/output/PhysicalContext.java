/*
 * Please refer to the LICENSE file for licensing information.
 */
package au.id.soundadvice.systemdesign.output;

import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalContext {

	public static void write(PrintStream stream, Item item) throws IOException {
		stream.println("graph G {");
		stream.println("node [shape=record];");
		stream.print('"');
		stream.print(item.toString().replaceAll("\"", "\\\""));
		stream.print("\" [ style = \"bold\" ]");
		Set<Interface> interfaces = item.getTrace().getSubsystemInterfaces();
		for (Interface iface : interfaces) {
			Item left = iface.getLeft();
			Item right = iface.getRight();
			if (left == item || right == item) {
				stream.print('"');
				stream.print(iface.getLeft().toString().replaceAll("\"", "\\\""));
				stream.print("\" -- \"");
				stream.print(iface.getRight().toString().replaceAll("\"", "\\\""));
				stream.println('"');
			}
		}
		stream.println("}");
	}
}
