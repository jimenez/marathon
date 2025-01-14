---
title: Setting Up and Running Marathon
---

## Setting Up And Running Marathon


### Requirements

* [Apache Mesos][Mesos] 0.24.0+
* [Apache ZooKeeper][ZooKeeper]
* JDK 1.8+ 

### Installation

#### Install Mesos

One easy way is via your system's package manager.
Current builds and instructions on how to set up repositories for major Linux distributions are available on the Mesosphere [downloads page](http://mesosphere.com/downloads/).

If building from source, see the
Mesos [Getting Started](http://mesos.apache.org/gettingstarted/) page or the
[Mesosphere tutorial](http://mesosphere.com/2013/08/01/distributed-fault-tolerant-framework-apache-mesos/)
for details. Running `make install` will install Mesos in `/usr/local` in
the same way as these packages do.

#### Install Marathon

#### Through your Package Manager

Marathon packages are available from Mesosphere's [repositories](http://mesosphere.com/2014/07/17/mesosphere-package-repositories/).

#### From a Tarball

Download and unpack the latest Marathon release.

``` bash
$ curl -O http://downloads.mesosphere.com/marathon/v0.15.1/marathon-0.15.1.tgz
$ tar xzf marathon-0.15.1.tgz
```

SHA-256 checksums are available by appending `.sha256` to the URLs.

### Versioning

Starting with version 0.9.0 Marathon will adhere to [semantic versioning](http://semver.org).
That means we are committed to keep our documented REST API compatible across releases unless we change the MAJOR version
(the first number in the version tuple). If you depend on undocumented features, please tell us about them by [raising a GitHub issue](https://github.com/mesosphere/marathon/issues/new). API parts which we explicitly marked as EXPERIMENTAL are exempted from this rule. We will not introduce new features in PATCH version increments (the last number in the version tuple).

We might change the command line interfaces of the Marathon server process in rare cases in a MINOR version upgrade.
Please check the release notes for these.

Furthermore, we will provide release candidates for all new MAJOR/MINOR versions and invite our users to test them and
give us feedback (particularly on violations of the versioning policy).

### Upgrading to a Newer Version

[See here]({{ site.baseurl }}/docs/upgrade/index.html) for our guide on upgrading to a new version.

### Running in Production Mode

To launch Marathon in *production mode*, you need to have both
ZooKeeper and Mesos running. The following command launches
Marathon in *production mode*. Point your web browser to
`localhost:8080` and you should see the [Marathon UI]({{ site.baseurl }}/docs/marathon-ui.html).

``` console
$ ./bin/start --master zk://zk1.foo.bar:2181,zk2.foo.bar:2181/mesos --zk zk://zk1.foo.bar:2181,zk2.foo.bar:2181/marathon
```

Marathon uses `--master` to find the Mesos masters, and `--zk` to find ZooKeepers
for storing state. They are separate options because Mesos masters can be
discovered in other ways as well.

For all configuration options, see the [command line flags](command-line-flags.html) doc. For more information on the high-availability feature of Marathon, see the [high availability](high-availability.html) doc.

### Mesos Library

`MESOS_NATIVE_JAVA_LIBRARY`: `bin/start` searches the common installation paths,
`/usr/lib` and `/usr/local/lib`, for the Mesos native library. If the
library lives elsewhere in your configuration, set the environment variable
`MESOS_NATIVE_JAVA_LIBRARY` to its full path.

For example:

```console
$ MESOS_NATIVE_JAVA_LIBRARY=/Users/bob/libmesos.dylib ./bin/start --master local --zk zk://localhost:2181/marathon
```

### Launch an Application

For an introduction to Marathon application definitions and how they are executed, see [Application Basics](application-basics.html).

[Mesos]: https://mesos.apache.org/ "Apache Mesos"
[Zookeeper]: https://zookeeper.apache.org/ "Apache ZooKeeper"
