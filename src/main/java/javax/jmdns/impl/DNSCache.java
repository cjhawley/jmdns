// Copyright 2003-2005 Arthur van Hoff Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package javax.jmdns.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.jmdns.impl.constants.DNSRecordClass;
import javax.jmdns.impl.constants.DNSRecordType;
import org.apache.commons.lang3.StringUtils;

/**
 * A table of DNS entries. This is a map table which can handle multiple entries with the same
 * name.
 * <p/>
 * Storing multiple entries with the same name is implemented using a linked list. This is hidden
 * from the user and can change in later implementation.
 * <p/>
 * Here's how to iterate over all entries:
 *
 * <pre>
 *       for (Iterator i=dnscache.allValues().iterator(); i.hasNext(); ) {
 *             DNSEntry entry = i.next();
 *             ...do something with entry...
 *       }
 * </pre>
 * <p/>
 * And here's how to iterate over all entries having a given name:
 *
 * <pre>
 *       for (Iterator i=dnscache.getDNSEntryList(name).iterator(); i.hasNext(); ) {
 *             DNSEntry entry = i.next();
 *           ...do something with entry...
 *       }
 * </pre>
 *
 * @author Arthur van Hoff, Werner Randelshofer, Rick Blair, Pierre Frisch
 */
public class DNSCache extends ConcurrentHashMap<String, List<DNSEntry>> {
  private static final long serialVersionUID = 3024739453186759259L;

  public DNSCache() {
    this(1024);
  }

  /**
   * @param map
   */
  public DNSCache(DNSCache map) {
    this(map != null ? map.size() : 1024);
    if (map != null) {
      this.putAll(map);
    }
  }

  /**
   * Create a table with a given initial size.
   */
  public DNSCache(int initialCapacity) {
    super(initialCapacity);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Object clone() throws CloneNotSupportedException {
    return new DNSCache(this);
  }

  /**
   * Returns all entries in the cache
   *
   * @return all entries in the cache
   */
  public Collection<DNSEntry> allValues() {
    return this.values().stream()
        .filter(Objects::nonNull).flatMap(List::stream).collect(Collectors.toList());
  }

  /**
   * Iterate only over items with matching name. Returns an list of DNSEntry or null. To retrieve
   * all entries, one must iterate over this linked list.
   *
   * @return list of DNSEntries
   */
  public Collection<? extends DNSEntry> getDNSEntryList(String name) {
    Collection<? extends DNSEntry> entryList = this._getDNSEntryList(name);
    if (entryList != null) {
      synchronized (entryList) {
        entryList = new ArrayList<DNSEntry>(entryList);
      }
    } else {
      entryList = Collections.emptyList();
    }
    return entryList;
  }

  private Collection<? extends DNSEntry> _getDNSEntryList(String name) {
    return this.get(StringUtils.lowerCase(name));
  }

  /**
   * Get a matching DNS entry from the table (using isSameEntry). Returns the entry that was found.
   *
   * @return DNSEntry
   */
  public DNSEntry getDNSEntry(DNSEntry dnsEntry) {
    DNSEntry result = null;
    if (dnsEntry != null) {
      Collection<? extends DNSEntry> entryList = this._getDNSEntryList(dnsEntry.getKey());
      if (entryList != null) {
        synchronized (entryList) {
          for (DNSEntry testDNSEntry : entryList) {
            if (testDNSEntry.isSameEntry(dnsEntry)) {
              result = testDNSEntry;
              break;
            }
          }
        }
      }
    }
    return result;
  }

  /**
   * Get a matching DNS entry from the table.
   *
   * @return DNSEntry
   */
  public DNSEntry getDNSEntry(String name, int type, DNSRecordClass recordClass) {
    DNSEntry result = null;
    Collection<? extends DNSEntry> entryList = this._getDNSEntryList(name);
    if (entryList != null) {
      synchronized (entryList) {
        for (DNSEntry testDNSEntry : entryList) {
          if (testDNSEntry.matchRecordType(type) && testDNSEntry.matchRecordClass(recordClass)) {
            result = testDNSEntry;
            break;
          }
        }
      }
    }
    return result;
  }

  /**
   * Get all matching DNS entries from the table.
   *
   * @return list of entries
   */
  public Collection<? extends DNSEntry> getDNSEntryList(String name, int type,
      DNSRecordClass recordClass) {
    Collection<? extends DNSEntry> entryList = this._getDNSEntryList(name);
    if (entryList != null) {
      synchronized (entryList) {
        entryList = new ArrayList<DNSEntry>(entryList);
        entryList.removeIf(testDNSEntry -> !testDNSEntry.matchRecordType(type) || (!testDNSEntry
            .matchRecordClass(recordClass)));
      }
    } else {
      entryList = Collections.emptyList();
    }
    return entryList;
  }

  /**
   * Adds an entry to the table.
   *
   * @return true if the entry was added
   */
  public boolean addDNSEntry(final DNSEntry dnsEntry) {
    boolean result = false;
    if (dnsEntry != null) {
      List<DNSEntry> entryList = this.get(dnsEntry.getKey());
      if (entryList == null) {
        this.putIfAbsent(dnsEntry.getKey(), new ArrayList<>());
        entryList = this.get(dnsEntry.getKey());
      }
      synchronized (entryList) {
        entryList.add(dnsEntry);
      }
      // This is probably not very informative
      result = true;
    }
    return result;
  }

  /**
   * Removes a specific entry from the table. Returns true if the entry was found.
   *
   * @return true if the entry was removed
   */
  public boolean removeDNSEntry(DNSEntry dnsEntry) {
    boolean result = false;
    if (dnsEntry != null) {
      List<DNSEntry> entryList = this.get(dnsEntry.getKey());
      if (entryList != null) {
        synchronized (entryList) {
          entryList.remove(dnsEntry);
        }
      }
    }
    return result;
  }

  /**
   * Replace an existing entry by a new one.<br/>
   * <b>Note:</b> the 2 entries must have the same key.
   *
   * @return <code>true</code> if the entry has been replace, <code>false</code> otherwise.
   */
  public boolean replaceDNSEntry(DNSEntry newDNSEntry, DNSEntry existingDNSEntry) {
    boolean result = false;
    if ((newDNSEntry != null) && (existingDNSEntry != null) && (newDNSEntry.getKey()
        .equals(existingDNSEntry.getKey()))) {
      List<DNSEntry> entryList = this.get(newDNSEntry.getKey());
      if (entryList == null) {
        this.putIfAbsent(newDNSEntry.getKey(), new ArrayList<>());
        entryList = this.get(newDNSEntry.getKey());
      }
      synchronized (entryList) {
        entryList.remove(existingDNSEntry);
        entryList.add(newDNSEntry);
      }
      // This is probably not very informative
      result = true;
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized String toString() {
    StringBuilder aLog = new StringBuilder(2000);
    aLog.append("\t---- cache ----");
    Enumeration<String> keyIter = this.keys();
    while (keyIter.hasMoreElements()) {
      String key = keyIter.nextElement();
      aLog.append("\n\t\t");
      aLog.append("\n\t\tname '");
      aLog.append(key);
      aLog.append("' ");
      List<? extends DNSEntry> entryList = this.get(key);
      if ((entryList != null) && (!entryList.isEmpty())) {
        synchronized (entryList) {
          for (DNSEntry entry : entryList) {
            aLog.append("\n\t\t\t");
            aLog.append(entry.toString());
          }
        }
      } else {
        aLog.append(" no entries");
      }
    }
    return aLog.toString();
  }

}
