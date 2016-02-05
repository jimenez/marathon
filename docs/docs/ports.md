---
title: Ports
---

# Ports

Port configuration for applications in Marathon can be confusing and there is [an outstanding issue](https://github.com/mesosphere/marathon/issues/670) to redesign the ports API. This page attempts to explain more clearly how they work.

## Definitions

*containerPort*: A _container port_ specifies a port within a container. This is only necessary as part of a _port mapping_ when using `BRIDGE` mode networking with a Docker container.

*hostPort*: A _host port_ specifies a port on the host to bind to. This is only necessary as part of a _port mapping_ when using `BRIDGE` mode networking with a Docker container.

*BRIDGE networking*: `BRIDGE` mode networking is used by Docker applications that specify `BRIDGE` mode networking. In this mode, container ports (a port within the container) are mapped to host ports (a port on the host machine). In this mode, applications bind to the specified ports within the container and Docker networking binds to the specified ports on the host.

*HOST networking*: `HOST` networking is used by non-Docker Marathon applications and Docker applications that use `HOST` mode networking. In this mode, applications bind directly to one or more ports on the host machine.

*portMapping*: A _port mapping_ is necessary for Docker applications that use `BRIDGE` mode networking and is a tuple containing a host port, container port, service port and protocol. Multiple _port mappings_ may be specified for a Marathon application.

*ports*: The _ports_ array is used to define ports that should be considered as part of a resource offer. It is necessary only to define this array if you are using `HOST` networking and no port mappings are specified.

*protocol*: _Protocol_ specifies the internet protocol to use for a port (e.g. `tcp` or `udp`). This is only necessary as part of a _port mapping_ when using `BRIDGE` mode networking with a Docker container.

*requirePorts*: _requirePorts_ is a property that specifies whether Marathon should specifically look for specified ports in the resource offers it receives. This ensures that these ports are free and available to be bound to on the Mesos agent. This does not apply to `BRIDGE` mode networking.

*servicePort*: A _service port_ is a port used to describe the port that a service should made available at. Marathon does not bind to the service ports specified but will ensure that you cannot have multiple applications that use the same service port running on the same host. Service ports are typically only used by external applications (e.g. HAProxy) to make the application available at the specified port. See [Service Discovery & Load Balancing](service-discovery-load-balancing.html) for more information.

## Random Port Assignment

Using the value 0 for any port settings indicates to Marathon that you would like a random port assignment. However, if containerPort is set to 0 within a portMapping, it is set to the same value as hostPort.

## Environment Variables

Each port value is exposed to the running application instance via environment variables `$PORT0`, `$PORT1`, etc. Each Marathon application is given a single port by default, so `$PORT0` is always available. These variables are available inside a Docker container being run by Marathon too.

## Example Configuration

### Host Mode

Host mode networking is the default networking mode for Docker containers and the only networking mode for non-Docker applications. Note that it not necessary to `EXPOSE` ports in your Dockerfile.

#### Enabling Host Mode

Host mode is enabled by default for containers. If you wish to be explicit, you can also specify it manually through the `network` property:

```json
  "container": {
    "type": "DOCKER",
    "docker": {
      "image": "my-image:1.0",
      "network": "HOST"
    }
  },
```

For non-Docker applications, you don't need to specify anything.

#### Specifying Ports

You can specify the ports that are available through the `ports` array:

```json
    "ports": [
        0, 0, 0
    ],
```

In this example, we specify three randomly assigned ports which would then be available to our command via the environment variables `$PORT0`, `$PORT1` and `$PORT2`.

These ports also become service ports for your application and are reserved cluster wide.

You can also specify specific ports:

```json
    "ports": [
        2001, 2002, 3000
    ],
```

In this case, `$PORT0`, `$PORT1` and `$PORT3` remain randomly assigned. However, the service ports for this application are now `2001`, `2002` and `3000`.

If you want the actual ports available to your application, you can set `requirePorts` to `true` (`requirePorts` is `false` by default). This will tell Marathon to only schedule this application on agents which have these ports available:

```json
    "ports": [
        2001, 2002, 3000
    ],
    "requirePorts" : true
```

The service ports and the environment variables`$PORT0`, `$PORT1`, and `$PORT2` are both now `2001`, `2002` and `3000` respectively.

This property is useful if you don't use a service discovery solution such as HAProxy to proxy requests on service ports.

#### Referencing Ports

You can reference ports in the Dockerfile for our fictitious app as follows:

```sh
CMD ./my-app --http-port=$PORT0 --https-port=$PORT1 --monitoring-port=$PORT2
```

Alternatively, if you aren't using Docker or had specified a `cmd` in your Marathon application definition, it works in the same way:

```json
    "cmd": "./my-app --http-port=$PORT0 --https-port=$PORT1 --monitoring-port=$PORT2"
```

### Bridge Mode

Bridge mode networking allows you to map host ports to ports inside your container and is only applicable to Docker containers. It is particularly useful if you are using a container image with fixed port assignments that you can't modify. Note that it not necessary to `EXPOSE` ports in your Dockerfile.

#### Enabling Bridge Mode

You need to specify bridge mode through the `network` property:

```json
  "container": {
    "type": "DOCKER",
    "docker": {
      "image": "my-image:1.0",
      "network": "BRIDGE"
    }
  },
```

#### Specifying Ports

Port mappings are similar to passing -p into the Docker command line and specify a relationship between a port on the host machine and a port inside the container.

Port mappings are specified inside the `portMappings` object for a `container`:

```json
  "container": {
    "type": "DOCKER",
    "docker": {
      "image": "my-image:1.0",
      "network": "BRIDGE",
      "portMappings": [
        { "containerPort": 0, "hostPort": 0 },
        { "containerPort": 0, "hostPort": 0 },
        { "containerPort": 0, "hostPort": 0 }
      ]
    }
  },
```

In this example, we specify 3 mappings. A value of 0 will ask Marathon to randomly assign a value for `hostPort`. In this case, setting `containerPort` to 0 will cause it to have the same value as `hostPort`. These values are available inside the container as `$PORT0`, `$PORT1` and `$PORT2` respectively.

Alternatively, if our process running in the container had fixed ports, we might do something like the following:

```json
  "container": {
    "type": "DOCKER",
    "docker": {
      "image": "my-image:1.0",
      "network": "BRIDGE",
      "portMappings": [
        { "containerPort": 80, "hostPort": 0 },
        { "containerPort": 443, "hostPort": 0 },
        { "containerPort": 4000, "hostPort": 0 }
      ]
    }
  },
```

In this case, Marathon will randomly allocate host ports and map these to ports `80`, `443` and `4000` respectively. It's important to note that the `$PORT` variables refer to the host ports. In this case, `$PORT0` will be set to the value of `hostPort` for the first mapping and so on.

##### Specifying Protocol

You can also specify the protocol for these port mappings. The default is `tcp`:

```json
  "container": {
    "type": "DOCKER",
    "docker": {
      "image": "my-image:1.0",
      "network": "BRIDGE",
      "portMappings": [
        { "containerPort": 80, "hostPort": 0, "protocol": "tcp" },
        { "containerPort": 443, "hostPort": 0, "protocol": "tcp" },
        { "containerPort": 4000, "hostPort": 0, "protocol": "udp" }
      ]
    }
  },
```

##### Specifying Service Ports

By default, Marathon will be creating service ports for each of these ports and assigning them random values. Service ports are used by service discovery solutions and it is often desirable to set these to well known values. You can do this by setting a `servicePort` for each mapping:

```json
  "container": {
    "type": "DOCKER",
    "docker": {
      "image": "my-image:1.0",
      "network": "BRIDGE",
      "portMappings": [
        { "containerPort": 80, "hostPort": 0, "protocol": "tcp", "servicePort": 2000 },
        { "containerPort": 443, "hostPort": 0, "protocol": "tcp", "servicePort": 2001 },
        { "containerPort": 4000, "hostPort": 0, "protocol": "udp", "servicePort": 3000}
      ]
    }
  },
```

In this example, the host ports `$PORT0`, `$PORT1` and `$PORT3` remain randomly assigned. However, the service ports for this application are now `2001`, `2002` and `3000`. An external proxy, like HAProxy, should be configured to route from the service ports to the host ports.

#### Referencing Ports

If you set `containerPort` to 0, then you should specify ports in the Dockerfile for our fictitious app as follows:

```sh
CMD ./my-app --http-port=$PORT0 --https-port=$PORT1 --monitoring-port=$PORT2
```

However, if you've specified `containerPort` values, you simply use the same values in the Dockerfile:

```sh
CMD ./my-app --http-port=80 --https-port=443 --monitoring-port=4000
```

Alternatively, you can specify a `cmd` in your Marathon application definition, it works in the same way as before:

```json
    "cmd": "./my-app --http-port=$PORT0 --https-port=$PORT1 --monitoring-port=$PORT2"
```

Or, if you've used fixed values:

```json
    "cmd": "./my-app --http-port=80 --https-port=443 --monitoring-port=4000"
```
