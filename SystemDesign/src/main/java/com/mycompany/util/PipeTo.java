/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 *
 * @author fuzzy
 */
public class PipeTo {

	private final File target;
	private final Process exec;

	public PipeTo(File target, Process exec) {
		this.target = target;
		this.exec = exec;
	}

	public OutputStream start() throws FileNotFoundException {
		new CopyStream(System.err, exec.getErrorStream()).start();
		new CopyStream(new FileOutputStream(target), exec.getInputStream()).start();
		return exec.getOutputStream();
	}
}
