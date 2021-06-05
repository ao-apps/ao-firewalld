/*
 * ao-firewalld - Java API for managing firewalld.
 * Copyright (C) 2017, 2020, 2021  AO Industries, Inc.
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
import com.aoapps.net.InetAddress;
import com.aoapps.net.InetAddressPrefix;
import com.aoapps.net.Port;
import com.aoapps.net.PortRange;
import com.aoapps.net.Protocol;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @see  Target
 *
 * @author  AO Industries, Inc.
 */
public class TargetTest {

	@Test
	public void testToString_protocol_singleAddress() throws ValidationException {
		assertEquals(
			"UDP@192.0.2.123",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.123"),
					32
				),
				Protocol.UDP
			).toString()
		);
	}

	@Test
	public void testToString_protocol_addressRange() throws ValidationException {
		assertEquals(
			"UDP@192.0.2.0/24",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.0"),
					24
				),
				Protocol.UDP
			).toString()
		);
	}

	@Test
	public void testToString_port_singleAddress() throws ValidationException {
		assertEquals(
			"53/UDP@192.0.2.123",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.123"),
					32
				),
				Port.valueOf(53, Protocol.UDP)
			).toString()
		);
	}

	@Test
	public void testToString_port_addressRange() throws ValidationException {
		assertEquals(
			"53/UDP@192.0.2.0/24",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.0"),
					24
				),
				Port.valueOf(53, Protocol.UDP)
			).toString()
		);
	}

	@Test
	public void testToString_portRange_singleAddress() throws ValidationException {
		assertEquals(
			"80-81/TCP@192.0.2.123",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.123"),
					32
				),
				PortRange.valueOf(80, 81, Protocol.TCP)
			).toString()
		);
	}

	@Test
	public void testToString_portRange_addressRange() throws ValidationException {
		assertEquals(
			"80-81/TCP@192.0.2.0/24",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.0"),
					24
				),
				PortRange.valueOf(80, 81, Protocol.TCP)
			).toString()
		);
	}

	@Test
	public void testCompareTo_destinationFirst() throws ValidationException {
		assertTrue(
			"Must be ordered by destination first",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.1"),
					32
				),
				Port.valueOf(100, Protocol.TCP)
			).compareTo(new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.2"),
					32
				),
				Port.valueOf(80, Protocol.TCP)
			)) < 0
		);
	}

	@Test
	public void testCompareTo_portRange_before_protocolOnly() throws ValidationException {
		assertTrue(
			"Port ranges must be before protocol-only",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.1"),
					32
				),
				Port.valueOf(53, Protocol.UDP)
			).compareTo(new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.1"),
					32
				),
				Protocol.UDP
			)) < 0
		);
	}

	@Test
	public void testCompareTo_protocolOnly_after_portRange() throws ValidationException {
		assertTrue(
			"Protocol-only must be after port ranges",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.1"),
					32
				),
				Protocol.UDP
			).compareTo(new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.1"),
					32
				),
				Port.valueOf(53, Protocol.UDP)
			)) > 0
		);
	}

	@Test
	public void testCompareTo_protocolOnly() throws ValidationException {
		assertTrue(
			"TCP should be before UDP, following default protocol ordering",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.1"),
					32
				),
				Protocol.TCP
			).compareTo(new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.1"),
					32
				),
				Protocol.UDP
			)) < 0
		);
	}

	@Test
	public void testCompareTo_portRange_sameProtocol() throws ValidationException {
		assertTrue(
			"Expect port 80/TCP before 80-81/TCP",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.1"),
					32
				),
				Port.valueOf(80, Protocol.TCP)
			).compareTo(new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.1"),
					32
				),
				PortRange.valueOf(80, 81, Protocol.TCP)
			)) < 0
		);
	}

	@Test
	public void testCompareTo_portRange_differentProtocol() throws ValidationException {
		assertTrue(
			"Expect port 80/TCP before 80/UDP",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.1"),
					32
				),
				Port.valueOf(80, Protocol.TCP)
			).compareTo(new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.1"),
					32
				),
				Port.valueOf(80, Protocol.UDP)
			)) < 0
		);
	}

	@Test
	public void test_destination_autoNormalized_protocolOnly() throws ValidationException {
		assertEquals(
			"UDP@192.0.2.0/24",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.123"),
					24
				),
				Protocol.UDP
			).toString()
		);
	}

	@Test
	public void test_destination_autoNormalized_portRange() throws ValidationException {
		assertEquals(
			"80-81/TCP@192.0.2.0/24",
			new Target(
				InetAddressPrefix.valueOf(
					InetAddress.valueOf("192.0.2.123"),
					24
				),
				PortRange.valueOf(80, 81, Protocol.TCP)
			).toString()
		);
	}

	@Test
	public void test_coalesce_sameDestination_tcpRange() throws ValidationException {
		assertEquals(
			"80-83/TCP@192.0.2.0/24",
			new Target(
				InetAddressPrefix.valueOf("192.0.2.123/24"),
				PortRange.valueOf(80, 81, Protocol.TCP)
			).coalesce(new Target(
				InetAddressPrefix.valueOf("192.0.2.123/24"),
				PortRange.valueOf(81, 83, Protocol.TCP)
			)).toString()
		);
	}

	@Test
	public void test_coalesce_sameDestination_tcpAll_1() throws ValidationException {
		assertEquals(
			"TCP@192.0.2.0/24",
			new Target(
				InetAddressPrefix.valueOf("192.0.2.123/24"),
				PortRange.valueOf(80, 81, Protocol.TCP)
			).coalesce(new Target(
				InetAddressPrefix.valueOf("192.0.2.123/24"),
				Protocol.TCP
			)).toString()
		);
	}

	@Test
	public void test_coalesce_sameDestination_tcpAll_2() throws ValidationException {
		assertEquals(
			"TCP@192.0.2.0/24",
			new Target(
				InetAddressPrefix.valueOf("192.0.2.123/24"),
				Protocol.TCP
			).coalesce(new Target(
				InetAddressPrefix.valueOf("192.0.2.123/24"),
				PortRange.valueOf(80, 81, Protocol.TCP)
			)).toString()
		);
	}

	@Test
	public void test_noCoalesce_sameDestination_tcpRange_gap_1() throws ValidationException {
		assertNull(
			new Target(
				InetAddressPrefix.valueOf("192.0.2.123/24"),
				PortRange.valueOf(80, 81, Protocol.TCP)
			).coalesce(new Target(
				InetAddressPrefix.valueOf("192.0.2.123/24"),
				PortRange.valueOf(83, 85, Protocol.TCP)
			))
		);
	}

	@Test
	public void test_noCoalesce_sameDestination_tcpRange_gap_2() throws ValidationException {
		assertNull(
			new Target(
				InetAddressPrefix.valueOf("192.0.2.123/24"),
				PortRange.valueOf(83, 85, Protocol.TCP)
			).coalesce(new Target(
				InetAddressPrefix.valueOf("192.0.2.123/24"),
				PortRange.valueOf(80, 81, Protocol.TCP)
			))
		);
	}

	@Test
	public void test_noCoalesce_sameDestination_samePort_diffProtocol() throws ValidationException {
		assertNull(
			new Target(
				InetAddressPrefix.valueOf("192.0.2.123/24"),
				Port.valueOf(80, Protocol.TCP)
			).coalesce(new Target(
				InetAddressPrefix.valueOf("192.0.2.123/24"),
				Port.valueOf(80, Protocol.UDP)
			))
		);
	}

	@Test
	public void test_coalesce_samePorts() throws ValidationException {
		assertEquals(
			"80/TCP@192.0.2.0/24",
			new Target(
				InetAddressPrefix.valueOf("192.0.2.1/25"),
				Port.valueOf(80, Protocol.TCP)
			).coalesce(new Target(
				InetAddressPrefix.valueOf("192.0.2.221/25"),
				Port.valueOf(80, Protocol.TCP)
			)).toString()
		);
	}

	@Test
	public void test_noCoalesce_samePorts() throws ValidationException {
		assertNull(
			new Target(
				InetAddressPrefix.valueOf("192.0.2.1/25"),
				Port.valueOf(80, Protocol.TCP)
			).coalesce(new Target(
				InetAddressPrefix.valueOf("192.0.1.221/25"),
				Port.valueOf(80, Protocol.TCP)
			))
		);
	}
}
