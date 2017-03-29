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
import com.aoindustries.lang.ObjectUtils;
import com.aoindustries.net.IPortRange;
import com.aoindustries.net.InetAddressPrefix;
import com.aoindustries.net.Protocol;

/**
 * A "target" is a single {@link InetAddressPrefix address/prefix},
 * {@link Protocol protocol}, and optional {@IPortRange port range}.
 * <p>
 * Each {@link Service} may have multiple targets, but all targets will be
 * within its {@link Service#getDestinationIPv4() IPv4 destination} and
 * {@link Service#getDestinationIPv6() IPv6 destination}.
 * </p>
 * <p>
 * A {@link ServiceSet} goes beyond the single-destination limits of {@Service}
 * and allows any arbitrary set of targets.
 * </p>
 * <p>
 * We choose the name "target" as it is the primary new concept and purpose of
 * this API and is otherwise not used in standard firewalld service terminology.
 * </p>
 *
 * @author  AO Industries, Inc.
 */
public class Target implements Comparable<Target> {

	private final InetAddressPrefix destination;
	private final Protocol protocol;
	private final IPortRange portRange;

	/**
	 * @param destination  The destination is {@link InetAddressPrefix#normalize() normalized}
	 */
	private Target(InetAddressPrefix destination, Protocol protocol, IPortRange portRange) {
		this.destination = NullArgumentException.checkNotNull(destination, "destination");
		assert destination == destination.normalize();
		this.protocol = NullArgumentException.checkNotNull(protocol, "protocol");
		this.portRange = portRange;
		assert portRange == null || portRange.getProtocol() == protocol;
	}

	/**
	 * @param destination  The destination is {@link InetAddressPrefix#normalize() normalized}
	 */
	public Target(InetAddressPrefix destination, IPortRange portRange) {
		this.destination = NullArgumentException.checkNotNull(destination, "destination").normalize();
		this.portRange = NullArgumentException.checkNotNull(portRange, "portRange");
		this.protocol = portRange.getProtocol();
	}

	/**
	 * @param destination  The destination is {@link InetAddressPrefix#normalize() normalized}
	 */
	public Target(InetAddressPrefix destination, Protocol protocol) {
		this.destination = NullArgumentException.checkNotNull(destination, "destination").normalize();
		this.protocol = NullArgumentException.checkNotNull(protocol, "protocol");
		this.portRange = null;
	}

	/**
	 * @return  The target in form <samp>[port[-range]/]protocol@address[/prefix]</samp>.
	 *
	 * @see  IPortRange#toString()
	 * @see  Protocol#toString()
	 * @see  InetAddressPrefix#toString()
	 */
	@Override
	public String toString() {
		if(portRange == null) {
			return protocol.toString() + '@' + destination;
		} else {
			return portRange.toString() + '@' + destination;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof Target)) return false;
		Target other = (Target)obj;
		return
			protocol == other.protocol
			&& ObjectUtils.equals(portRange, other.portRange)
			&& destination.equals(other.destination)
		;
	}

	@Override
	public int hashCode() {
		int hash = destination.hashCode();
		if(portRange == null) {
			return hash * 31 + protocol.hashCode();
		} else {
			return hash * 31 + portRange.hashCode();
		}
	}

	/**
	 * Ordered by destination, portRange, protocol; those with port ranges
	 * before those that are protocol-only.
	 *
	 * @see  InetAddressPrefix#compareTo(com.aoindustries.net.InetAddressPrefix)
	 * @see  IPortRange#compareTo(com.aoindustries.net.IPortRange)
	 */
	@Override
	public int compareTo(Target other) {
		int diff = destination.compareTo(other.destination);
		if(diff != 0) return diff;
		if(portRange == null) {
			if(other.portRange == null) {
				return protocol.compareTo(other.protocol);
			} else {
				return 1;
			}
		} else {
			if(other.portRange == null) {
				return -1;
			} else {
				return portRange.compareTo(other.portRange);
			}
		}
	}

	/**
	 * Gets the destination network range for this target.
	 *
	 * @return The {@link InetAddressPrefix#normalize() normalized} destination
	 */
	public InetAddressPrefix getDestination() {
		return destination;
	}

	/**
	 * Gets the protocol for this target.
	 */
	public Protocol getProtocol() {
		return protocol;
	}

	/**
	 * Gets the optional port range for this target.
	 */
	public IPortRange getPortRange() {
		return portRange;
	}

	/**
	 * Combines this target with the given target if possible.
	 * <p>
	 * If have the same destination, combines by protocol and port range.
	 * If have the same protocol and port range, tries to combine by destination network prefixes.
	 * </p>
	 * <p>
	 * When combining by protocol and port range, a target with no port range
	 * matches all ports on that protocol.
	 * </p>
	 *
	 * @return  The new target that represents the union of this and the other target or {@code null}
	 *          when they cannot be combined.
	 */
	public Target coalesce(Target other) {
		if(this.protocol != other.protocol) {
			// Different protocols
			return null;
		}
		if(this.destination.equals(other.destination)) {
			if(this.portRange == null) {
				// This has no port range, use for all ports
				return this;
			} else if(other.portRange == null) {
				// Other has no port range, use for all ports
				return other;
			} else {
				IPortRange coalescedRange = this.portRange.coalesce(other.portRange);
				if(coalescedRange == null) {
					// Not combinable
					return null;
				} else if(coalescedRange == this.portRange) {
					return this;
				} else if(coalescedRange == other.portRange) {
					return other;
				} else {
					return new Target(destination, protocol, coalescedRange);
				}
			}
		} else if(ObjectUtils.equals(this.portRange, other.portRange)) {
			InetAddressPrefix coalescedDestination = this.destination.coalesce(other.destination);
			if(coalescedDestination == null) {
				// Not combinable
				return null;
			} else if(coalescedDestination == this.destination) {
				return this;
			} else if(coalescedDestination == other.destination) {
				return other;
			} else {
				return new Target(coalescedDestination, protocol, portRange);
			}
		} else {
			// Cannot combine by port range or destination
			return null;
		}
	}
}
