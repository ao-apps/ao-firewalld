# [<img src="ao-logo.png" alt="AO Logo" width="35" height="40">](https://github.com/aoindustries) [AO firewalld](https://github.com/aoindustries/ao-firewalld)
<p>
	<a href="https://aoindustries.com/life-cycle#project-current-stable">
		<img src="https://aoindustries.com/ao-badges/project-current-stable.svg" alt="project: current stable" />
	</a>
	<a href="https://aoindustries.com/life-cycle#management-production">
		<img src="https://aoindustries.com/ao-badges/management-production.svg" alt="management: production" />
	</a>
	<a href="https://aoindustries.com/life-cycle#packaging-active">
		<img src="https://aoindustries.com/ao-badges/packaging-active.svg" alt="packaging: active" />
	</a>
	<br />
	<a href="https://docs.oracle.com/javase/8/docs/api/">
		<img src="https://aoindustries.com/ao-badges/java-8.svg" alt="java: &gt;= 8" />
	</a>
	<a href="http://semver.org/spec/v2.0.0.html">
		<img src="https://aoindustries.com/ao-badges/semver-2.0.0.svg" alt="semantic versioning: 2.0.0" />
	</a>
	<a href="https://www.gnu.org/licenses/lgpl-3.0">
		<img src="https://aoindustries.com/ao-badges/license-lgpl-3.0.svg" alt="license: LGPL v3" />
	</a>
</p>

Java API for managing [firewalld](http://www.firewalld.org/).

## Project Links
* [Project Home](https://aoindustries.com/ao-firewalld/)
* [Changelog](https://aoindustries.com/ao-firewalld/changelog)
* [API Docs](https://aoindustries.com/ao-firewalld/apidocs/)
* [Maven Central Repository](https://search.maven.org/artifact/com.aoindustries/ao-firewalld)
* [GitHub](https://github.com/aoindustries/ao-firewalld)

## Features
* Clean programmatic access to [firewalld](http://www.firewalld.org/).
* Supports fine-grained control over specific port and IP address combinations.
* Manages sets of services because firewalld is limited to only one &lt;destination /&gt; per service.
* Optimizes arbitrary sets of ports and IP addresses into a minimal set of service files.
* Small footprint, minimal dependencies - not part of a big monolithic package.

## Motivation
The [AOServ Platform](https://aoindustries.com/aoserv/) allows opening ports on a per-IP basis.  [firewalld](http://www.firewalld.org/) service files are limited to a single &lt;destination /&gt; per service file.  To selectively open ports on a per-IP basis, additional service files must be managed.  This is tedious if done manually.  We would rather [firewalld](http://www.firewalld.org/) support multiple &lt;service /&gt; tags with multiple &lt;destination /&gt; per service file, but this is not currently a feature.

Our server configuration process, [AOServ Daemon](https://github.com/aoindustries/aoserv-daemon), is written in the Java programming language.  We desire a clean interface to [firewalld](http://www.firewalld.org/) without having to operate with `firewall-cmd` and other commands directly.

## Evaluated Alternatives
We were unable to find any existing implementations via [GitHub](https://github.com/search?utf8=%E2%9C%93&q=java+firewalld&type=Repositories&ref=searchresults), [The Central Repository](http://search.maven.org/#search|ga|1|firewalld), or [Google Search](https://www.google.com/search?q=java+api+for+firewalld).

## Contact Us
For questions or support, please [contact us](https://aoindustries.com/contact):

Email: [support@aoindustries.com](mailto:support@aoindustries.com)  
Phone: [1-800-519-9541](tel:1-800-519-9541)  
Phone: [+1-251-607-9556](tel:+1-251-607-9556)  
Web: https://aoindustries.com/contact
