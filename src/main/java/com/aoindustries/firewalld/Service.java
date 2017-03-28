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

import com.aoindustries.lang.NullArgumentException;
import com.aoindustries.net.AddressFamily;
import com.aoindustries.net.IPortRange;
import com.aoindustries.net.InetAddressPrefix;
import com.aoindustries.net.Protocol;
import com.aoindustries.util.AoCollections;
import com.aoindustries.validation.ValidationException;
import com.aoindustries.xml.XmlUtils;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Represents on specific service as configured in either
 * <code>/usr/lib/firewalld/services/<i>service</i>.xml</code> or
 * <code>/etc/firewalld/services/<i>service</i>.xml</code>.
 * <p>
 * See <code>man 5 firewalld.service</code> for details.
 * </p>
 *
 * @author  AO Industries, Inc.
 */
public class Service {

	/**
	 * The directory containing local service files.
	 */
	public static final String LOCAL_SERVICES_DIRECTORY = "/etc/firewalld/services";

	/**
	 * The directory containing system service files.
	 */
	public static final String SYSTEM_SERVICES_DIRECTORY = "/usr/lib/firewalld/services";

	/**
	 * File extension used on service XML files.
	 */
	public static final String EXTENSION = ".xml";

	/**
	 * The names of the various XML elements.
	 */
	private static final String
		SERVICE_ELEM = "service",
		VERSION_ATTR = "version",
		SHORT_ELEM = "short",
		DESCRIPTION_ELEM = "description",
		PORT_ELEM = "port",
		PORT_ATTR = "port",
		PROTOCOL_ATTR = "protocol",
		PROTOCOL_ELEM = "protocol",
		VALUE_ATTR = "value",
		SOURCE_PORT_ELEM = "source-port",
		MODULE_ELEM = "module",
		NAME_ATTR = "name",
		DESTINATION_ELEM = "destination",
		IPV4_ATTR = "ipv4",
		IPV6_ATTR = "ipv6"
	;

	/**
	 * Parses a protocol value compatible with <code>/etc/protocols</code>.
	 */
	static Protocol parseProtocol(String protocol) throws IllegalArgumentException {
		NullArgumentException.checkNotNull(protocol);
		// This might need some other type of conversion.  Do as-needed.
		return Protocol.valueOf(protocol.toUpperCase(Locale.ROOT));
	}

	/**
	 * Parse a port or port range.
	 *
	 * @param port  the port number (###) or port range (###-###).
	 */
	static IPortRange parsePort(String port, Protocol protocol) throws NumberFormatException, ValidationException {
		int from, to;
		{
			int hyphenPos = port.indexOf('-');
			if(hyphenPos == -1) {
				from = to = Integer.parseInt(port);
			} else {
				from = Integer.parseInt(port.substring(0, hyphenPos));
				to = Integer.parseInt(port.substring(hyphenPos + 1));
			}
		}
		return IPortRange.valueOf(from, to, protocol);
	}

