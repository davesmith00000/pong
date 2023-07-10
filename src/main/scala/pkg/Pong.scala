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

  val assets: Set[AssetType]     = Assets.assets
  val fonts: Set[FontInfo]       = Set(Fonts.fontInfo)
  val animations: Set[Animation] = Set()
  val shaders: Set[Shader]       = Set()

  def setup(
      assetCollection: AssetCollection,
      dice: Dice
  ): Outcome[Startup[Unit]] =
    Outcome(Startup.Success(()))

  val paddle            = Rectangle(10, 50)
  val ballStartPosition = Vertex(270, 195)

  def initialModel(startupData: Unit): Outcome[Model] =
    val a = paddle.withPosition(30, 50)
    val b = paddle.withPosition(510, 50)

    Outcome(
      Model(
        scoreA = 0,
        scoreB = 0,
        paddleA = a,
        paddleB = b,
        world = World
          .empty[String]
          .withColliders(
            Collider
              .Box("left goal", BoundingBox(-50, -100, 50, 600))
              .makeStatic,
            Collider
              .Box("right goal", BoundingBox(550, -100, 50, 600))
              .makeStatic,
            Collider.Box("top wall", BoundingBox(10, 10, 530, 10)).makeStatic,
            Collider
              .Box("bottom wall", BoundingBox(10, 380, 530, 10))
              .makeStatic,
            Collider
              .Circle("ball", BoundingCircle(ballStartPosition, 10))
              .withVelocity(100, 100)
              .onCollision {
                case c if c.tag.startsWith("paddle") =>
                  Batch(GameEvent.SpeedUp)

                case c if c.tag == "left goal" =>
                  Batch(GameEvent.ScoreA, GameEvent.ResetBall)

                case c if c.tag == "right goal" =>
                  Batch(GameEvent.ScoreB, GameEvent.ResetBall)

                case _ =>
                  Batch()
              },
            Collider.Box("paddle a", a.toBoundingBox).makeStatic,
            Collider.Box("paddle b", b.toBoundingBox).makeStatic
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

    case GameEvent.SpeedUp =>
      val nextWorld =
        model.world.modifyByTag("ball")(b => b.withVelocity(b.velocity * 1.5))

      Outcome(model.copy(world = nextWorld))

    case GameEvent.ScoreA =>
      Outcome(model.copy(scoreA = model.scoreA + 1))

    case GameEvent.ScoreB =>
      Outcome(model.copy(scoreB = model.scoreB + 1))

    case GameEvent.ResetBall =>
      def giveVec =
        val choose = List(1, -1)
        Vector2(
          choose(context.dice.roll(2) - 1),
          choose(context.dice.roll(2) - 1)
        )

      val nextWorld =
        model.world
          .modifyByTag("ball")(
            _.moveTo(ballStartPosition).withVelocity(giveVec * 100)
          )

      Outcome(model.copy(world = nextWorld))

    case _ =>
      Outcome(model)

  def present(
      context: FrameContext[Unit],
      model: Model
  ): Outcome[SceneUpdateFragment] =
    Outcome(
      SceneUpdateFragment(
        Layer(
          Text(
            s"score\n${model.scoreA.toString()} - ${model.scoreB.toString()}",
            2,
            2,
            5,
            Fonts.fontKey,
            Assets.fontMaterial
          ).alignCenter
            .moveTo(550 / 2, 30)
        ),
        Layer(
          model.world.presentNot(_.tag.contains("goal")) {
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
    )

final case class Model(
    scoreA: Int,
    scoreB: Int,
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

enum GameEvent extends GlobalEvent:
  case SpeedUp, ScoreA, ScoreB, ResetBall
