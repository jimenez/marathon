package mesosphere.marathon.api.v2.serialization

import mesosphere.marathon.Protos
import mesosphere.marathon.state.{ Container, Parameter }
import mesosphere.marathon.state.Container.{ Docker, PersistentVolumeInfo, DockerVolume, PersistentVolume, Volume }
import org.apache.mesos

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

object ContainerSerializer {
  def toProto(container: Container): Protos.ExtendedContainerInfo = {
    val builder = Protos.ExtendedContainerInfo.newBuilder
      .setType(container.`type`)
      .addAllVolumes(container.volumes.map(VolumeSerializer.toProto).asJava)
    container.docker.foreach { d => builder.setDocker(DockerSerializer.toProto(d)) }
    builder.build
  }

  def fromProto(proto: Protos.ExtendedContainerInfo): Container = {
    val maybeDocker = if (proto.hasDocker) Some(Docker(proto.getDocker)) else None
    Container(
      `type` = proto.getType,
      volumes = proto.getVolumesList.asScala.map(Container.Volume(_)).to[Seq],
      docker = maybeDocker
    )
  }

  def toMesos(container: Container): mesos.Protos.ContainerInfo = {
    // we can only serialize DockerVolumes into a Mesos Protobuf.
    // PersistentVolumes and ExternalVolumes are handled differently
    val serializedVolumes = container.volumes.collect { case dv: DockerVolume => VolumeSerializer.toMesos(dv) }
    val builder = mesos.Protos.ContainerInfo.newBuilder
      .setType(container.`type`)
      .addAllVolumes(serializedVolumes.asJava)
    container.docker.foreach { d => builder.setDocker(DockerSerializer.toMesos(d)) }
    builder.build
  }

  /**
    * Lossy conversion for backwards compatibility with deprecated
    * container representation.
    */
  def fromMesos(proto: mesos.Protos.CommandInfo.ContainerInfo): Container =
    Container(
      `type` = mesos.Protos.ContainerInfo.Type.DOCKER,
      docker = Some(Docker(proto.getImage))
    )

  /**
    * Lossy conversion for backwards compatibility with deprecated
    * container representation.
    */
  def fromMesos(proto: Protos.ContainerInfo): Container =
    Container(
      `type` = mesos.Protos.ContainerInfo.Type.DOCKER,
      docker = Some(Docker(proto.getImage.toStringUtf8))
    )

}

object VolumeSerializer {

  def toProto(volume: Volume): Protos.Volume = volume match {
    case p: PersistentVolume =>
      Protos.Volume.newBuilder()
        .setContainerPath(p.containerPath)
        .setPersistent(PersistentVolumeInfoSerializer.toProto(p.persistent))
        .setMode(p.mode)
        .build()

    case d: DockerVolume =>
      Protos.Volume.newBuilder()
        .setContainerPath(d.containerPath)
        .setHostPath(d.hostPath)
        .setMode(d.mode)
        .build()
  }

  /** Only DockerVolumes can be serialized into a Mesos Protobuf */
  def toMesos(volume: DockerVolume): mesos.Protos.Volume =
    mesos.Protos.Volume.newBuilder
      .setContainerPath(volume.containerPath)
      .setHostPath(volume.hostPath)
      .setMode(volume.mode)
      .build
}

object PersistentVolumeInfoSerializer {
  def toProto(info: PersistentVolumeInfo): Protos.PersistentVolumeInfo =
    Protos.PersistentVolumeInfo.newBuilder()
      .setSize(info.size)
      .build()
}

object DockerSerializer {
  def toProto(docker: Container.Docker): Protos.ExtendedContainerInfo.DockerInfo = {
    val builder = Protos.ExtendedContainerInfo.DockerInfo.newBuilder

    builder.setImage(docker.image)

    docker.network foreach builder.setNetwork

    docker.portMappings.foreach { pms =>
      builder.addAllPortMappings(pms.map(PortMappingSerializer.toProto).asJava)
    }

    builder.setPrivileged(docker.privileged)

    builder.addAllParameters(docker.parameters.map(ParameterSerializer.toMesos).asJava)

    builder.setForcePullImage(docker.forcePullImage)

    builder.build
  }

  def toMesos(docker: Container.Docker): mesos.Protos.ContainerInfo.DockerInfo = {
    val builder = mesos.Protos.ContainerInfo.DockerInfo.newBuilder

    builder.setImage(docker.image)

    docker.network foreach builder.setNetwork

    docker.portMappings.foreach { pms =>
      builder.addAllPortMappings(pms.map(PortMappingSerializer.toMesos).asJava)
    }

    builder.setPrivileged(docker.privileged)

    builder.addAllParameters(docker.parameters.map(ParameterSerializer.toMesos).asJava)

    builder.setForcePullImage(docker.forcePullImage)

    builder.build
  }
}

object PortMappingSerializer {
  def toProto(mapping: Container.Docker.PortMapping): Protos.ExtendedContainerInfo.DockerInfo.PortMapping = {
    Protos.ExtendedContainerInfo.DockerInfo.PortMapping.newBuilder
      .setContainerPort(mapping.containerPort)
      .setHostPort(mapping.hostPort)
      .setProtocol(mapping.protocol)
      .setServicePort(mapping.servicePort)
      .build
  }

  def toMesos(mapping: Container.Docker.PortMapping): mesos.Protos.ContainerInfo.DockerInfo.PortMapping = {
    mesos.Protos.ContainerInfo.DockerInfo.PortMapping.newBuilder
      .setContainerPort(mapping.containerPort)
      .setHostPort(mapping.hostPort)
      .setProtocol(mapping.protocol)
      .build
  }

}

object ParameterSerializer {
  def toMesos(param: Parameter): mesos.Protos.Parameter =
    mesos.Protos.Parameter.newBuilder
      .setKey(param.key)
      .setValue(param.value)
      .build

}
