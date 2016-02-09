---
title: Service Discovery & Load Balancing
---

# Service Discovery & Load Balancing

Once our app is up and running, we need a way to send traffic to it, from other applications on the same cluster and from external clients.

There are several mechanisms available to accomplish this:
* [Mesos-DNS](https://github.com/mesosphere/mesos-dns) provides service discovery through the domain name system ([DNS](http://en.wikipedia.org/wiki/Domain_Name_System))
* [Marathon-lb](https://github.com/mesosphere/marathon-lb) provides port based service discovery using HAProxy, a lightweight TCP/HTTP proxy
* [haproxy-marathon-bridge](https://github.com/mesosphere/marathon/blob/master/examples/haproxy-marathon-bridge) is an example script which configures a local HAProxy installation

For a detailed description of how ports work in Marathon, see [Ports](ports.html).

## Mesos-DNS

Mesos-DNS generates a SRV record for each Mesos task (including Marathon application instances) and translates these records to the IP address and port on the machine currently running each application.

Mesos-DNS is particularly useful when:
* apps are launched through multiple frameworks (not just Marathon)
* you're using an IP per container solution like [Project Calico](http://www.projectcalico.org/)
* you use random host port assignments in Marathon

Check the Mesos-DNS [documentation and tutorials page](http://mesosphere.github.io/mesos-dns/) for further information.

## Marathon-lb

An alternative way to implement service discovery is to run a TCP/HTTP proxy on each host in the cluster, and transparently forward connections to the static service port on localhost to the dynamically assigned host/port combinations of the individual Marathon application instances (running Mesos *tasks*). That way, clients simply connect to the well known defined service port, and do not need to know the implementation details of discovery.This approach is sufficient if all apps are launched through Marathon.

Marathon-lb is a Dockerized application that includes both HAProxy an application that uses Marathon's REST API to regenerate the HAProxy configuration. It supports more advanced functionality like SSL offloading, sticky connections and VHost based load balancing, allowing you to specify virtual hosts for your Marathon applications.

When using Marathon-lb, note that it is not necessary to set `requirePorts` to `true`, as described in the [ports documentation](ports.html).

See the [Marathon-lb repository](https://github.com/mesosphere/marathon-lb) for more information or check out [the tutorial on the Mesosphere blog](https://mesosphere.com/blog/2015/12/04/dcos-marathon-lb/).

## haproxy-marathon-bridge

Marathon ships with a simple shell script called `haproxy-marathon-bridge` which uses Marathon's REST API to create a config file for HAProxy. The `haproxy-marathon-bridge` provides a minimum set of functionality and is easier to understand for beginners or as a good starting point for a custom implementation. Note that this script itself is now deprecated and should not be used as-is in production. For production use, please consider using Marathon-lb, above.

To generate an HAProxy configuration from Marathon running at `localhost:8080` with the `haproxy-marathon-bridge` script:

``` console
$ ./bin/haproxy-marathon-bridge localhost:8080 > /etc/haproxy/haproxy.cfg
```

To reload the HAProxy configuration without interrupting existing connections:

``` console
$ haproxy -f haproxy.cfg -p haproxy.pid -sf $(cat haproxy.pid)
```

The configuration script and reload could be triggered frequently by Cron, to
keep track of topology changes. If a node goes away between reloads, HAProxy's
health check will catch it and stop sending traffic to that node.

To facilitate this setup, the `haproxy-marathon-bridge` script can be invoked in
an alternate way which installs the script itself, HAProxy and a cronjob that
once a minute pings one of the Marathon servers specified and refreshes
HAProxy if anything has changed.

``` console
$ ./bin/haproxy-marathon-bridge install_haproxy_system localhost:8080
```

- The list of Marathons to ping is stored one per line in
  `/etc/haproxy-marathon-bridge/marathons`
- The script is installed as `/usr/local/bin/haproxy-marathon-bridge`
- The cronjob is installed as `/etc/cron.d/haproxy-marathon-bridge`
  and run as root.

The provided script is just a basic example. For a full list of options, check the
[HAProxy configuration docs](http://cbonte.github.io/haproxy-dconv/configuration-1.5.html).
