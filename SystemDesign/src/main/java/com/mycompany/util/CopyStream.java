/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author fuzzy
 */
public class CopyStream {

	private static final Logger LOG = Logger.getLogger(CopyStream.class.getName());
	private final OutputStream to;
	private final InputStream from;

	public CopyStream(OutputStream to, InputStream from) {
		this.to = to;
		this.from = from;
	}

	public void start() {
		new Thread(new Run(), "Copy " + from + " to " + to).start();
	}

	private class Run implements Runnable {

		@Override
		public void run() {
			try {
				for (;;) {
					byte[] bytes = new byte[1024 * 16];
					int readResult = from.read(bytes);
					if (readResult == -1) {
						break;
					}
					to.write(bytes, 0, readResult);
				}
			} catch (RuntimeException ex) {
				LOG.log(Level.SEVERE, "Failed to copy stream: " + ex, ex);
			} catch (IOException ex) {
				LOG.log(Level.WARNING, "Failed to copy stream: " + ex, ex);
			} finally {
				try {
					to.close();
				} catch (IOException ex) {
					LOG.log(Level.WARNING, "Failed to close stream: " + ex, ex);
				}
				try {
					from.close();
				} catch (IOException ex) {
					LOG.log(Level.WARNING, "Failed to close stream: " + ex, ex);
				}
			}
		}
	}
}
