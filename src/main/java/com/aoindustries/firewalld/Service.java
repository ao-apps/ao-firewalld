/*
 * ao-firewalld - Java API for managing firewalld.
 * Copyright (C) 2017, 2019, 2020, 2021, 2022, 2023, 2024, 2025  AO Industries, Inc.
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
import com.aoapps.lang.NullArgumentException;
import com.aoapps.lang.io.FileUtils;
import com.aoapps.lang.validation.ValidationException;
import com.aoapps.lang.xml.XmlUtils;
import com.aoapps.net.IPortRange;
import com.aoapps.net.InetAddressPrefix;
import com.aoapps.net.InetAddressPrefixes;
import com.aoapps.net.Protocol;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Represents on specific service as configured in either
 * <code>/usr/lib/firewalld/services/<i>service</i>.xml</code> or
 * <code>/etc/firewalld/services/<i>service</i>.xml</code>.
 *
 * <p>See <code>man 5 firewalld.service</code> for details.</p>
 *
 * @author  AO Industries, Inc.
 */
public class Service {

  private static final Logger logger = Logger.getLogger(Service.class.getName());

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
      IPV6_ATTR = "ipv6";

  /**
   * Parses a protocol value compatible with <code>/etc/protocols</code>.
   */
  static Protocol parseProtocol(String protocol) throws IllegalArgumentException {
    NullArgumentException.checkNotNull(protocol);
    // This might need some other type of conversion.  Do as-needed.
    Protocol p = Protocol.getProtocolByKeyword(protocol);
    if (p == null) {
      throw new IllegalArgumentException("Protocol not found: " + protocol);
    }
    return p;
  }

  /**
   * Parse a port or port range.
   *
   * @param port  the port number (###) or port range (###-###).
   */
  static IPortRange parsePort(String port, Protocol protocol) throws NumberFormatException, ValidationException {
    int from;
    int to;
    {
      int hyphenPos = port.indexOf('-');
      if (hyphenPos == -1) {
        from = to = Integer.parseInt(port);
      } else {
        from = Integer.parseInt(port.substring(0, hyphenPos));
        to = Integer.parseInt(port.substring(hyphenPos + 1));
      }
    }
    return IPortRange.valueOf(from, to, protocol);
  }

  private static class FileCacheEntry {
    private final long lastModified;
    private final long length;
    private final Service service;

    private FileCacheEntry(long lastModified, long length, Service service) {
      this.lastModified = lastModified;
      this.length = length;
      this.service = service;
    }
  }

  private static final Map<File, FileCacheEntry> fileCache = new HashMap<>();

