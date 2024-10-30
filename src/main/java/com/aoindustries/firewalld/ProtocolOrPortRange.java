/*
 * ao-firewalld - Java API for managing firewalld.
 * Copyright (C) 2017, 2019, 2021, 2022, 2024  AO Industries, Inc.
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

import com.aoapps.lang.NullArgumentException;
import com.aoapps.net.IPortRange;
import com.aoapps.net.Protocol;
import java.util.Objects;

/**
 * A {@link Protocol protocol}, and optional {@IPortRange port range}.
 *
 * @author  AO Industries, Inc.
 */
class ProtocolOrPortRange implements Comparable<ProtocolOrPortRange> {

  private final Protocol protocol;
  private final IPortRange portRange;

  ProtocolOrPortRange(Protocol protocol, IPortRange portRange) {
    this.protocol = NullArgumentException.checkNotNull(protocol, "protocol");
    this.portRange = portRange;
    assert portRange == null || portRange.getProtocol() == protocol;
  }

  ProtocolOrPortRange(IPortRange portRange) {
    this.portRange = NullArgumentException.checkNotNull(portRange, "portRange");
    this.protocol = portRange.getProtocol();
  }

  ProtocolOrPortRange(Protocol protocol) {
    this.protocol = NullArgumentException.checkNotNull(protocol, "protocol");
    this.portRange = null;
  }

  /**
   * {@inheritDoc}
   *
   * @return  The string in form <samp>[port[-range]/]protocol</samp>.
   *
   * @see  IPortRange#toString()
   * @see  Protocol#toString()
   */
  @Override
  public String toString() {
    if (portRange == null) {
      return protocol.toString();
    } else {
      return portRange.toString();
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ProtocolOrPortRange)) {
      return false;
    }
    ProtocolOrPortRange other = (ProtocolOrPortRange) obj;
    return
        protocol == other.protocol
            && Objects.equals(portRange, other.portRange);
  }

  @Override
  public int hashCode() {
    if (portRange == null) {
      return protocol.hashCode();
    } else {
      return portRange.hashCode();
    }
  }

  /**
   * Ordered by portRange, protocol; those with port ranges
   * before those that are protocol-only.
   *
   * @see  IPortRange#compareTo(com.aoapps.net.IPortRange)
   */
  @Override
  public int compareTo(ProtocolOrPortRange other) {
    if (portRange == null) {
      if (other.portRange == null) {
        return protocol.compareTo(other.protocol);
      } else {
        return 1;
      }
    } else {
      if (other.portRange == null) {
        return -1;
      } else {
        return portRange.compareTo(other.portRange);
      }
    }
  }

  /**
   * Gets the protocol.
   */
  Protocol getProtocol() {
    return protocol;
  }

  /**
   * Gets the optional port range.
   */
  IPortRange getPortRange() {
    return portRange;
  }

  /**
   * Combines this with the given if possible.
   *
   * <p>No port range matches all ports on that protocol.</p>
   *
   * @return  The new value that represents the union of this and the other or {@code null}
   *          when they cannot be combined.
   */
  ProtocolOrPortRange coalesce(ProtocolOrPortRange other) {
    if (this.protocol != other.protocol) {
      // Different protocols
      return null;
    }
    if (this.portRange == null) {
      // This has no port range, use for all ports
      return this;
    } else if (other.portRange == null) {
      // Other has no port range, use for all ports
      return other;
    } else {
      IPortRange coalescedRange = this.portRange.coalesce(other.portRange);
      if (coalescedRange == null) {
        // Not combinable
        return null;
      } else if (coalescedRange == this.portRange) {
        return this;
      } else if (coalescedRange == other.portRange) {
        return other;
      } else {
        return new ProtocolOrPortRange(protocol, coalescedRange);
      }
    }
  }
}
