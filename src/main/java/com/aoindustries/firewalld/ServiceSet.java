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

import com.aoindustries.io.FileUtils;
import com.aoindustries.net.AddressFamily;
import com.aoindustries.net.IPortRange;
import com.aoindustries.net.InetAddressPrefix;
import com.aoindustries.net.InetAddressPrefixes;
import com.aoindustries.net.Protocol;
import com.aoindustries.util.AoCollections;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A service set is one service opened on a set of specific IP and port combinations.
 * Because each {@link Service} only supports a single &lt;destination /&gt;, to
 * open only the specific IP and port combinations, a set of additional services are
 * created using the base service as a template.
 * <p>
 * The first service in the set uses the template service name.  Additional services are
 * named with {@code "-2"}, {@code "-3"}, ... added to the template service name.
 * </p>
 * <p>
 * Consider the example of opening SSH on two addresses, but leaving it closed on others.
 * One port could be <samp>192.0.2.14:22</samp> while the other is <samp>192.0.2.16:22</samp>.
 * This would result in two services: <samp>ssh</samp> with a destination of
 * <samp>192.0.2.14:22</samp> and <samp>ssh-2</samp> with a destination of <samp>192.0.2.16:22</samp>.
 *
 * @author  AO Industries, Inc.
 */
public class ServiceSet {

	private static final Logger logger = Logger.getLogger(ServiceSet.class.getName());

	/**
	 * Loads the currently configured service set for the given name.
	 * The system service is used as the template.
	 *
	 * @see  Service#loadSystemService(java.lang.String)
	 * @see  #loadServiceSet(com.aoindustries.firewalld.Service)
	 */
	public static ServiceSet loadServiceSet(String name) throws IOException {
		if(logger.isLoggable(Level.FINE)) logger.fine("Loading service set: " + name);
		Service template = Service.loadSystemService(name);
		if(template == null) throw new IllegalArgumentException("System service not found: " + name);
		return loadServiceSet(template);
	}

	/**
	 * Checks that there is no system service that conflicts with the additional
	 * service pattern of <i>name</i>-<i>#</i>.xml
	 *
	 * @throw  IllegalStateException when conflicting service detected
	 */
	private static void checkForSystemServiceConflict(String name) throws IllegalStateException {
		String[] list = new File(Service.SYSTEM_SERVICES_DIRECTORY).list();
		if(list != null) {
			String prefix = name + '-';
			for(String filename : list) {
				if(logger.isLoggable(Level.FINER)) logger.finer("Scanning for system service conflict: " + filename);
				if(
					filename.startsWith(prefix)
					&& filename.endsWith(Service.EXTENSION)
				) {
					// Must also be parseable as an int
					try {
						Integer.parseInt(
							filename.substring(
								prefix.length(),
								filename.length() - Service.EXTENSION.length()
							)
						);
						throw new IllegalStateException("System service conflicts with service set names: " + filename);
					} catch(NumberFormatException e) {
						// Is not parseable as Integer, no conflict
						if(logger.isLoggable(Level.FINE)) logger.fine("Skipping not int parseable: " + filename);
					}
				}
			}
		}
	}

