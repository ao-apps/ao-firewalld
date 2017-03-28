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

import com.aoindustries.net.Port;
import com.aoindustries.net.PortRange;
import com.aoindustries.net.Protocol;
import com.aoindustries.validation.ValidationException;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import static org.junit.Assert.*;

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

	private static final String[] centos7TestFiles = {
		"amanda-client.xml",
		"amanda-k5-client.xml",
		"aoserv-daemon.xml",
		"bacula-client.xml",
		"bacula.xml",
		"ceph-mon.xml",
		"ceph.xml",
		"dhcpv6-client.xml",
		"dhcpv6.xml",
		"dhcp.xml",
		"dns.xml",
		"docker-registry.xml",
		"dropbox-lansync.xml",
		"freeipa-ldaps.xml",
		"freeipa-ldap.xml",
		"freeipa-replication.xml",
		"ftp.xml",
		"high-availability.xml",
		"https.xml",
		"http.xml",
		"imaps.xml",
		"imap.xml",
		"ipp-client.xml",
		"ipp.xml",
		"ipsec.xml",
		"iscsi-target.xml",
		"kadmin.xml",
		"kerberos.xml",
		"kpasswd.xml",
		"ldaps.xml",
		"ldap.xml",
		"libvirt-tls.xml",
		"libvirt.xml",
		"mdns.xml",
		"mosh.xml",
		"mountd.xml",
		"ms-wbt.xml",
		"mysql.xml",
		"nfs.xml",
		"ntp.xml",
		"openvpn.xml",
		"pmcd.xml",
		"pmproxy.xml",
		"pmwebapis.xml",
		"pmwebapi.xml",
		"pop3s.xml",
		"pop3.xml",
		"postgresql.xml",
		"privoxy.xml",
		"proxy-dhcp.xml",
		"ptp.xml",
		"pulseaudio.xml",
		"puppetmaster.xml",
		"radius.xml",
		"RH-Satellite-6.xml",
		"rpc-bind.xml",
		"rsyncd.xml",
		"samba-client.xml",
		"samba.xml",
		"sane.xml",
		"smtps.xml",
		"smtp.xml",
		"snmptrap.xml",
		"snmp.xml",
		"squid.xml",
		"ssh.xml",
		"synergy.xml",
		"syslog-tls.xml",
		"syslog.xml",
		"telnet.xml",
		"tftp-client.xml",
		"tftp.xml",
		"tinc.xml",
		"tor-socks.xml",
		"transmission-client.xml",
		"vdsm.xml",
		"vnc-server.xml",
		"wbem-https.xml",
		"xmpp-bosh.xml",
		"xmpp-client.xml",
		"xmpp-local.xml",
		"xmpp-server.xml"
	};
	@Test
	public void testLoadService_InputStream() throws ValidationException, IOException {
		for(String centos7TestFile : centos7TestFiles) {
			String resourceName = "centos7/" + centos7TestFile;
			InputStream in = ServiceTest.class.getResourceAsStream(resourceName);
			if(in==null) throw new IOException("Resource not found: " + resourceName);
			try {
				Service.loadService(in);
			} catch(IOException e) {
				throw new IOException("Resource: " + resourceName, e);
			} finally {
				in.close();
			}
		}
	}
}
