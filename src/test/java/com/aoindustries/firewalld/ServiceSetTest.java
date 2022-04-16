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

import com.aoapps.lang.validation.ValidationException;
import com.aoapps.net.IPortRange;
import com.aoapps.net.InetAddressPrefix;
import com.aoapps.net.InetAddressPrefixes;
import com.aoapps.net.Port;
import com.aoapps.net.PortRange;
import com.aoapps.net.Protocol;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @see  Service
 *
 * @author  AO Industries, Inc.
 */
public class ServiceSetTest {

	@Test
	public void testCreateOptimizedServiceSet_empty() throws IOException {
		Service sshTemplate = ServiceTest.loadCentos7TestService("ssh");
		Iterable<Target> emptyTargets = Collections.emptySet();
		ServiceSet optimized = ServiceSet.createOptimizedServiceSet(
			sshTemplate,
			emptyTargets
		);
		assertEquals(
			"Empty targets must give empty service set",
			Collections.emptySet(),
			optimized.getServices()
		);
	}

	@Test
	public void testCreateOptimizedServiceSet_singlePort_unspecified_IPv4() throws IOException, ValidationException {
		Service sshTemplate = ServiceTest.loadCentos7TestService("ssh");
		ServiceSet optimized = ServiceSet.createOptimizedServiceSet(
			sshTemplate,
			Arrays.asList(
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(22, Protocol.TCP))
			)
		);
		assertEquals(
			"Single port should result in a single service",
			1,
			optimized.getServices().size()
		);
		Service service = optimized.getServices().iterator().next();
		assertEquals(
			"ssh",
			service.getName()
		);
		assertNull(
			service.getVersion()
		);
		assertEquals(
			"Secure Shell (SSH) is a protocol for logging into and executing commands on remote machines. It provides secure encrypted communications. If you plan on accessing your machine remotely via SSH over a firewalled interface, enable this option. You need the openssh-server package installed for this option to be useful.",
			service.getDescription()
		);
		assertEquals(
			Collections.singleton(Port.valueOf(22, Protocol.TCP)),
			service.getPorts()
		);
		assertEquals(
			Collections.emptySet(),
			service.getProtocols()
		);
		assertEquals(
			Collections.emptySet(),
			service.getSourcePorts()
		);
		assertEquals(
			Collections.emptySet(),
			service.getModules()
		);
		assertEquals(
			InetAddressPrefixes.UNSPECIFIED_IPV4,
			service.getDestinationIPv4()
		);
		assertNull(
			service.getDestinationIPv6()
		);
	}

	@Test
	public void testCreateOptimizedServiceSet_singlePort_unspecified_IPv4and6() throws IOException, ValidationException {
		Service sshTemplate = ServiceTest.loadCentos7TestService("ssh");
		ServiceSet optimized = ServiceSet.createOptimizedServiceSet(
			sshTemplate,
			Arrays.asList(
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV6, Port.valueOf(22, Protocol.TCP))
			)
		);
		assertEquals(
			"Single port should result in a single service",
			1,
			optimized.getServices().size()
		);
		Service service = optimized.getServices().iterator().next();
		assertEquals(
			Collections.singleton(Port.valueOf(22, Protocol.TCP)),
			service.getPorts()
		);
		assertEquals(
			Collections.emptySet(),
			service.getProtocols()
		);
		assertEquals(
			InetAddressPrefixes.UNSPECIFIED_IPV4,
			service.getDestinationIPv4()
		);
		assertEquals(
			InetAddressPrefixes.UNSPECIFIED_IPV6,
			service.getDestinationIPv6()
		);
	}

	@Test
	public void testCreateOptimizedServiceSet_adjacentPorts_unspecified_IPv4() throws IOException, ValidationException {
		Service sshTemplate = ServiceTest.loadCentos7TestService("ssh");
		ServiceSet optimized = ServiceSet.createOptimizedServiceSet(
			sshTemplate,
			Arrays.asList(
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(23, Protocol.TCP))
			)
		);
		assertEquals(
			"Adjacent ports should result in a single service",
			1,
			optimized.getServices().size()
		);
		Service service = optimized.getServices().iterator().next();
		assertEquals(
			Collections.singleton(PortRange.valueOf(22, 23, Protocol.TCP)),
			service.getPorts()
		);
		assertEquals(
			Collections.emptySet(),
			service.getProtocols()
		);
		assertEquals(
			InetAddressPrefixes.UNSPECIFIED_IPV4,
			service.getDestinationIPv4()
		);
		assertNull(
			service.getDestinationIPv6()
		);
	}

	@Test
	public void testCreateOptimizedServiceSet_adjacentPorts_unspecified_IPv4and6() throws IOException, ValidationException {
		Service sshTemplate = ServiceTest.loadCentos7TestService("ssh");
		ServiceSet optimized = ServiceSet.createOptimizedServiceSet(
			sshTemplate,
			Arrays.asList(
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(23, Protocol.TCP)),
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV6, Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV6, Port.valueOf(23, Protocol.TCP))
			)
		);
		assertEquals(
			"Adjacent ports should result in a single service",
			1,
			optimized.getServices().size()
		);
		Service service = optimized.getServices().iterator().next();
		assertEquals(
			Collections.singleton(PortRange.valueOf(22, 23, Protocol.TCP)),
			service.getPorts()
		);
		assertEquals(
			Collections.emptySet(),
			service.getProtocols()
		);
		assertEquals(
			InetAddressPrefixes.UNSPECIFIED_IPV4,
			service.getDestinationIPv4()
		);
		assertEquals(
			InetAddressPrefixes.UNSPECIFIED_IPV6,
			service.getDestinationIPv6()
		);
	}

	@Test
	public void testCreateOptimizedServiceSet_adjacentPorts_unspecifiedIPv4_specificIPv6() throws IOException, ValidationException {
		Service sshTemplate = ServiceTest.loadCentos7TestService("ssh");
		ServiceSet optimized = ServiceSet.createOptimizedServiceSet(
			sshTemplate,
			Arrays.asList(
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(23, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), Port.valueOf(23, Protocol.TCP))
			)
		);
		assertEquals(
			"Adjacent ports should result in a single service",
			1,
			optimized.getServices().size()
		);
		Service service = optimized.getServices().iterator().next();
		assertEquals(
			Collections.singleton(PortRange.valueOf(22, 23, Protocol.TCP)),
			service.getPorts()
		);
		assertEquals(
			Collections.emptySet(),
			service.getProtocols()
		);
		assertEquals(
			InetAddressPrefixes.UNSPECIFIED_IPV4,
			service.getDestinationIPv4()
		);
		assertEquals(
			InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"),
			service.getDestinationIPv6()
		);
	}

	@Test
	public void testCreateOptimizedServiceSet_splitPorts_unspecifiedIPv4_specificIPv6() throws IOException, ValidationException {
		Service sshTemplate = ServiceTest.loadCentos7TestService("ssh");
		ServiceSet optimized = ServiceSet.createOptimizedServiceSet(
			sshTemplate,
			Arrays.asList(
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, PortRange.valueOf(45, 100, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), PortRange.valueOf(45, 100, Protocol.TCP))
			)
		);
		assertEquals(
			1,
			optimized.getServices().size()
		);
		Service service = optimized.getServices().iterator().next();
		assertEquals(
			new HashSet<>(
				Arrays.asList(
					(IPortRange)Port.valueOf(22, Protocol.TCP),
					PortRange.valueOf(45, 100, Protocol.TCP)
				)
			),
			service.getPorts()
		);
		assertEquals(
			Collections.emptySet(),
			service.getProtocols()
		);
		assertEquals(
			InetAddressPrefixes.UNSPECIFIED_IPV4,
			service.getDestinationIPv4()
		);
		assertEquals(
			InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"),
			service.getDestinationIPv6()
		);
	}

	@Test
	public void testCreateOptimizedServiceSet_unalignedSplitPorts_unspecifiedIPv4_specificIPv6() throws IOException, ValidationException {
		Service sshTemplate = ServiceTest.loadCentos7TestService("ssh");
		ServiceSet optimized = ServiceSet.createOptimizedServiceSet(
			sshTemplate,
			Arrays.asList(
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, PortRange.valueOf(45, 100, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), PortRange.valueOf(45, 78, Protocol.TCP))
			)
		);
		assertEquals(
			2,
			optimized.getServices().size()
		);
		Iterator<Service> serviceIter = optimized.getServices().iterator();
		{
			Service service1 = serviceIter.next();
			assertEquals(
				new HashSet<>(
					Arrays.asList(
						(IPortRange)Port.valueOf(22, Protocol.TCP),
						PortRange.valueOf(45, 78, Protocol.TCP)
					)
				),
				service1.getPorts()
			);
			assertEquals(
				Collections.emptySet(),
				service1.getProtocols()
			);
			assertNull(
				service1.getDestinationIPv4()
			);
			assertEquals(
				InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"),
				service1.getDestinationIPv6()
			);
		}
		{
			Service service2 = serviceIter.next();
			assertEquals(
				new HashSet<>(
					Arrays.asList(
						(IPortRange)Port.valueOf(22, Protocol.TCP),
						PortRange.valueOf(45, 100, Protocol.TCP)
					)
				),
				service2.getPorts()
			);
			assertEquals(
				Collections.emptySet(),
				service2.getProtocols()
			);
			assertEquals(
				InetAddressPrefixes.UNSPECIFIED_IPV4,
				service2.getDestinationIPv4()
			);
			assertNull(
				service2.getDestinationIPv6()
			);
		}
	}

	@Test
	public void testCreateOptimizedServiceSet_unalignedSplitPorts_unspecifiedIPv4_specificIPv6_reversed() throws IOException, ValidationException {
		Service sshTemplate = ServiceTest.loadCentos7TestService("ssh");
		ServiceSet optimized = ServiceSet.createOptimizedServiceSet(
			sshTemplate,
			Arrays.asList(
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, PortRange.valueOf(45, 78, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), PortRange.valueOf(45, 100, Protocol.TCP))
			)
		);
		assertEquals(
			2,
			optimized.getServices().size()
		);
		Iterator<Service> serviceIter = optimized.getServices().iterator();
		{
			Service service1 = serviceIter.next();
			assertEquals(
				new HashSet<>(
					Arrays.asList(
						(IPortRange)Port.valueOf(22, Protocol.TCP),
						PortRange.valueOf(45, 78, Protocol.TCP)
					)
				),
				service1.getPorts()
			);
			assertEquals(
				Collections.emptySet(),
				service1.getProtocols()
			);
			assertEquals(
				InetAddressPrefixes.UNSPECIFIED_IPV4,
				service1.getDestinationIPv4()
			);
			assertNull(
				service1.getDestinationIPv6()
			);
		}
		{
			Service service2 = serviceIter.next();
			assertEquals(
				new HashSet<>(
					Arrays.asList(
						(IPortRange)Port.valueOf(22, Protocol.TCP),
						PortRange.valueOf(45, 100, Protocol.TCP)
					)
				),
				service2.getPorts()
			);
			assertEquals(
				Collections.emptySet(),
				service2.getProtocols()
			);
			assertNull(
				service2.getDestinationIPv4()
			);
			assertEquals(
				InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"),
				service2.getDestinationIPv6()
			);
		}
	}

	@Test
	public void testCreateOptimizedServiceSet_multiAdjacent_unspecifiedIPv4_specificIPv6_with_overlap() throws IOException, ValidationException {
		Service sshTemplate = ServiceTest.loadCentos7TestService("ssh");
		ServiceSet optimized = ServiceSet.createOptimizedServiceSet(
			sshTemplate,
			Arrays.asList(
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(24, Protocol.TCP)),
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(23, Protocol.TCP)),
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, Port.valueOf(25, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), Port.valueOf(24, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), Port.valueOf(23, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), Port.valueOf(25, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8/112"), Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8/112"), Port.valueOf(24, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8/112"), Port.valueOf(23, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8/112"), Port.valueOf(25, Protocol.TCP))
			)
		);
		assertEquals(
			1,
			optimized.getServices().size()
		);
		Service service = optimized.getServices().iterator().next();
		assertEquals(
			Collections.singleton(PortRange.valueOf(22, 25, Protocol.TCP)),
			service.getPorts()
		);
		assertEquals(
			Collections.emptySet(),
			service.getProtocols()
		);
		assertEquals(
			InetAddressPrefixes.UNSPECIFIED_IPV4,
			service.getDestinationIPv4()
		);
		assertEquals(
			InetAddressPrefix.valueOf("1:2:3:4:5:6:7:0/112"),
			service.getDestinationIPv6()
		);
	}


	@Test
	public void testCreateOptimizedServiceSet_finalCrazyCombo() throws IOException, ValidationException {
		Service sshTemplate = ServiceTest.loadCentos7TestService("ssh");
		ServiceSet optimized = ServiceSet.createOptimizedServiceSet(
			sshTemplate,
			Arrays.asList(
				new Target(InetAddressPrefix.valueOf("1.2.3.4"), Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1.2.3.4"), Port.valueOf(24, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1.2.3.5/31"), PortRange.valueOf(22, 23, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1.2.3.5/31"), PortRange.valueOf(45, 78, Protocol.TCP)),
				new Target(InetAddressPrefixes.UNSPECIFIED_IPV4, PortRange.valueOf(45, 78, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), Port.valueOf(22, Protocol.TCP)),
				new Target(InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"), PortRange.valueOf(45, 78, Protocol.TCP))
			)
		);
		// Phase 1: Coalesce ports by destination
		// 0.0.0.0/0 -> 45-78/TCP
		// 1.2.3.4 -> 22/TCP, 24/TCP
		// 1.2.3.4/31 -> 22-23/TCP, 45-78/TCP
		// 1:2:3:4:5:6:7:8 -> 22/TCP, 45-78/TCP

		// Phase 2: Coalesce destinations by protocol and ports
		// 22/TCP, 24/TCP -> 1.2.3.4
		// 22/TCP, 45-78/TCP -> 1:2:3:4:5:6:7:8
		// 22-23/TCP, 45-78/TCP -> 1.2.3.4/31
		// 45-78/TCP -> 0.0.0.0/0

		// Phase 3: Split by destinations by family
		// Same

		// Phase 4: Build service set
		// Same

		assertEquals(
			4,
			optimized.getServices().size()
		);
		Iterator<Service> serviceIter = optimized.getServices().iterator();
		{
			// 22/TCP, 24/TCP -> 1.2.3.4
			Service service1 = serviceIter.next();
			assertEquals(
				new HashSet<>(
					Arrays.asList(
						(IPortRange)Port.valueOf(22, Protocol.TCP),
						Port.valueOf(24, Protocol.TCP)
					)
				),
				service1.getPorts()
			);
			assertEquals(
				Collections.emptySet(),
				service1.getProtocols()
			);
			assertEquals(
				InetAddressPrefix.valueOf("1.2.3.4"),
				service1.getDestinationIPv4()
			);
			assertNull(
				service1.getDestinationIPv6()
			);
		}
		{
			// 22/TCP, 45-78/TCP -> 1:2:3:4:5:6:7:8
			Service service2 = serviceIter.next();
			assertEquals(
				new HashSet<>(
					Arrays.asList(
						(IPortRange)Port.valueOf(22, Protocol.TCP),
						PortRange.valueOf(45, 78, Protocol.TCP)
					)
				),
				service2.getPorts()
			);
			assertEquals(
				Collections.emptySet(),
				service2.getProtocols()
			);
			assertNull(
				service2.getDestinationIPv4()
			);
			assertEquals(
				InetAddressPrefix.valueOf("1:2:3:4:5:6:7:8"),
				service2.getDestinationIPv6()
			);
		}
		{
			// 22-23/TCP, 45-78/TCP -> 1.2.3.4/31
			Service service3 = serviceIter.next();
			assertEquals(
				new HashSet<>(
					Arrays.asList(
						(IPortRange)PortRange.valueOf(22, 23, Protocol.TCP),
						PortRange.valueOf(45, 78, Protocol.TCP)
					)
				),
				service3.getPorts()
			);
			assertEquals(
				Collections.emptySet(),
				service3.getProtocols()
			);
			assertEquals(
				InetAddressPrefix.valueOf("1.2.3.4/31"),
				service3.getDestinationIPv4()
			);
			assertNull(
				service3.getDestinationIPv6()
			);
		}
		{
			// 45-78/TCP -> 0.0.0.0/0
			Service service4 = serviceIter.next();
			assertEquals(
				new HashSet<>(
					Arrays.asList(
						(IPortRange)PortRange.valueOf(45, 78, Protocol.TCP)
					)
				),
				service4.getPorts()
			);
			assertEquals(
				Collections.emptySet(),
				service4.getProtocols()
			);
			assertEquals(
				InetAddressPrefixes.UNSPECIFIED_IPV4,
				service4.getDestinationIPv4()
			);
			assertNull(
				service4.getDestinationIPv6()
			);
		}
	}
}
