/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.output;

import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;

/**
 *
 * @author fuzzy
 */
public class PhysicalSchematic {

	public static void write(PrintStream stream, Item context) throws IOException {
		stream.println("graph G {");
		stream.println("node [shape=record];");

		// Distinguish subsystems from external systems
		for (Item subsystem : context.getChildren().values()) {
			stream.print('"');
			stream.print(subsystem.toString().replaceAll("\"", "\\\""));
			stream.print("\" [ style = \"bold\" ]");
		}

		Set<Interface> interfaces = context.getSubsystemInterfaces();
		for (Interface iface : interfaces) {
			stream.print('"');
			stream.print(iface.getLeft().toString().replaceAll("\"", "\\\""));
			stream.print("\" -- \"");
			stream.print(iface.getRight().toString().replaceAll("\"", "\\\""));
			stream.println('"');
		}
		stream.println("}");
	}
}
