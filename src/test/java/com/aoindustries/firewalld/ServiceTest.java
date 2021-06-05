/*
 * ao-firewalld - Java API for managing firewalld.
 * Copyright (C) 2017, 2019, 2020, 2021  AO Industries, Inc.
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

import com.aoapps.lang.validation.ValidationException;
import com.aoapps.net.IPortRange;
import com.aoapps.net.InetAddressPrefixes;
import com.aoapps.net.Port;
import com.aoapps.net.PortRange;
import com.aoapps.net.Protocol;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @see  Service
 *
 * @author  AO Industries, Inc.
 */
public class ServiceTest {

	@Test
	public void testParseProtocol() {
		assertEquals(
			Protocol.TCP,
			Service.parseProtocol("tcp")
		);
		assertEquals(
			Protocol.UDP,
			Service.parseProtocol("udp")
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParseProtocolBad1() {
		Service.parseProtocol("THIS-SHOULD_FAIL");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParseProtocolBad2() {
		Service.parseProtocol(null);
	}

	@Test
	public void testParsePort1() throws ValidationException {
		assertEquals(
			Port.valueOf(1, Protocol.UDP),
			Service.parsePort("1", Protocol.UDP)
		);
	}

	@Test
	public void testParsePort2() throws ValidationException {
		assertEquals(
			Port.valueOf(1, Protocol.UDP),
			Service.parsePort("1-1", Protocol.UDP)
		);
	}

	@Test
	public void testParsePort3() throws ValidationException {
		assertEquals(
			PortRange.valueOf(1, 2, Protocol.TCP),
			Service.parsePort("1-2", Protocol.TCP)
		);
	}

	@Test(expected = NumberFormatException.class)
	public void testParsePort4() throws ValidationException {
		Service.parsePort("BAD-VALUE", Protocol.TCP);
	}

	@Test(expected = ValidationException.class)
	public void testParsePort5() throws ValidationException {
		Service.parsePort("4-566", null);
	}

	@Test(expected = ValidationException.class)
	public void testParsePort6() throws ValidationException {
		Service.parsePort("1--10000", Protocol.TCP);
	}

	private static final String[] centos7TestServices = {
		"amanda-client",
		"amanda-k5-client",
		"aoserv-daemon",
		"bacula-client",
		"bacula",
		"ceph-mon",
		"ceph",
		"dhcpv6-client",
		"dhcpv6",
		"dhcp",
		"dns",
		"docker-registry",
		"dropbox-lansync",
		"freeipa-ldaps",
		"freeipa-ldap",
		"freeipa-replication",
		"ftp",
		"high-availability",
		"https",
		"http",
		"imaps",
		"imap",
		"ipp-client",
		"ipp",
		"ipsec",
		"iscsi-target",
		"kadmin",
		"kerberos",
		"kpasswd",
		"ldaps",
		"ldap",
		"libvirt-tls",
		"libvirt",
		"mdns",
		"mosh",
		"mountd",
		"ms-wbt",
		"mysql",
		"nfs",
		"ntp",
		"openvpn",
		"pmcd",
		"pmproxy",
		"pmwebapis",
		"pmwebapi",
		"pop3s",
		"pop3",
		"postgresql",
		"privoxy",
		"proxy-dhcp",
		"ptp",
		"pulseaudio",
		"puppetmaster",
		"radius",
		"RH-Satellite-6",
		"rpc-bind",
		"rsyncd",
		"samba-client",
		"samba",
		"sane",
		"smtps",
		"smtp",
		"snmptrap",
		"snmp",
		"squid",
		"ssh",
		"synergy",
		"syslog-tls",
		"syslog",
		"telnet",
		"tftp-client",
		"tftp",
		"tinc",
		"tor-socks",
		"transmission-client",
		"vdsm",
		"vnc-server",
		"wbem-https",
		"xmpp-bosh",
		"xmpp-client",
		"xmpp-local",
		"xmpp-server"
	};
	static Service loadCentos7TestService(String name) throws IOException {
		String resourceName = "centos7/" + name + Service.EXTENSION;
		InputStream in = ServiceTest.class.getResourceAsStream(resourceName);
		if(in==null) throw new IOException("Resource not found: " + resourceName);
		try {
			return Service.loadService(name, in);
		} catch(IOException e) {
			throw new IOException("Resource: " + resourceName, e);
		} finally {
			in.close();
		}
	}

	@Test
	public void testLoadService_InputStream() throws IOException {
		for(String centos7TestService : centos7TestServices) {
			loadCentos7TestService(centos7TestService);
		}
	}

	@Test
	public void testToString1() throws IOException {
		assertEquals(
			"shortName must override name",
			"Red Hat Satellite 6",
			loadCentos7TestService("RH-Satellite-6").toString()
		);
	}

	// Java 1.8: Can inline
	static final Set<? extends IPortRange> EMPTY_PORTS = Collections.emptySet();

	// Java 1.8: Can inline
	static final Set<String> EMPTY_MODULES = Collections.emptySet();

	@Test
	public void testToString2() throws IOException {
		assertEquals(
			"name used when no shortName",
			"test",
			new Service(
				"test",
				null, // version
				null, // shortName
				null, // description
				EMPTY_PORTS, // ports
				EnumSet.of(Protocol.ESP), // protocols
				EMPTY_PORTS, // sourcePorts
				EMPTY_MODULES, // modules
				InetAddressPrefixes.UNSPECIFIED_IPV4,
				InetAddressPrefixes.UNSPECIFIED_IPV6
			).toString()
		);
	}

	@Test
	public void testEquals1() throws IOException, ValidationException {
		assertEquals(
			"port order must not matter",
			new Service(
				"test",
				null, // version
				null, // shortName
				null, // description
				new LinkedHashSet<>(
					Arrays.asList(
						(IPortRange)PortRange.valueOf(80, 81, Protocol.TCP),
						Port.valueOf(22, Protocol.TCP)
					)
				), // ports
				EnumSet.noneOf(Protocol.class), // protocols
				EMPTY_PORTS, // sourcePorts
				EMPTY_MODULES, // modules
				InetAddressPrefixes.UNSPECIFIED_IPV4,
				InetAddressPrefixes.UNSPECIFIED_IPV6
			),
			new Service(
				"test",
				null, // version
				null, // shortName
				null, // description
				new LinkedHashSet<>(
					Arrays.asList(
						(IPortRange)Port.valueOf(22, Protocol.TCP),
						PortRange.valueOf(80, 81, Protocol.TCP)
					)
				), // ports
				EnumSet.noneOf(Protocol.class), // protocols
				EMPTY_PORTS, // sourcePorts
				EMPTY_MODULES, // modules
				InetAddressPrefixes.UNSPECIFIED_IPV4,
				InetAddressPrefixes.UNSPECIFIED_IPV6
			)
		);
	}
}