	/**
	 * Loads the currently configured service set for the given template.
	 *
	 * @see #loadServiceSet(java.lang.String)
	 */
	public static ServiceSet loadServiceSet(Service template) throws IOException {
		String templateName = template.getName();
		if(logger.isLoggable(Level.FINE)) logger.fine("Loading service set: " + templateName);
		checkForSystemServiceConflict(templateName);
		Map<String,File> servicesToLoad = new LinkedHashMap<String,File>();
		String[] list = new File(Service.LOCAL_SERVICES_DIRECTORY).list();
		if(list != null) {
			String prefix = templateName + '-';
			for(String filename : list) {
				if(logger.isLoggable(Level.FINER)) logger.finer("Scanning for service set: " + filename);
				if(
					filename.startsWith(prefix)
					&& filename.endsWith(Service.EXTENSION)
				) {
					// Must also be parseable as an int
					try {
						int num = Integer.parseInt(
							filename.substring(
								prefix.length(),
								filename.length() - Service.EXTENSION.length()
							)
						);
						if(logger.isLoggable(Level.FINE)) logger.fine("Found local service: " + filename);
						servicesToLoad.put(
							prefix + num,
							new File(Service.LOCAL_SERVICES_DIRECTORY, filename)
						);
					} catch(NumberFormatException e) {
						// Is not parseable as Integer, ignore
						if(logger.isLoggable(Level.FINE)) logger.fine("Skipping not int parseable: " + filename);
					}
				}
			}
		}
		// Add template file if not found in local services
		if(servicesToLoad.containsKey(templateName)) {
			// No local override of system service, load the system service if exists
			if(logger.isLoggable(Level.FINE)) logger.fine("Adding system service: " + templateName);
			servicesToLoad.put(
				templateName,
				Service.getLocalServiceFile(templateName)
			);
		}
		// Load services
		Set<Service> services = new LinkedHashSet<Service>(servicesToLoad.size()*4/3+1);
		for(Map.Entry<String,File> entry : servicesToLoad.entrySet()) {
			File file = entry.getValue();
			Service service = Service.loadService(entry.getKey(), file);
			// Ignore if file removed or doesn't exist
			if(service != null) {
				if(logger.isLoggable(Level.FINE)) logger.fine("Successfully loaded service: " + service + " from " + file);
				if(!services.add(service)) throw new AssertionError("Duplicate service: " + service);
			} else {
				if(logger.isLoggable(Level.FINE)) logger.fine("Service file not found: " + file);
			}
		}
		return new ServiceSet(template, services);
	}

	/**
	 * Creates an optimized service set for the given name and targets.
	 * The system service is used as the template.
	 * The service set is not {@link #commit(java.util.Set) committed}.
	 *
	 * @see Service#loadSystemService(java.lang.String)
	 * @see #createOptimizedServiceSet(com.aoindustries.firewalld.Service, java.lang.Iterable) 
	 * @see #optimize()
	 */
	public static ServiceSet createOptimizedServiceSet(String name, Iterable<? extends Target> targets) throws IOException {
		if(logger.isLoggable(Level.FINE)) logger.fine("Loading service set: " + name);
		Service template = Service.loadSystemService(name);
		if(template == null) throw new IllegalArgumentException("System service not found: " + name);
		return createOptimizedServiceSet(template, targets);
	}

	private static final Comparator<SortedSet<ProtocolOrPortRange>> portSetComparator = new Comparator<SortedSet<ProtocolOrPortRange>>() {
		@Override
		public int compare(SortedSet<ProtocolOrPortRange> ports1, SortedSet<ProtocolOrPortRange> ports2) {
			// Put shorter sets of ports before longer when they are otherwise not sorted
			Iterator<ProtocolOrPortRange> iter1 = ports1.iterator();
			Iterator<ProtocolOrPortRange> iter2 = ports2.iterator();
			while(iter1.hasNext()) {
				if(!iter2.hasNext()) return 1; // ports2 shorter list than ports1
				int diff = iter1.next().compareTo(iter2.next());
				if(diff != 0) return diff;
			}
			if(iter2.hasNext()) return -1; // ports1 shorter list than ports2
			return 0;
		}
	};

