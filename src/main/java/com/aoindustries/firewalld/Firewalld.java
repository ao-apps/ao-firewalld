/*
 * ao-firewalld - Java API for managing firewalld.
 * Copyright (C) 2017  AO Industries, Inc.
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
 * along with ao-firewalld.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.firewalld;

import com.aoindustries.lang.ProcessResult;
import java.io.IOException;

/**
 * Wraps functions of the <code>firewall-cmd</code> commands.
 *
 * @author  AO Industries, Inc.
 */
// TODO: Not public once another class exists for javadocs
public class Firewalld {

	/**
	 * The full path to the <code>firewall-cmd</code> executable.
	 */
	private static final String FIREWALL_CMD_EXE = "/usr/bin/firewall-cmd";

	/**
	 * Serializes access to the underlying <code>firewall-cmd</code> command.
	 */
	private static class FirewallCmdLock {}
	static final FirewallCmdLock firewallCmdLock = new FirewallCmdLock();

	/**
	 * Calls the <code>firewall-cmd</code> command with the given arguments
	 *
	 * @throws  IOException  when I/O exception or non-zero exit value
	 */
	static ProcessResult execFirewallCmd(String ... args) throws IOException {
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
	 * Make no instances.
	 */
	private Firewalld() {
	}
}