	/**
	 * Loads a service from an {@link InputStream}.
	 *
	 * @throws  IOException  when cannot read or parse the service file
	 */
	public static Service loadService(InputStream in) throws IOException {
		try {
			Element serviceElem = XmlUtils.parseXml(in).getDocumentElement();
			{
				String rootNodeName = serviceElem.getNodeName();
				if(!rootNodeName.equals(SERVICE_ELEM)) throw new IOException("Root node is not a " + SERVICE_ELEM + ": " + rootNodeName);
			}
			String version;
			{
				String versionAttr = serviceElem.getAttribute(VERSION_ATTR);
				version = versionAttr.isEmpty() ? null : versionAttr;
			}
			String shortName = XmlUtils.getChildTextContent(serviceElem, SHORT_ELEM);
			String description = XmlUtils.getChildTextContent(serviceElem, DESCRIPTION_ELEM);
			Set<IPortRange> ports = new LinkedHashSet<IPortRange>();
			Set<Protocol> protocols = new LinkedHashSet<Protocol>(); // Not using EnumSet to maintain source order
			for(Element portElem : XmlUtils.iterableChildElementsByTagName(serviceElem, PORT_ELEM)) {
				Protocol protocol = parseProtocol(portElem.getAttribute(PROTOCOL_ATTR));
				String portAttr = portElem.getAttribute(PORT_ATTR);
				if(portAttr.isEmpty()) {
					if(!protocols.add(protocol)) throw new IOException("Duplicate " + PROTOCOL_ATTR + ": " + protocol);
				} else {
					IPortRange port = parsePort(portAttr, protocol);
					if(!ports.add(port)) throw new IOException("Duplicate " + PORT_ATTR + ": " + port);
				}
			}
			for(Element protocolElem : XmlUtils.iterableChildElementsByTagName(serviceElem, PROTOCOL_ELEM)) {
				Protocol protocol = parseProtocol(protocolElem.getAttribute(VALUE_ATTR));
				if(!protocols.add(protocol)) throw new IOException("Duplicate " + PROTOCOL_ELEM + ": " + protocol);
			}
			Set<IPortRange> sourcePorts = new LinkedHashSet<IPortRange>();
			for(Element sourcePortElem : XmlUtils.iterableChildElementsByTagName(serviceElem, SOURCE_PORT_ELEM)) {
				IPortRange sourcePort = parsePort(
					sourcePortElem.getAttribute(PORT_ATTR),
					parseProtocol(sourcePortElem.getAttribute(PROTOCOL_ATTR))
				);
				if(!sourcePorts.add(sourcePort)) throw new IOException("Duplicate " + SOURCE_PORT_ELEM + ": " + sourcePort);
			}
			Set<String> modules = new LinkedHashSet<String>();
			for(Element moduleElem : XmlUtils.iterableChildElementsByTagName(serviceElem, MODULE_ELEM)) {
				String module = moduleElem.getAttribute(NAME_ATTR);
				if(!modules.add(module)) throw new IOException("Duplicate " + MODULE_ELEM + ": " + module);
			}
			InetAddressPrefix destinationIPv4, destinationIPv6;
			Element destinationElem = XmlUtils.getChildElementByTagName(serviceElem, DESTINATION_ELEM);
			if(destinationElem != null) {
				String ipv4Attr = destinationElem.getAttribute(IPV4_ATTR);
				destinationIPv4 = ipv4Attr.isEmpty() ? null : InetAddressPrefix.valueOf(ipv4Attr);
				String ipv6Attr = destinationElem.getAttribute(IPV6_ATTR);
				destinationIPv6 = ipv6Attr.isEmpty() ? null : InetAddressPrefix.valueOf(ipv6Attr);
				if(destinationIPv4 == null && destinationIPv6 == null) {
					throw new IOException(DESTINATION_ELEM + " has neither " + IPV4_ATTR + " nor " + IPV6_ATTR);
				}
			} else {
				destinationIPv4 = destinationIPv6 = null;
			}
			return new Service(
				version,
				shortName,
				description,
				ports,
				protocols,
				sourcePorts,
				modules,
				destinationIPv4,
				destinationIPv6
			);
		} catch(IllegalArgumentException e) {
			throw new IOException(e);
		} catch(ValidationException e) {
			throw new IOException(e);
		} catch(ParserConfigurationException e) {
			throw new IOException(e);
		} catch(SAXException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Loads a service from the given {@link File}.
	 *
	 * @throws  IOException  when cannot read or parse the service file
	 */
	public static Service loadService(File file) throws IOException {
		InputStream in = new BufferedInputStream(new FileInputStream(file));
		try {
			return loadService(in);
		} finally {
			in.close();
		}
	}

	/**
	 * Loads a local service from {@link #LOCAL_SERVICES_DIRECTORY}.
	 *
	 * @return  The {@link Service} or {@code null} if the service file does not exist.
	 *
	 * @throws  IOException  when cannot read or parse the service file
	 */
	public static Service loadLocalService(String name) throws IOException {
		File file = new File(LOCAL_SERVICES_DIRECTORY, name + EXTENSION);
		return file.exists() ? loadService(file) : null;
	}

	/**
	 * Loads a system service from {@link #SYSTEM_SERVICES_DIRECTORY}.
	 *
	 * @return  The {@link Service} or {@code null} if the service file does not exist.
	 *
	 * @throws  IOException  when cannot read or parse the service file
	 */
	public static Service loadSystemService(String name) throws IOException {
		File file = new File(SYSTEM_SERVICES_DIRECTORY, name + EXTENSION);
		return file.exists() ? loadService(file) : null;
	}

	private final String version;
	private final String shortName;
	private final String description;
	private final Set<? extends IPortRange> ports;
	private final Set<Protocol> protocols;
	private final Set<? extends IPortRange> sourcePorts;
	private final Set<String> modules;
	private final InetAddressPrefix destinationIPv4;
	private final InetAddressPrefix destinationIPv6;

	public Service(
		String version,
		String shortName,
		String description,
		Collection<? extends IPortRange> ports,
		Collection<Protocol> protocols,
		Collection<? extends IPortRange> sourcePorts,
		Set<String> modules,
		InetAddressPrefix destinationIPv4,
		InetAddressPrefix destinationIPv6
	) {
		this.version = version;
		this.shortName = shortName;
		this.description = description;
		this.ports = AoCollections.unmodifiableCopySet(ports);
		this.protocols = AoCollections.unmodifiableCopySet(protocols);
		this.sourcePorts = AoCollections.unmodifiableCopySet(sourcePorts);
		this.modules = AoCollections.unmodifiableCopySet(modules);
		if(
			destinationIPv4 != null
			&& destinationIPv4.getAddress().getAddressFamily() != AddressFamily.INET
		) {
			throw new IllegalArgumentException("Not an IPv4 destination: " + destinationIPv4);
		}
		this.destinationIPv4 = destinationIPv4;
		if(
			destinationIPv6 != null
			&& destinationIPv6.getAddress().getAddressFamily() != AddressFamily.INET6
		) {
			throw new IllegalArgumentException("Not an IPv6 destination: " + destinationIPv6);
		}
		this.destinationIPv6 = destinationIPv6;
	}

	/**
	 * The optional version.
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * The optional more readable short name.
	 */
	public String getShortName() {
		return shortName;
	}

	/**
	 * The optional longer description.
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * The optional set of ports.  When no ports will be an empty set.
	 *
	 * @return  an unmodifiable set of ports
	 */
	public Set<? extends IPortRange> getPorts() {
		return ports;
	}

	/**
	 * The optional set of protocols.  When no protocols will be an empty set.
	 *
	 * @return  an unmodifiable set of protocols
	 */
	public Set<Protocol> getProtocols() {
		return protocols;
	}

	/**
	 * The optional set of source ports.  When no source ports will be an empty set.
	 *
	 * @return  an unmodifiable set of source ports
	 */
	public Set<? extends IPortRange> getSourcePorts() {
		return sourcePorts;
	}

	/**
	 * The optional set of modules.  When no modules will be an empty set.
	 *
	 * @return  an unmodifiable set of modules
	 */
	public Set<String> getModules() {
		return modules;
	}

	/**
	 * The optional IPv4 destination network.
	 *
	 * @see  #getDestinationIPv6()  for IPv6
	 */
	public InetAddressPrefix getDestinationIPv4() {
		return destinationIPv4;
	}

	/**
	 * The optional IPv6 destination network.
	 *
	 * @see  #getDestinationIPv4()  for IPv4
	 */
	public InetAddressPrefix getDestinationIPv6() {
		return destinationIPv6;
	}

	/**
	 * Gets the destination for the given {@link AddressFamily}.
	 */
	public InetAddressPrefix getDestination(AddressFamily addressFamily) {
		NullArgumentException.checkNotNull(addressFamily);
		switch(addressFamily) {
			case INET  : return destinationIPv4;
			case INET6 : return destinationIPv6;
			default : throw new AssertionError(addressFamily);
		}
	}
}