  /**
   * Loads a service from an {@link InputStream}.
   *
   * @throws  IOException  when cannot read or parse the service file
   */
  public static Service loadService(String name, InputStream in) throws IOException {
    try {
      Element serviceElem = XmlUtils.parseXml(in).getDocumentElement();
      {
        String rootNodeName = serviceElem.getNodeName();
        if (!rootNodeName.equals(SERVICE_ELEM)) {
          throw new IOException("Root node is not a " + SERVICE_ELEM + ": " + rootNodeName);
        }
      }
      Set<IPortRange> ports = new LinkedHashSet<>();
      Set<Protocol> protocols = new LinkedHashSet<>(); // Not using EnumSet to maintain source order
      for (Element portElem : XmlUtils.iterableChildElementsByTagName(serviceElem, PORT_ELEM)) {
        Protocol protocol = parseProtocol(portElem.getAttribute(PROTOCOL_ATTR));
        String portAttr = portElem.getAttribute(PORT_ATTR);
        if (portAttr.isEmpty()) {
          if (!protocols.add(protocol)) {
            throw new IOException("Duplicate " + PROTOCOL_ATTR + ": " + protocol);
          }
        } else {
          IPortRange port = parsePort(portAttr, protocol);
          if (!ports.add(port)) {
            throw new IOException("Duplicate " + PORT_ATTR + ": " + port);
          }
        }
      }
      for (Element protocolElem : XmlUtils.iterableChildElementsByTagName(serviceElem, PROTOCOL_ELEM)) {
        Protocol protocol = parseProtocol(protocolElem.getAttribute(VALUE_ATTR));
        if (!protocols.add(protocol)) {
          throw new IOException("Duplicate " + PROTOCOL_ELEM + ": " + protocol);
        }
      }
      Set<IPortRange> sourcePorts = new LinkedHashSet<>();
      for (Element sourcePortElem : XmlUtils.iterableChildElementsByTagName(serviceElem, SOURCE_PORT_ELEM)) {
        IPortRange sourcePort = parsePort(
            sourcePortElem.getAttribute(PORT_ATTR),
            parseProtocol(sourcePortElem.getAttribute(PROTOCOL_ATTR))
        );
        if (!sourcePorts.add(sourcePort)) {
          throw new IOException("Duplicate " + SOURCE_PORT_ELEM + ": " + sourcePort);
        }
      }
      Set<String> modules = new LinkedHashSet<>();
      for (Element moduleElem : XmlUtils.iterableChildElementsByTagName(serviceElem, MODULE_ELEM)) {
        String module = moduleElem.getAttribute(NAME_ATTR);
        if (!modules.add(module)) {
          throw new IOException("Duplicate " + MODULE_ELEM + ": " + module);
        }
      }
      InetAddressPrefix destinationIpv4;
      InetAddressPrefix destinationIpv6;
      Element destinationElem = XmlUtils.getChildElementByTagName(serviceElem, DESTINATION_ELEM);
      if (destinationElem != null) {
        String ipv4Attr = destinationElem.getAttribute(IPV4_ATTR);
        destinationIpv4 = ipv4Attr.isEmpty() ? null : InetAddressPrefix.valueOf(ipv4Attr);
        String ipv6Attr = destinationElem.getAttribute(IPV6_ATTR);
        destinationIpv6 = ipv6Attr.isEmpty() ? null : InetAddressPrefix.valueOf(ipv6Attr);
        if (destinationIpv4 == null && destinationIpv6 == null) {
          throw new IOException(DESTINATION_ELEM + " has neither " + IPV4_ATTR + " nor " + IPV6_ATTR);
        }
      } else {
        destinationIpv4 = InetAddressPrefixes.UNSPECIFIED_IPV4;
        destinationIpv6 = InetAddressPrefixes.UNSPECIFIED_IPV6;
      }
      return new Service(
          name,
          serviceElem.getAttribute(VERSION_ATTR),
          XmlUtils.getChildTextContent(serviceElem, SHORT_ELEM),
          XmlUtils.getChildTextContent(serviceElem, DESCRIPTION_ELEM),
          ports,
          protocols,
          sourcePorts,
          modules,
          destinationIpv4,
          destinationIpv6
      );
    } catch (IllegalArgumentException | ValidationException | ParserConfigurationException | SAXException e) {
      throw new IOException(e);
    }
  }

