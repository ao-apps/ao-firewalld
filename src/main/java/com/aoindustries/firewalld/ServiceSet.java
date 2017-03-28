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

import com.aoindustries.util.AoCollections;
import java.io.File;
import java.io.IOException;
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

	private final Service template;
	private final Set<Service> services;
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
	 * This may be an empty set when a template has no existing configuration.
	 * <p>
	 * This may have overlapping destinations if the service set was not previously optimized.
	 * </p>
	 *
	 * @see  Target#compareTo(com.aoindustries.firewalld.Target)
	 */
	public SortedSet<Target> getTargets() {
		return targets;
	}
}
