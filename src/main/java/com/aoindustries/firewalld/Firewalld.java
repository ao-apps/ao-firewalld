/*
 * ao-firewalld - Java API for managing firewalld.
 * Copyright (C) 2017, 2019, 2020, 2021, 2022  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-firewalld.
 *
 * ao-firewalld is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-firewalld is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-firewalld.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoindustries.firewalld;

import com.aoapps.collections.AoCollections;
import com.aoapps.lang.ProcessResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps functions of the <code>firewall-cmd</code> commands.
 *
 * @author  AO Industries, Inc.
 */
final class Firewalld {

	/** Make no instances. */
	private Firewalld() {throw new AssertionError();}

	private static final Logger logger = Logger.getLogger(Firewalld.class.getName());

	/**
	 * The full path to the <code>firewall-cmd</code> executable.
	 */
	private static final String FIREWALL_CMD_EXE = "/usr/bin/firewall-cmd";

	/**
	 * Serializes access to the underlying <code>firewall-cmd</code> command.
	 */
	private static class FirewallCmdLock {/* Empty lock class to help heap profile */}
	static final FirewallCmdLock firewallCmdLock = new FirewallCmdLock();

	/**
	 * Calls the <code>firewall-cmd</code> command with the given arguments
	 *
	 * @throws  IOException  when I/O exception or non-zero exit value
	 */
	private static ProcessResult execFirewallCmd(String ... args) throws IOException {
		String[] command = new String[1 + args.length];
		command[0] = FIREWALL_CMD_EXE;
		System.arraycopy(args, 0, command, 1, args.length);
		ProcessResult result;
		synchronized(firewallCmdLock) {
			result = ProcessResult.exec(command);
		}
		if(result.getExitVal() != 0) throw new IOException(result.getStderr());
		return result;
	}

	/**
	 * Gets the listing of services enabled on a per-zone basis.
	 */
	static Map<String, Set<String>> listAllServices() throws IOException {
		ProcessResult result = execFirewallCmd("--permanent", "--list-all-zones");
		Map<String, Set<String>> allServices = new LinkedHashMap<>();
		try (BufferedReader in = new BufferedReader(new StringReader(result.getStdout()))) {
			String currentZone = null;
			String line;
			while((line = in.readLine()) != null) {
				if(!line.trim().isEmpty()) {
					if(!line.startsWith("  ")) {
						// Might be followed by " (active)"
						currentZone = line;
						if(currentZone.endsWith(" (active)")) {
							currentZone = currentZone.substring(0, currentZone.length() - " (active)".length());
						}
					} else if(line.startsWith("  services:")) {
						if(currentZone == null) throw new IOException("currentZone not set");
						Set<String> zoneServices = new LinkedHashSet<>();
						for(String service : line.substring("  services:".length()).trim().split(" ")) {
							if(!service.isEmpty()) {
								if(!zoneServices.add(service)) throw new IOException("Duplicate service: " + service);
							}
						}
						if(
							allServices.put(
								currentZone,
								AoCollections.optimalUnmodifiableSet(zoneServices)
							) != null
						) {
							throw new IOException("Duplicate zone: " + currentZone);
						}
					}
				}
			}
		}
		if(logger.isLoggable(Level.FINER)) logger.finer("Got allServices: " + allServices);
		return AoCollections.optimalUnmodifiableMap(allServices);
	}

	static void addServices(String zone, Set<String> toAdd) throws IOException {
		if(toAdd.isEmpty()) throw new IllegalArgumentException("toAdd is empty");
		if(logger.isLoggable(Level.FINE)) logger.fine("Adding services: zone=" + zone + ", toAdd=" + toAdd);
		String[] command = new String[2 + toAdd.size()];
		command[0] = "--permanent";
		command[1] = "--zone="+zone;
		int pos = 2;
		for(String service : toAdd) {
			command[pos++] = "--add-service=" + service;
		}
		if(pos != command.length) throw new ConcurrentModificationException();
		execFirewallCmd(command);
	}

	static void removeServices(String zone, Set<String> toRemove) throws IOException {
		if(toRemove.isEmpty()) throw new IllegalArgumentException("toRemove is empty");
		if(logger.isLoggable(Level.FINE)) logger.fine("Removing services: zone=" + zone + ", toRemove=" + toRemove);
		String[] command = new String[2 + toRemove.size()];
		command[0] = "--permanent";
		command[1] = "--zone="+zone;
		int pos = 2;
		for(String service : toRemove) {
			command[pos++] = "--remove-service=" + service;
		}
		if(pos != command.length) throw new ConcurrentModificationException();
		execFirewallCmd(command);
	}

	static void reload() throws IOException {
		if(logger.isLoggable(Level.FINE)) logger.fine("Reloading firewall");
		execFirewallCmd("--reload");
	}
}
