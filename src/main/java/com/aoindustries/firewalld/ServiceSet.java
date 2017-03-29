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

import com.aoindustries.lang.NotImplementedException;
import com.aoindustries.net.InetAddressPrefix;
import com.aoindustries.net.InetAddressPrefixes;
import com.aoindustries.util.AoCollections;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A service set is one service opened on a set of specific IP and port combinations.
 * Because each {@link Service} on supports a single &lt;destination /&gt;, to
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
				new File(Service.LOCAL_SERVICES_DIRECTORY, templateName + Service.EXTENSION)
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
	 * The service set is not {@link #commit() committed}.
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

	/**
	 * Creates an optimized service set for the given template and targets.
	 * The service set is not {@link #commit() committed}.
	 * <p>
	 * First, ports are coalesced into port ranges within matching destinations.
	 * Protocol-only is considered to match all ports of that protocol.
	 * </p>
	 * <p>
	 * Second, destinations are combined within network prefixes within matching port ranges.
	 * {@link InetAddressPrefixes#UNSPECIFIED_IPV4} and {@link InetAddressPrefixes#UNSPECIFIED_IPV6}
	 * are considered to match all addresses of the same family (this is a natural consequence of
	 * the way the unspecified prefixes are defined with prefix of zero).
	 * </p>
	 * <p>
	 * Third, a set of services are generated based on the template.  All fields
	 * except {@link Service#getDestinationIPv4()} and {@link Service#getDestinationIPv6()}
	 * are copied from the template.  The template destinations are not used.
	 * </p>
	 *
	 * @see #createOptimizedServiceSet(java.lang.String, java.lang.Iterable)
	 * @see #optimize()
	 */
	public static ServiceSet createOptimizedServiceSet(Service template, Iterable<? extends Target> targets) {
		if(logger.isLoggable(Level.FINE)) logger.fine("Optimizing service set: " + template + "->" + targets);
		// TODO: Should we optimize the set of targets in a separate method?
		// Coalesce ports by destination
		Map<InetAddressPrefix,SortedSet<Target>> coalescedTargetsByDestination = new HashMap<InetAddressPrefix,SortedSet<Target>>();
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
				SortedSet<Target> coalescedTargets = coalescedTargetsByDestination.get(destination);
				if(coalescedTargets == null) {
					coalescedTargets = new TreeSet<Target>();
					coalescedTargets.add(target);
					coalescedTargetsByDestination.put(destination, coalescedTargets);
				} else {
					// TODO: Test 1, 3, 2 coalesced correctly, or 1-3, 2-4, 3-5?
					Iterator<Target> coalescedIter = coalescedTargets.iterator();
					boolean wasCoalesced = false;
					while(coalescedIter.hasNext()) {
						Target coalesced = coalescedIter.next();
						Target newCoalesced = target.coalesce(coalesced);
						if(newCoalesced != null) {
							toAdd.add(newCoalesced);
							coalescedIter.remove();
							wasCoalesced = true;
						}
					}
					if(!wasCoalesced) {
						coalescedTargets.add(target);
					}
				}
			}
		}
		if(logger.isLoggable(Level.FINE)) logger.fine("After coalesce port ranges: " + template + "->" + coalescedTargetsByDestination);
		// TODO
		throw new NotImplementedException("TODO: Finish method");
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
	 * @see #createOptimizedServiceSet(java.lang.String, java.util.Set)
	 * @see #createOptimizedServiceSet(com.aoindustries.firewalld.Service, java.util.Set)
	 */
	public ServiceSet optimize() {
		ServiceSet optimized = createOptimizedServiceSet(template, targets);
		return this.equals(optimized) ? this : optimized;
	}

	/**
	 * Commits this service set to the system configuration, reconfiguring and
	 * reloading the firewall as necessary.
	 */
	public void commit() {
		throw new NotImplementedException("TODO: Implement method");
	}
}
