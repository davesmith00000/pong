package pkg

import indigo.*
import indigo.physics.*
import indigo.scenes.*

import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("IndigoGame")
object Pong extends IndigoSandbox[Unit, Model]:

  val config: GameConfig =
    GameConfig.default
      .withViewport(550, 400)

  val assets: Set[AssetType]     = Set()
  val fonts: Set[FontInfo]       = Set()
  val animations: Set[Animation] = Set()
  val shaders: Set[Shader]       = Set()

  def setup(
      assetCollection: AssetCollection,
      dice: Dice
  ): Outcome[Startup[Unit]] =
    Outcome(Startup.Success(()))

  val paddle = Rectangle(10, 50)

  def initialModel(startupData: Unit): Outcome[Model] =
    val a = paddle.withPosition(30, 50)
    val b = paddle.withPosition(510, 50)

    Outcome(
      Model(
        paddleA = a,
        paddleB = b,
        world = World
          .empty[String]
          .withColliders(
            Collider.Box("top wall", BoundingBox(10, 10, 530, 10)).makeStatic,
            Collider
              .Box("bottom wall", BoundingBox(10, 380, 530, 10))
              .makeStatic,
            Collider
              .Circle("ball", BoundingCircle(270, 195, 10))
              .withVelocity(100, 100)
              .onCollision {
                case c if c.tag.startsWith("paddle") => Batch(SpeedUp)
                case _                               => Batch()
              },
            Collider.Box("paddle a", a.toBoundingBox),
            Collider.Box("paddle b", b.toBoundingBox)
          )
      )
    )

  def updateModel(
      context: FrameContext[Unit],
      model: Model
  ): GlobalEvent => Outcome[Model] =
    case FrameTick =>
      val nextPaddleA =
        Model.movePaddle(model.paddleA, context.inputState.mouse.position.y)
      val nextPaddleB =
        Model.movePaddle(model.paddleB, context.inputState.mouse.position.y)

      model.world
        .modifyByTag("paddle a")(_.moveTo(model.paddleA.position.toVertex))
        .modifyByTag("paddle b")(_.moveTo(model.paddleB.position.toVertex))
        .update(context.delta)
        .map { updatedWorld =>
          model.copy(
            paddleA = nextPaddleA,
            paddleB = nextPaddleB,
            world = updatedWorld
          )
        }

    case SpeedUp =>
      Outcome(
        model.copy(world =
          model.world.modifyByTag("ball")(b =>
            b.withVelocity(b.velocity * 1.5)
          )
        )
      )

    case _ =>
      Outcome(model)

  def present(
      context: FrameContext[Unit],
      model: Model
  ): Outcome[SceneUpdateFragment] =
    Outcome(
      SceneUpdateFragment(
        model.world.present {
          case Collider.Circle(_, bounds, _, _, _, _, _, _, _) =>
            Shape.Circle(
              bounds.position.toPoint,
              bounds.radius.toInt,
              Fill.Color(RGBA.White)
            )

          case Collider.Box(_, bounds, _, _, _, _, _, _, _) =>
            Shape.Box(
              bounds.toRectangle,
              Fill.Color(RGBA.White)
            )
        }
      )
    )

final case class Model(
    paddleA: Rectangle,
    paddleB: Rectangle,
    world: World[String]
)

object Model:

  def movePaddle(paddle: Rectangle, mouseY: Int): Rectangle =
    val top    = 20
    val bottom = 400 - 20 - paddle.height
    val maybeY = mouseY - paddle.halfSize.height

    val nextY =
      if maybeY <= top then top
      else if maybeY >= bottom then bottom
      else maybeY

    paddle.moveTo(paddle.x, nextY)

case object SpeedUp extends GlobalEvent