  /**
   * Loads a service from the given {@link File}.
   *
   * @return  The {@link Service} or {@code null} if the service file does not exist.
   *
   * @throws  IOException  when cannot read or parse the service file
   */
  public static Service loadService(String name, File file) throws IOException {
    synchronized (fileCache) {
      if (!file.exists()) {
        fileCache.remove(file);
        return null;
      }
      long lastModified = file.lastModified();
      long length = file.length();
      // Check for cache match
      FileCacheEntry cacheEntry = fileCache.get(file);
      if (
          cacheEntry != null
              && lastModified == cacheEntry.lastModified
              && length == cacheEntry.length
      ) {
        Service service = cacheEntry.service;
        if (!name.equals(service.name)) {
          throw new IllegalArgumentException("Service name mismatch: " + name + " != " + service.name);
        }
        return service;
      }
      // Load from file
      Service service;
      try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
        service = loadService(name, in);
      }
      // Store in cache
      fileCache.put(
          file,
          new FileCacheEntry(lastModified, length, service)
      );
      return service;
    }
  }

  /**
   * Gets the file to use for local service.
   */
  public static File getLocalServiceFile(String name) {
    return new File(LOCAL_SERVICES_DIRECTORY, name + EXTENSION);
  }

  /**
   * Loads a local service from {@link #LOCAL_SERVICES_DIRECTORY}.
   *
   * @return  The {@link Service} or {@code null} if the service file does not exist.
   *
   * @throws  IOException  when cannot read or parse the service file
   */
  public static Service loadLocalService(String name) throws IOException {
    return loadService(name, getLocalServiceFile(name));
  }

  /**
   * Gets the file to use for system service.
   */
  public static File getSystemServiceFile(String name) {
    return new File(SYSTEM_SERVICES_DIRECTORY, name + EXTENSION);
  }

  /**
   * Loads a system service from {@link #SYSTEM_SERVICES_DIRECTORY}.
   *
   * @return  The {@link Service} or {@code null} if the service file does not exist.
   *
   * @throws  IOException  when cannot read or parse the service file
   */
  public static Service loadSystemService(String name) throws IOException {
    return loadService(name, getSystemServiceFile(name));
  }

  private final String name;
  private final String version;
  private final String shortName;
  private final String description;
  private final Set<IPortRange> ports;
  private final Set<Protocol> protocols;
  private final Set<IPortRange> sourcePorts;
  private final Set<String> modules;
  private final InetAddressPrefix destinationIpv4;
  private final InetAddressPrefix destinationIpv6;

  /**
   * The computed targets.
   */
  private final SortedSet<Target> targets;

  /**
   * Creates a new service.
   */
  public Service(
      String name,
      String version,
      String shortName,
      String description,
      Collection<? extends IPortRange> ports,
      Collection<Protocol> protocols,
      Collection<? extends IPortRange> sourcePorts,
      Set<String> modules,
      InetAddressPrefix destinationIpv4,
      InetAddressPrefix destinationIpv6
  ) {
    this.name = NullArgumentException.checkNotNull(name, "name");
    this.version = version == null || version.isEmpty() ? null : version;
    this.shortName = shortName == null || shortName.isEmpty() ? null : shortName;
    this.description = description == null || description.isEmpty() ? null : description;
    if (ports.isEmpty() && protocols.isEmpty() && modules.isEmpty()) {
      throw new IllegalArgumentException("Neither ports nor protocols nor modules provided.");
    }
    this.ports = AoCollections.unmodifiableCopySet(ports);
    this.protocols = AoCollections.unmodifiableCopySet(protocols);
    this.sourcePorts = AoCollections.unmodifiableCopySet(sourcePorts);
    this.modules = AoCollections.unmodifiableCopySet(modules);
    if (destinationIpv4 == null && destinationIpv6 == null) {
      throw new IllegalArgumentException("Neither destinationIpv4 nor destinationIpv6 provided.  To match all, use \"0.0.0.0/0\" or \"::/0\".");
    }
    if (
        destinationIpv4 != null
            && destinationIpv4.getAddress().getProtocolFamily() != StandardProtocolFamily.INET
    ) {
      throw new IllegalArgumentException("Not an IPv4 destination: " + destinationIpv4);
    }
    this.destinationIpv4 = destinationIpv4;
    if (
        destinationIpv6 != null
            && destinationIpv6.getAddress().getProtocolFamily() != StandardProtocolFamily.INET6
    ) {
      throw new IllegalArgumentException("Not an IPv6 destination: " + destinationIpv6);
    }
    this.destinationIpv6 = destinationIpv6;
    // Find all the targets
    SortedSet<Target> newTargets = new TreeSet<>();
    for (IPortRange port : ports) {
      if (destinationIpv4 != null) {
        Target target = new Target(destinationIpv4, port);
        if (!newTargets.add(target)) {
          throw new IllegalStateException("Duplicate target: " + target);
        }
      }
      if (destinationIpv6 != null) {
        Target target = new Target(destinationIpv6, port);
        if (!newTargets.add(target)) {
          throw new IllegalStateException("Duplicate target: " + target);
        }
      }
    }
    for (Protocol protocol : protocols) {
      if (destinationIpv4 != null) {
        Target target = new Target(destinationIpv4, protocol);
        if (!newTargets.add(target)) {
          throw new IllegalStateException("Duplicate target: " + target);
        }
      }
      if (destinationIpv6 != null) {
        Target target = new Target(destinationIpv6, protocol);
        if (!newTargets.add(target)) {
          throw new IllegalStateException("Duplicate target: " + target);
        }
      }
    }
    this.targets = AoCollections.optimalUnmodifiableSortedSet(newTargets);
  }

  @Override
  public String toString() {
    return shortName != null ? shortName : name;
  }

  /**
   * Compares two services for equality.
   * All fields must be equal, with ordering not mattering for sets.
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Service)) {
      return false;
    }
    Service other = (Service) obj;
    return
        name.equals(other.name)
            && Objects.equals(version, other.version)
            && Objects.equals(shortName, other.shortName)
            && Objects.equals(description, other.description)
            && ports.equals(other.ports)
            && protocols.equals(other.protocols)
            && sourcePorts.equals(other.sourcePorts)
            && modules.equals(other.modules)
            && Objects.equals(destinationIpv4, other.destinationIpv4)
            && Objects.equals(destinationIpv6, other.destinationIpv6);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  /**
   * The name as used by firewalld commands and XML filenames.
   */
  public String getName() {
    return name;
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
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public Set<IPortRange> getPorts() {
    return ports;
  }

  /**
   * The optional set of protocols.  When no protocols will be an empty set.
   *
   * @return  an unmodifiable set of protocols
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public Set<Protocol> getProtocols() {
    return protocols;
  }

  /**
   * The optional set of source ports.  When no source ports will be an empty set.
   *
   * @return  an unmodifiable set of source ports
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public Set<IPortRange> getSourcePorts() {
    return sourcePorts;
  }

  /**
   * The optional set of modules.  When no modules will be an empty set.
   *
   * @return  an unmodifiable set of modules
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public Set<String> getModules() {
    return modules;
  }

  /**
   * The optional IPv4 destination network.
   *
   * @return  the IPv4 address and prefix or {@code null} for no IPv4 destination.
   *
   * @see  #getDestinationIpv6()  for IPv6
   */
  public InetAddressPrefix getDestinationIpv4() {
    return destinationIpv4;
  }

  /**
   * The optional IPv4 destination network.
   *
   * @return  the IPv4 address and prefix or {@code null} for no IPv4 destination.
   *
   * @see  #getDestinationIpv6()  for IPv6
   *
   * @deprecated  Please use {@link #getDestinationIpv4()} instead.
   */
  // TODO: Remove in 6.0.0 release
  @Deprecated
  public final InetAddressPrefix getDestinationIPv4() {
    return getDestinationIpv4();
  }

  /**
   * The optional IPv6 destination network.
   *
   * @return  the IPv6 address and prefix or {@code null} for no IPv6 destination.
   *
   * @see  #getDestinationIpv4()  for IPv4
   */
  public InetAddressPrefix getDestinationIpv6() {
    return destinationIpv6;
  }

  /**
   * The optional IPv6 destination network.
   *
   * @return  the IPv6 address and prefix or {@code null} for no IPv6 destination.
   *
   * @see  #getDestinationIpv4()  for IPv4
   *
   * @deprecated  Please use {@link #getDestinationIpv6()} instead.
   */
  // TODO: Remove in 6.0.0 release
  @Deprecated
  public final InetAddressPrefix getDestinationIPv6() {
    return getDestinationIpv6();
  }

  /**
   * Gets the destination for the given {@link com.aoapps.net.AddressFamily}.
   *
   * @deprecated  Please use {@link #getDestination(java.net.ProtocolFamily)} as of Java 1.7.
   */
  @Deprecated
  public InetAddressPrefix getDestination(com.aoapps.net.AddressFamily addressFamily) {
    NullArgumentException.checkNotNull(addressFamily);
    switch (addressFamily) {
      case INET:
        return destinationIpv4;
      case INET6:
        return destinationIpv6;
      default:
        throw new AssertionError(addressFamily);
    }
  }

  /**
   * Gets the destination for the given {@link ProtocolFamily}.
   */
  public InetAddressPrefix getDestination(ProtocolFamily family) {
    NullArgumentException.checkNotNull(family);
    if (family == StandardProtocolFamily.INET) {
      return destinationIpv4;
    } else if (family == StandardProtocolFamily.INET6) {
      return destinationIpv6;
    } else {
      throw new AssertionError("Unexpected family: " + family);
    }
  }

  /**
   * Gets the set of all targets represented by this service.
   * This may be an empty set when a service is modules-only (like tftp-client).
   *
   * <p>This may have overlapping targets if the service was not previously {@link ServiceSet#optimize() optimized}.</p>
   *
   * @see  Target#compareTo(com.aoindustries.firewalld.Target)
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public SortedSet<Target> getTargets() {
    assert !targets.isEmpty();
    return targets;
  }

  private static String toPortAttr(IPortRange port) {
    int from = port.getFrom();
    int to = port.getTo();
    if (from == to) {
      return Integer.toString(from);
    } else {
      return from + "-" + to;
    }
  }

  private static String toProtocolAttr(Protocol protocol) {
    return protocol.toString().toLowerCase(Locale.ROOT);
  }

  /**
   * Write this service to its local service file.
   */
  public void saveLocalService() throws IOException {
    try {
      final File serviceFile = getLocalServiceFile(name);
      final File newServiceFile = File.createTempFile(name + '-', null, new File(LOCAL_SERVICES_DIRECTORY));
      // Should we use ao-encoding here?  Java XML is just so tedious
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      try {
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      } catch (ParserConfigurationException e) {
        throw new AssertionError("All implementations are required to support the javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING feature.", e);
      }
      // See https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.md#java
      // See https://rules.sonarsource.com/java/RSPEC-2755
      dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      DocumentBuilder docBuilder = dbf.newDocumentBuilder();
      Document document = docBuilder.newDocument();
      Element serviceElem = document.createElement(SERVICE_ELEM);
      document.appendChild(serviceElem);
      if (version != null) {
        serviceElem.setAttribute(VERSION_ATTR, version);
      }
      if (shortName != null) {
        Element shortElem = document.createElement(SHORT_ELEM);
        shortElem.appendChild(document.createTextNode(shortName));
        serviceElem.appendChild(shortElem);
      }
      if (description != null) {
        Element descriptionElem = document.createElement(DESCRIPTION_ELEM);
        descriptionElem.appendChild(document.createTextNode(description));
        serviceElem.appendChild(descriptionElem);
      }
      for (IPortRange port : ports) {
        Element portElem = document.createElement(PORT_ELEM);
        portElem.setAttribute(PROTOCOL_ATTR, toProtocolAttr(port.getProtocol()));
        portElem.setAttribute(PORT_ATTR, toPortAttr(port));
        serviceElem.appendChild(portElem);
      }
      for (Protocol protocol : protocols) {
        Element protocolElem = document.createElement(PROTOCOL_ELEM);
        protocolElem.setAttribute(VALUE_ATTR, toProtocolAttr(protocol));
        serviceElem.appendChild(protocolElem);
      }
      for (IPortRange sourcePort : sourcePorts) {
        Element sourcePortElem = document.createElement(SOURCE_PORT_ELEM);
        sourcePortElem.setAttribute(PROTOCOL_ATTR, toProtocolAttr(sourcePort.getProtocol()));
        sourcePortElem.setAttribute(PORT_ATTR, toPortAttr(sourcePort));
        serviceElem.appendChild(sourcePortElem);
      }
      for (String module : modules) {
        Element moduleElem = document.createElement(MODULE_ELEM);
        moduleElem.setAttribute(NAME_ATTR, module);
        serviceElem.appendChild(moduleElem);
      }
      if (
          !InetAddressPrefixes.UNSPECIFIED_IPV4.equals(destinationIpv4)
              || !InetAddressPrefixes.UNSPECIFIED_IPV6.equals(destinationIpv6)
      ) {
        Element destinationElem = document.createElement(DESTINATION_ELEM);
        if (destinationIpv4 != null) {
          destinationElem.setAttribute(IPV4_ATTR, destinationIpv4.toString());
        }
        if (destinationIpv6 != null) {
          destinationElem.setAttribute(IPV6_ATTR, destinationIpv6.toString());
        }
        serviceElem.appendChild(destinationElem);
      }
      // TODO: New XML Processing Limits (JDK-8270504 (not public)), see https://www.oracle.com/java/technologies/javase/8all-relnotes.html
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      try {
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      } catch (TransformerConfigurationException e) {
        throw new AssertionError("All implementations are required to support the javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING feature.", e);
      }
      // See https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.md#java
      // See https://rules.sonarsource.com/java/RSPEC-2755
      transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      transformerFactory.setAttribute("indent-number", XmlUtils.INDENT_SPACES);
      Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
      transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
      //transformer.setOutputProperty(OutputKeys.STANDALONE, "no");
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", Integer.toString(XmlUtils.INDENT_SPACES));
      DOMSource source = new DOMSource(document);
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("Writing new local service file: " + newServiceFile);
      }
      StreamResult result = new StreamResult(newServiceFile);
      transformer.transform(source, result);
      // Move successful file into place
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("Moving new local service file into place: " + newServiceFile + "→" + serviceFile);
      }
      FileUtils.rename(newServiceFile, serviceFile);
    } catch (ParserConfigurationException | TransformerException e) {
      throw new IOException(e);
    }
  }
}
