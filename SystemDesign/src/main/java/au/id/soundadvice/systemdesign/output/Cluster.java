/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.output;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author fuzzy
 */
public class Cluster {

	@Override
	public String toString() {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		try (PrintStream stream = new PrintStream(byteStream)) {
			this.write(stream);
		}
		return byteStream.toString();
	}

	public Cluster(String label, String style) {
		this.label = label;
		this.style = style;
	}
	private final String label;
	private final String style;
	private final Set<String> nodes =
			Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

	void add(String node) {
		nodes.add(node);
	}

	void write(PrintStream stream) {
		// Distinguish system functions from external systems
		stream.println("subgraph \"cluster_" + label.replaceAll("\"", "\\\"") + "\" {");
		stream.println(style);
		for (String node : nodes) {
			stream.print('"');
			stream.print(node.replaceAll("\"", "\\\""));
			stream.println("\" [ style = \"bold\" ]");
		}
		stream.println("label = \"" + label + "\"");
		stream.println("}");
	}
}
