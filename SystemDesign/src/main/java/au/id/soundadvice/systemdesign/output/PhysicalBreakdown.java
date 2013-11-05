/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.output;

import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import java.io.IOException;
import java.io.PrintStream;

/**
 *
 * @author fuzzy
 */
public class PhysicalBreakdown {

	public static void write(PrintStream stream, Item item, boolean includeInterfaces) throws IOException {
		stream.println("digraph G {");
		stream.println("node [shape=record];");
		writeTraces(stream, item, includeInterfaces);
		stream.println("}");
	}

	public static void writeTraces(PrintStream stream, Item parent, boolean includeInterfaces) throws IOException {
		for (Item subsystem : parent.getChildren().values()) {
			stream.print('"');
			stream.print(parent.toString());
			stream.print("\" -> \"");
			stream.print(subsystem.toString());
			stream.println("\"");
			writeTraces(stream, subsystem, includeInterfaces);
		}
		if (includeInterfaces) {
			for (Interface iface : parent.getSubInterfaces().values()) {
				stream.print('"');
				stream.print(parent.toString());
				stream.print("\" -> \"");
				stream.print(iface.toString());
				stream.println("\"");
				writeTraces(stream, iface);
			}
		}
	}

	public static void writeTraces(PrintStream stream, Interface parent) throws IOException {
		for (Interface iface : parent.getChildren().values()) {
			stream.print('"');
			stream.print(parent.toString());
			stream.print("\" -> \"");
			stream.print(iface.toString());
			stream.println("\"");
			writeTraces(stream, iface);
		}
	}
}
