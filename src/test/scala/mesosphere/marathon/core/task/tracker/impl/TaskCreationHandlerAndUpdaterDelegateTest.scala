package mesosphere.marathon.core.task.tracker.impl

import akka.actor.Status
import akka.testkit.TestProbe
import mesosphere.marathon.Protos.MarathonTask
import mesosphere.marathon.core.base.ConstantClock
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.state.PathId
import mesosphere.marathon.test.{ MarathonActorSupport, Mockito }
import mesosphere.marathon.{ MarathonSpec, MarathonTestHelper }
import org.apache.mesos.Protos.{ TaskID, TaskStatus }
import org.scalatest.{ Matchers, GivenWhenThen }
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration.Deadline

class TaskCreationHandlerAndUpdaterDelegateTest
    extends MarathonActorSupport with MarathonSpec with Mockito with GivenWhenThen with ScalaFutures with Matchers {

  test("Created succeeds") {
    val f = new Fixture
    val appId: PathId = PathId("/test")
    val task = MarathonTestHelper.mininimalTask(appId)
    val marathonTask = task.marathonTask

    When("created is called")
    val create = f.delegate.created(appId, marathonTask)

    Then("an update operation is requested")
    f.taskTrackerProbe.expectMsg(
      TaskTrackerActor.ForwardTaskOp(f.timeoutFromNow, appId, task.taskId, TaskOpProcessor.Action.Update(task))
    )

    When("the request is acknowledged")
    f.taskTrackerProbe.reply(())
    Then("The reply is the value of task")
    create.futureValue should be(marathonTask)
  }

  test("Created fails") {
    val f = new Fixture
    val appId: PathId = PathId("/test")
    val taskState = MarathonTestHelper.mininimalTask(appId)
    val task: MarathonTask = taskState.marathonTask

    When("created is called")
    val create = f.delegate.created(appId, task)

    Then("an update operation is requested")
    f.taskTrackerProbe.expectMsg(
      TaskTrackerActor.ForwardTaskOp(f.timeoutFromNow, appId, taskState.taskId, TaskOpProcessor.Action.Update(taskState))
    )

    When("the response is an error")
    val cause: RuntimeException = new scala.RuntimeException("test failure")
    f.taskTrackerProbe.reply(Status.Failure(cause))
    Then("The reply is the value of task")
    create.failed.futureValue.getMessage should include(appId.toString)
    create.failed.futureValue.getMessage should include(task.getId)
    create.failed.futureValue.getMessage should include("Update")
    create.failed.futureValue.getCause should be(cause)
  }

  test("Terminated succeeds") {
    val f = new Fixture
    val appId: PathId = PathId("/test")
    val taskState = MarathonTestHelper.mininimalTask(appId)
    val task: MarathonTask = taskState.marathonTask

    When("created is called")
    val create = f.delegate.terminated(appId, task.getId)

    Then("an expunge operation is requested")
    f.taskTrackerProbe.expectMsg(
      TaskTrackerActor.ForwardTaskOp(f.timeoutFromNow, appId, taskState.taskId, TaskOpProcessor.Action.Expunge)
    )

    When("the request is acknowledged")
    f.taskTrackerProbe.reply(())
    Then("The reply is the value of the future")
    create.futureValue should be(())
  }

  test("Terminated fails") {
    val f = new Fixture
    val appId: PathId = PathId("/test")
    val taskState = MarathonTestHelper.mininimalTask(appId)
    val task: MarathonTask = taskState.marathonTask

    When("created is called")
    val create = f.delegate.terminated(appId, task.getId)

    Then("an expunge operation is requested")
    f.taskTrackerProbe.expectMsg(
      TaskTrackerActor.ForwardTaskOp(f.timeoutFromNow, appId, taskState.taskId, TaskOpProcessor.Action.Expunge)
    )

    When("the response is an error")
    val cause: RuntimeException = new scala.RuntimeException("test failure")
    f.taskTrackerProbe.reply(Status.Failure(cause))
    Then("The reply is the value of task")
    create.failed.futureValue.getMessage should include(appId.toString)
    create.failed.futureValue.getMessage should include(task.getId)
    create.failed.futureValue.getMessage should include("Expunge")
    create.failed.futureValue.getCause should be(cause)
  }

  test("StatusUpdate succeeds") {
    val f = new Fixture
    val appId: PathId = PathId("/test")
    val taskId = "task1"

    val update = TaskStatus.newBuilder().setTaskId(TaskID.newBuilder().setValue(taskId)).buildPartial()

    When("created is called")
    val create = f.delegate.statusUpdate(appId, update)

    Then("an expunge operation is requested")
    f.taskTrackerProbe.expectMsg(
      TaskTrackerActor.ForwardTaskOp(f.timeoutFromNow, appId, Task.Id(taskId), TaskOpProcessor.Action.UpdateStatus(update))
    )

    When("the request is acknowledged")
    f.taskTrackerProbe.reply(())
    Then("The reply is the value of the future")
    create.futureValue should be(())
  }

  test("StatusUpdate fails") {
    val f = new Fixture
    val appId: PathId = PathId("/test")
    val taskId = "task1"

    val update = TaskStatus.newBuilder().setTaskId(TaskID.newBuilder().setValue(taskId)).buildPartial()

    When("created is called")
    val create = f.delegate.statusUpdate(appId, update)

    Then("an expunge operation is requested")
    f.taskTrackerProbe.expectMsg(
      TaskTrackerActor.ForwardTaskOp(f.timeoutFromNow, appId, Task.Id(taskId), TaskOpProcessor.Action.UpdateStatus(update))
    )

    When("the response is an error")
    val cause: RuntimeException = new scala.RuntimeException("test failure")
    f.taskTrackerProbe.reply(Status.Failure(cause))
    Then("The reply is the value of task")
    create.failed.futureValue.getMessage should include(appId.toString)
    create.failed.futureValue.getMessage should include(taskId)
    create.failed.futureValue.getMessage should include("UpdateStatus")
    create.failed.futureValue.getCause should be(cause)
  }

  class Fixture {
    lazy val clock = ConstantClock()
    lazy val config = MarathonTestHelper.defaultConfig()
    lazy val taskTrackerProbe = TestProbe()
    lazy val delegate = new TaskCreationHandlerAndUpdaterDelegate(clock, config, taskTrackerProbe.ref)
    lazy val timeoutDuration = delegate.timeout.duration
    def timeoutFromNow = clock.now() + timeoutDuration
  }
}
