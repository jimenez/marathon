package mesosphere.marathon.state

import com.wix.accord.dsl._
import com.wix.accord.{ RuleViolation, Failure, Result, Validator }
import mesosphere.marathon.Protos
import mesosphere.marathon.api.v2.Validation._
import org.apache.mesos.{ Protos => Mesos }
import org.apache.mesos.Protos.Volume.Mode
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

// TODO: trait Container and specializations?
// Current implementation with type defaulting to DOCKER and docker to NONE makes no sense
case class Container(
  `type`: Mesos.ContainerInfo.Type = Mesos.ContainerInfo.Type.DOCKER,
  volumes: Seq[Container.Volume] = Nil,
  docker: Option[Container.Docker] = None)

object Container {

  object Empty extends Container

  sealed trait Volume {
    def containerPath: String
    def mode: Mesos.Volume.Mode
  }

  object Volume {
    def apply(
      containerPath: String,
      hostPath: Option[String],
      mode: Mesos.Volume.Mode,
      persistent: Option[PersistentVolumeInfo]): Volume =
      persistent match {
        case Some(persistentVolumeInfo) =>
          PersistentVolume(
            containerPath = containerPath,
            persistent = persistentVolumeInfo,
            mode = mode
          )
        case None =>
          DockerVolume(
            containerPath = containerPath,
            hostPath = hostPath.getOrElse(""),
            mode = mode
          )
      }

    def apply(proto: Protos.Volume): Volume = {
      val persistent: Option[PersistentVolumeInfo] =
        if (proto.hasPersistent) Some(PersistentVolumeInfo(proto.getPersistent.getSize)) else None

      persistent match {
        case Some(persistentVolumeInfo) =>
          PersistentVolume(
            containerPath = proto.getContainerPath,
            persistent = persistentVolumeInfo,
            mode = proto.getMode
          )
        case None =>
          DockerVolume(
            containerPath = proto.getContainerPath,
            hostPath = proto.getHostPath,
            mode = proto.getMode
          )
      }
    }

    def apply(proto: Mesos.Volume): Volume =
      DockerVolume(
        containerPath = proto.getContainerPath,
        hostPath = proto.getHostPath,
        mode = proto.getMode
      )

    def unapply(volume: Volume): Option[(String, Option[String], Mesos.Volume.Mode, Option[PersistentVolumeInfo])] =
      volume match {
        case persistentVolume: PersistentVolume =>
          Some((persistentVolume.containerPath, None, persistentVolume.mode, Some(persistentVolume.persistent)))
        case dockerVolume: DockerVolume =>
          Some((dockerVolume.containerPath, Some(dockerVolume.hostPath), dockerVolume.mode, None))
      }
  }

  /**
    * A volume mapping either from host to container or vice versa.
    * Both paths can either refer to a directory or a file.  Paths must be
    * absolute.
    */
  case class DockerVolume(
    containerPath: String,
    hostPath: String,
    mode: Mesos.Volume.Mode)
      extends Volume

  object DockerVolume {

    implicit val dockerVolumeValidator = validator[DockerVolume] { vol =>
      vol.containerPath is notEmpty
      vol.hostPath is notEmpty
      vol.mode is oneOf(Mode.RW, Mode.RO)
    }
  }

  case class PersistentVolumeInfo(size: Long)

  object PersistentVolumeInfo {
    implicit val persistentVolumeInfoValidator = validator[PersistentVolumeInfo] { info =>
      (info.size > 0) is true
    }
  }

  case class PersistentVolume(
    containerPath: String,
    persistent: PersistentVolumeInfo,
    mode: Mesos.Volume.Mode)
      extends Volume

  object PersistentVolume {
    import org.apache.mesos.Protos.Volume.Mode
    implicit val persistentVolumeValidator = validator[PersistentVolume] { vol =>
      vol.containerPath is notEmpty
      vol.persistent is valid
      vol.mode is equalTo(Mode.RW)
    }
  }

  /**
    * Docker-specific container parameters.
    */
  case class Docker(
    image: String = "",
    network: Option[Mesos.ContainerInfo.DockerInfo.Network] = None,
    portMappings: Option[Seq[Docker.PortMapping]] = None,
    privileged: Boolean = false,
    parameters: Seq[Parameter] = Nil,
    forcePullImage: Boolean = false)

  object Docker {
    def apply(proto: Protos.ExtendedContainerInfo.DockerInfo): Docker =
      Docker(
        image = proto.getImage,

        network = if (proto.hasNetwork) Some(proto.getNetwork) else None,

        portMappings = {
          val pms = proto.getPortMappingsList.asScala

          if (pms.isEmpty) None
          else Some(pms.map(PortMapping(_)).to[Seq])
        },

        privileged = proto.getPrivileged,

        parameters = proto.getParametersList.asScala.map(Parameter(_)).to[Seq],

        forcePullImage = if (proto.hasForcePullImage) proto.getForcePullImage else false
      )

    /**
      * @param containerPort The container port to expose
      * @param hostPort      The host port to bind
      * @param servicePort   The well-known port for this service
      * @param protocol      Layer 4 protocol to expose (i.e. tcp, udp).
      */
    case class PortMapping(
        containerPort: Int = 0,
        hostPort: Int = 0,
        servicePort: Int = 0,
        protocol: String = "tcp") {

      require(protocol == "tcp" || protocol == "udp", "protocol can only be 'tcp' or 'udp'")
    }

    object PortMapping {
      def apply(proto: Protos.ExtendedContainerInfo.DockerInfo.PortMapping): PortMapping =
        PortMapping(
          proto.getContainerPort,
          proto.getHostPort,
          proto.getServicePort,
          proto.getProtocol
        )
    }

  }

  // We need validation based on the container type, but don't have dedicated classes. Therefore this approach manually
  // delegates validation to the matching validator
  implicit val containerValidator: Validator[Container] = {
    val volumeValidator: Validator[Volume] = new Validator[Volume] {
      override def apply(volume: Volume): Result = volume match {
        case pv: PersistentVolume => valid[PersistentVolume](PersistentVolume.persistentVolumeValidator).apply(pv)
        case dv: DockerVolume     => valid[DockerVolume](DockerVolume.dockerVolumeValidator).apply(dv)
      }
    }

    val dockerContainerValidator: Validator[Container] = validator[Container] { container =>
      container.docker is notEmpty
      container.volumes is every(valid(volumeValidator))
    }

    val mesosContainerValidator: Validator[Container] = validator[Container] { container =>
      container.docker is empty
      container.volumes is every(valid(volumeValidator))
    }

    new Validator[Container] {
      override def apply(c: Container): Result = c.`type` match {
        case Mesos.ContainerInfo.Type.MESOS  => validate(c)(mesosContainerValidator)
        case Mesos.ContainerInfo.Type.DOCKER => validate(c)(dockerContainerValidator)
        case _                               => Failure(Set(RuleViolation(c.`type`, "unknown", None)))
      }
    }
  }

}