	/**
	 * Creates an optimized service set for the given template and targets.
	 * The service set is not {@link #commit(java.util.Set) committed}.
	 * <p>
	 * First, ports are coalesced into port ranges within matching destinations.
	 * Protocol-only is considered to match all ports of that protocol.
	 * </p>
	 * <p>
	 * Second, destinations are combined within network prefixes when have equal port ranges.
	 * {@link InetAddressPrefixes#UNSPECIFIED_IPV4} and {@link InetAddressPrefixes#UNSPECIFIED_IPV6}
	 * are considered to match all addresses of the same family (this is a natural consequence of
	 * the way the unspecified prefixes are defined with prefix of zero).
	 * </p>
	 * <p>
	 * Third, a set of services are generated based on the template.  All fields
	 * except {@link Service#getPorts()}, {@link Service#getProtocols()},
	 * {@link Service#getDestinationIPv4()} and {@link Service#getDestinationIPv6()}
	 * are copied from the template.  The template ports, protocols, and destinations
	 * are not used.
	 * </p>
	 *
	 * @see #createOptimizedServiceSet(java.lang.String, java.lang.Iterable)
	 * @see #optimize()
	 */
	// TODO: An initial coalesce pass on all targets could reduce this set further
	// TODO: And target-level coalesce needs to consider with *both* destination and ports are widening conversions
	// TODO: Specifically, 22/TCP@1.2.3.4 overlaps 22-23/TCP@1.2.3.4/31
	// TODO: No rush on this, though, since we'll tend to feed individual IPs, which should group reasonably well with current algorithm
	public static ServiceSet createOptimizedServiceSet(Service template, Iterable<? extends Target> targets) {
		if(logger.isLoggable(Level.FINE)) logger.fine("Optimizing service set: " + template + "->" + targets);
		// Coalesce ports by destination
		SortedMap<InetAddressPrefix,SortedSet<ProtocolOrPortRange>> coalescedPortsByDestination = new TreeMap<InetAddressPrefix,SortedSet<ProtocolOrPortRange>>();
		{
			SortedSet<Target> toAdd = new TreeSet<Target>();
			for(Target target : targets) toAdd.add(target);
			if(logger.isLoggable(Level.FINE)) logger.fine("Combined into toAdd: " + template + "->" + toAdd);
			while(!toAdd.isEmpty()) {
				// Get and remove the first element
				Target target;
				{
					Iterator<Target> toAddIter = toAdd.iterator();
					target = toAddIter.next();
					if(logger.isLoggable(Level.FINER)) logger.finer(toAdd.size() + " more to add: " + template + "->" + target);
					toAddIter.remove();
				}
				InetAddressPrefix destination = target.getDestination();
				SortedSet<ProtocolOrPortRange> coalescedPorts = coalescedPortsByDestination.get(destination);
				if(coalescedPorts == null) {
					coalescedPorts = new TreeSet<ProtocolOrPortRange>();
					coalescedPorts.add(target.protocolOrPortRange);
					coalescedPortsByDestination.put(destination, coalescedPorts);
				} else {
					Iterator<ProtocolOrPortRange> coalescedIter = coalescedPorts.iterator();
					boolean wasCoalesced = false;
					while(coalescedIter.hasNext()) {
						ProtocolOrPortRange coalesced = coalescedIter.next();
						ProtocolOrPortRange newCoalesced = target.protocolOrPortRange.coalesce(coalesced);
						if(newCoalesced != null) {
							if(!toAdd.add(new Target(destination, newCoalesced))) throw new AssertionError();
							coalescedIter.remove();
							wasCoalesced = true;
						}
					}
					if(!wasCoalesced) {
						coalescedPorts.add(target.protocolOrPortRange);
					}
				}
			}
		}
		if(logger.isLoggable(Level.FINE)) logger.fine("After coalesce port ranges: " + template + "->" + coalescedPortsByDestination);
		// Coalesce destinations by protocol and ports
		SortedMap<SortedSet<ProtocolOrPortRange>,SortedSet<InetAddressPrefix>> coalescedDestinationsByPorts = new TreeMap<SortedSet<ProtocolOrPortRange>,SortedSet<InetAddressPrefix>>(portSetComparator);
		{
			SortedMap<InetAddressPrefix,SortedSet<ProtocolOrPortRange>> toAdd = new TreeMap<InetAddressPrefix,SortedSet<ProtocolOrPortRange>>(coalescedPortsByDestination);
			while(!toAdd.isEmpty()) {
				// Get and remove the first element
				InetAddressPrefix destinationToAdd;
				SortedSet<ProtocolOrPortRange> portsToAdd;
				{
					Iterator<Map.Entry<InetAddressPrefix,SortedSet<ProtocolOrPortRange>>> toAddIter = toAdd.entrySet().iterator();
					Map.Entry<InetAddressPrefix,SortedSet<ProtocolOrPortRange>> entry = toAddIter.next();
					destinationToAdd = entry.getKey();
					portsToAdd = entry.getValue();
					if(logger.isLoggable(Level.FINER)) logger.finer(toAdd.size() + " more to add: " + template + "->" + destinationToAdd);
					toAddIter.remove();
				}
				SortedSet<InetAddressPrefix> coalescedDestinations = coalescedDestinationsByPorts.get(portsToAdd);
				if(coalescedDestinations == null) {
					coalescedDestinations = new TreeSet<InetAddressPrefix>();
					coalescedDestinations.add(destinationToAdd);
					coalescedDestinationsByPorts.put(portsToAdd, coalescedDestinations);
				} else {
					Iterator<InetAddressPrefix> coalescedIter = coalescedDestinations.iterator();
					boolean wasCoalesced = false;
					while(coalescedIter.hasNext()) {
						InetAddressPrefix coalesced = coalescedIter.next();
						InetAddressPrefix newCoalesced = destinationToAdd.coalesce(coalesced);
						if(newCoalesced != null) {
							if(toAdd.put(newCoalesced, portsToAdd) != null) throw new AssertionError();
							coalescedIter.remove();
							wasCoalesced = true;
							// Can only coalesce with one existing destination per pass, break now
							break;
						}
					}
					if(!wasCoalesced) {
						coalescedDestinations.add(destinationToAdd);
					}
				}
			}
		}
		if(logger.isLoggable(Level.FINE)) logger.fine("After coalesce destinations: " + template + "->" + coalescedDestinationsByPorts);
		// Split by destinations by family
		SortedMap<SortedSet<ProtocolOrPortRange>,EnumMap<AddressFamily,SortedSet<InetAddressPrefix>>> splitByFamily = new TreeMap<SortedSet<ProtocolOrPortRange>,EnumMap<AddressFamily,SortedSet<InetAddressPrefix>>>(portSetComparator);
		for(Map.Entry<SortedSet<ProtocolOrPortRange>,SortedSet<InetAddressPrefix>> entry : coalescedDestinationsByPorts.entrySet()) {
			SortedSet<ProtocolOrPortRange> portsToSplit = entry.getKey();
			EnumMap<AddressFamily,SortedSet<InetAddressPrefix>> destinationsByFamily = splitByFamily.get(portsToSplit);
			if(destinationsByFamily == null) {
				destinationsByFamily = new EnumMap<AddressFamily,SortedSet<InetAddressPrefix>>(AddressFamily.class);
				splitByFamily.put(portsToSplit, destinationsByFamily);
			}
			SortedSet<InetAddressPrefix> destinationsToSplit = entry.getValue();
			for(InetAddressPrefix destinationToSplit : destinationsToSplit) {
				AddressFamily family = destinationToSplit.getAddress().getAddressFamily();
				SortedSet<InetAddressPrefix> destinationsForFamily = destinationsByFamily.get(family);
				if(destinationsForFamily == null) {
					destinationsForFamily = new TreeSet<InetAddressPrefix>();
					destinationsByFamily.put(family, destinationsForFamily);
				}
				if(!destinationsForFamily.add(destinationToSplit)) throw new AssertionError();
			}
		}
		if(logger.isLoggable(Level.FINE)) logger.fine("After split by family: " + template + "->" + splitByFamily);

		// Build service set
		// Note: The natural ordering of InetAddressPrefix puts unspecified first, which has the best chance to match default system services
		Set<Service> services = new LinkedHashSet<Service>();
		for(Map.Entry<SortedSet<ProtocolOrPortRange>,EnumMap<AddressFamily,SortedSet<InetAddressPrefix>>> entry : splitByFamily.entrySet()) {
			// Split protocols and ports
			SortedSet<IPortRange> ports = new TreeSet<IPortRange>();
			SortedSet<Protocol> protocols = new TreeSet<Protocol>();
			{
				SortedSet<ProtocolOrPortRange> protocolsAndPorts = entry.getKey();
				for(ProtocolOrPortRange protocolAndPort : protocolsAndPorts) {
					IPortRange portRange = protocolAndPort.getPortRange();
					if(portRange != null) ports.add(portRange);
					else protocols.add(protocolAndPort.getProtocol());
				}
			}
			EnumMap<AddressFamily,SortedSet<InetAddressPrefix>> destinationsByFamily = entry.getValue();
			SortedSet<InetAddressPrefix> ipv4Destinations = destinationsByFamily.get(AddressFamily.INET);
			SortedSet<InetAddressPrefix> ipv6Destinations = destinationsByFamily.get(AddressFamily.INET6);
			Iterator<InetAddressPrefix> ipv4Iter;
			if(ipv4Destinations==null) ipv4Iter = AoCollections.emptyIterator();
			else ipv4Iter = ipv4Destinations.iterator();
			Iterator<InetAddressPrefix> ipv6Iter;
			if(ipv6Destinations==null) ipv6Iter = AoCollections.emptyIterator();
			else ipv6Iter = ipv6Destinations.iterator();
			while(ipv4Iter.hasNext() || ipv6Iter.hasNext()) {
				InetAddressPrefix destinationIPv4 = ipv4Iter.hasNext() ? ipv4Iter.next() : null;
				InetAddressPrefix destinationIPv6 = ipv6Iter.hasNext() ? ipv6Iter.next() : null;
				String name;
				String shortName;
				if(services.isEmpty()) {
					name = template.getName();
					shortName = template.getShortName();
				} else {
					int num = services.size() + 1;
					name = template.getName() + '-' + num;
					shortName = template.getShortName() == null ? null : (template.getShortName() + " #" + num);
				}
				if(logger.isLoggable(Level.FINE)) logger.fine("Adding service: " + name + "->ports(" + ports + ") and protocols(" + protocols + ')');
				services.add(
					new Service(
						name,
						template.getVersion(),
						shortName,
						template.getDescription(),
						ports,
						protocols,
						template.getSourcePorts(),
						template.getModules(),
						destinationIPv4,
						destinationIPv6
					)
				);
			}
		}
		if(logger.isLoggable(Level.FINE)) logger.fine("Finished " + services.size() + (services.size()==1 ? " service." : " services."));
		return new ServiceSet(template, services);
	}

