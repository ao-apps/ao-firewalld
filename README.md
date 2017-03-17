# [<img src="ao-logo.png" alt="AO Logo" width="35" height="40">](https://aoindustries.com/) [AO firewalld](https://aoindustries.com/ao-firewalld/)
Java API for managing [firewalld](http://www.firewalld.org/).

## Features
* Clean programmatic access to [firewalld](http://www.firewalld.org/).
* Supports fine-grained control over specific port and IP address combinations.
* Manages sets of services because firewalld is limited in only one <destination /> per service.
* Optimizes arbitrary sets of ports and IP addresses into a minimal set of service files.
* Small footprint, minimal dependencies - not part of a big monolithic package.

## Motivation
The [AOServ Platform](https://aoindustries.com/aoserv/) allows opening ports on a per-IP basis.  [firewalld](http://www.firewalld.org/) service files are limited to a single <destination /> per service file.  To selectively open ports on a per-IP basis, additional service files must be managed.  This is tedious if done manually.  We would rather [firewalld](http://www.firewalld.org/) support multiple <service /> tags with multiple <destination /> per service file, but this is not currently a feature.

Our server configuration process, [AOServ Daemon](https://aoindustries.com/aoserv/daemon/), is written in the Java programming language.  We desire a clean interface to [firewalld](http://www.firewalld.org/) without having to operate with `firewall-cmd` and other commands directly.

## Evaluated Alternatives
We were unable to find any existing implementations via [GitHub](https://github.com/search?utf8=%E2%9C%93&q=java+firewalld&type=Repositories&ref=searchresults), [The Central Repository](http://search.maven.org/#search|ga|1|firewalld), or [Google Search](https://www.google.com/search?q=java+api+for+firewalld).

## Project Links
* [Project Home](https://aoindustries.com/ao-firewalld/)
* [Changelog](https://aoindustries.com/ao-firewalld/changelog)
* [API Docs](https://aoindustries.com/ao-firewalld/apidocs/)
* [Maven Central Repository](https://search.maven.org/#search%7Cgav%7C1%7Cg:%22com.aoindustries%22%20AND%20a:%22ao-firewalld%22)
* [GitHub](https://github.com/aoindustries/ao-firewalld)

## Contact Us
For questions or support, please [contact us](https://aoindustries.com/contact):

Email: [support@aoindustries.com](mailto:support@aoindustries.com)  
Phone: [1-800-519-9541](tel:1-800-519-9541)  
Phone: [+1-251-607-9556](tel:+1-251-607-9556)  
Web: https://aoindustries.com/contact