	private final Service template;
	private final Set<Service> services;

	/**
	 * The computed targets.
	 */
	private final SortedSet<Target> targets;

	private ServiceSet(
		Service template,
		Set<Service> services
	) {
		this.template = template;
		this.services = AoCollections.optimalUnmodifiableSet(services);
		SortedSet<Target> newTargets = new TreeSet<Target>();
		for(Service service : services) {
			newTargets.addAll(service.getTargets());
		}
		this.targets = AoCollections.optimalUnmodifiableSortedSet(newTargets);
	}

	@Override
	public String toString() {
		return template + "->" + targets;
	}

	/**
	 * Two service sets are equal when they have the same services.
	 * The template is <em>not</em> compared for equality.
	 */
	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof ServiceSet)) return false;
		ServiceSet other = (ServiceSet)obj;
		return services.equals(other.services);
	}

	@Override
	public int hashCode() {
		return services.hashCode();
	}

	/**
	 * Gets the template for this service set.  This will often be loaded from
	 * the {@link Service#loadSystemService(java.lang.String) system service},
	 * but may be programmatically provided for dynamic services.
	 */
	public Service getTemplate() {
		return template;
	}

	/**
	 * The set of services representing this service set.
	 * This may be an empty set when a template has no existing configuration.
	 */
	public Set<Service> getServices() {
		return services;
	}

	/**
	 * Gets the set of all targets represented by all services in this set.
	 * This may be an empty set when a template has no existing configuration or is modules-only (like tftp-client).
	 * <p>
	 * This may have overlapping destinations if the service set was not previously {@link #optimize() optimized}.
	 * </p>
	 *
	 * @see  Target#compareTo(com.aoindustries.firewalld.Target)
	 */
	public SortedSet<Target> getTargets() {
		return targets;
	}

	/**
	 * Returns an optimized version of this set.
	 *
	 * @return  {@code this} if already optimized, or new {@link ServiceSet} it optimal form is different.
	 *
	 * @see #createOptimizedServiceSet(java.lang.String, java.lang.Iterable)
	 * @see #createOptimizedServiceSet(com.aoindustries.firewalld.Service, java.lang.Iterable)
	 */
	public ServiceSet optimize() {
		ServiceSet optimized = createOptimizedServiceSet(template, targets);
		return this.equals(optimized) ? this : optimized;
	}

	/**
	 * Check if a service name is part of ths service set.
	 */
	private boolean isInThisServiceSet(String service) {
		// Must be service name or parseable as service-integer
		if(service.equals(template.getName())) {
			return true;
		} else {
			String prefix = template.getName() + '-';
			if(service.startsWith(prefix)) {
				try {
					Integer.parseInt(service.substring(prefix.length()));
					return true;
				} catch(NumberFormatException e) {
					// Not parseable as int, ignore
				}
			}
		}
		return false;
	}

	/**
	 * Commits this service set to the system configuration, reconfiguring and
	 * reloading the firewall as necessary.
	 * <p>
	 * Probably worth {@link #optimize() optimizing} before committing.
	 * </p>
	 *
	 * @param  zones  the zones that that the service set should be activated in, this can generally be just "public"
	 *
	 * @see #commit(java.lang.Iterable, java.util.Set)
	 */
	public void commit(Set<String> zones) throws IOException {
		commit(Collections.singleton(this), zones);
	}

	/**
	 * Commits multiple service sets to the system configuration, reconfiguring and
	 * reloading the firewall as necessary.
	 * <p>
	 * Probably worth {@link #optimize() optimizing} before committing.
	 * </p>
	 * <p>
	 * TODO: Should we use <code>firewall-cmd --permanent --new-service-from-file=filename [--name=service]</code>
	 *       instead of manipulating service XML files directly?
	 * </p>
	 *
	 * @param  serviceSets  the service sets to commit; iterated once; no duplicate service names allowed.
	 * @param  zones  the zones that that the service set should be activated in, this can generally be just "public"
	 *
	 * @see  #commit(java.util.Set)
	 */
	public static void commit(Iterable<ServiceSet> serviceSets, Set<String> zones) throws IOException {
		Map<String,ServiceSet> serviceSetsMap = new LinkedHashMap<String,ServiceSet>();
		for(ServiceSet serviceSet : serviceSets) {
			String name = serviceSet.getTemplate().getName();
			if(serviceSetsMap.put(name, serviceSet) != null) throw new IllegalArgumentException("Duplicate service set name: " + name);
		}
		synchronized(Firewalld.firewallCmdLock) {
			boolean needsReload = false;
			// Get the set of all service names that should exist
			Set<String> serviceNames = new LinkedHashSet<String>();
			for(ServiceSet serviceSet : serviceSetsMap.values()) {
				for(Service service : serviceSet.services) {
					String serviceName = service.getName();
					if(!serviceNames.add(serviceName)) throw new AssertionError("Duplicate service name: " + serviceName);
				}
			}
			// Get listing of all zones and services (firewall-cmd --permanent --list-all-zones)
			Map<String,Set<String>> servicesByZone = Firewalld.listAllServices();
			// Remove any extra services from all zones
			for(Map.Entry<String,Set<String>> entry : servicesByZone.entrySet()) {
				String zone = entry.getKey();
				Set<String> expected;
				if(zones.contains(zone)) expected = serviceNames;
				else expected = Collections.emptySet();
				Set<String> toRemove = new LinkedHashSet<String>();
				for(String service : entry.getValue()) {
					if(!expected.contains(service)) {
						boolean isInAServiceSet = false;
						for(ServiceSet serviceSet : serviceSetsMap.values()) {
							if(serviceSet.isInThisServiceSet(service)) {
								isInAServiceSet = true;
								break;
							}
						}
						if(isInAServiceSet) {
							toRemove.add(service);
						}
					}
				}
				if(!toRemove.isEmpty()) {
					Firewalld.removeServices(zone, toRemove);
					needsReload = true;
				}
			}
			// Remove any extra local service files
			File localServicesDir = new File(Service.LOCAL_SERVICES_DIRECTORY);
			{
				String[] list = localServicesDir.list();
				if(list != null) {
					for(String filename : list) {
						if(filename.endsWith(Service.EXTENSION)) {
							String service = filename.substring(0, filename.length() - Service.EXTENSION.length());
							if(!serviceNames.contains(service)) {
								boolean isInAServiceSet = false;
								for(ServiceSet serviceSet : serviceSetsMap.values()) {
									if(serviceSet.isInThisServiceSet(service)) {
										isInAServiceSet = true;
										break;
									}
								}
								if(isInAServiceSet) {
									File serviceFile = new File(localServicesDir, filename);
									if(logger.isLoggable(Level.FINE)) logger.fine("Deleting extra local service file: " + serviceFile);
									FileUtils.delete(serviceFile);
									needsReload = true;
								}
							}
						}
					}
				}
			}
			// Rewrite any changed or missing service files
			for(Map.Entry<String,ServiceSet> serviceSetEntry : serviceSetsMap.entrySet()) {
				String templateName = serviceSetEntry.getKey();
				ServiceSet serviceSet = serviceSetEntry.getValue();
				assert templateName.equals(serviceSet.template.getName());
				for(Service service : serviceSet.services) {
					String serviceName = service.getName();
					if(serviceName.equals(templateName)) {
						// When the first service file equals the system default, do not write and delete if present
						Service systemService = Service.loadSystemService(serviceName);
						if(systemService != null && service.equals(systemService)) {
							// Delete any local service file, if present
							File serviceFile = Service.getLocalServiceFile(serviceName);
							if(serviceFile.exists()) {
								if(logger.isLoggable(Level.FINE)) logger.fine("Deleting local service file handled by system file: " + serviceFile);
								FileUtils.delete(serviceFile);
								needsReload = true;
							}
							continue;
						}
					}
					Service localService = Service.loadLocalService(serviceName);
					if(localService == null || !service.equals(localService)) {
						service.saveLocalService();
						needsReload = true;
					}
				}
			}
			// Reload firewall if any file changed
			if(needsReload) {
				// Reload now to avoid "Error: INVALID_SERVICE: 'named-2' not among existing services"
				Firewalld.reload();
				needsReload = false;
			}
			// Add any services missing from zones
			for(String zone : zones) {
				Set<String> servicesForZone = servicesByZone.get(zone);
				if(servicesByZone == null) throw new IOException("Zone not found: " + zone);
				Set<String> toAdd = new TreeSet<String>();
				for(ServiceSet serviceSet : serviceSetsMap.values()) {
					for(Service service : serviceSet.services) {
						String serviceName = service.getName();
						if(!servicesForZone.contains(serviceName)) toAdd.add(serviceName);
					}
				}
				if(!toAdd.isEmpty()) {
					Firewalld.addServices(zone, toAdd);
					needsReload = true;
				}
			}
			// Reload firewall if any file changed
			if(needsReload) Firewalld.reload();
		}
	}
}
