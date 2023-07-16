package pkg

import indigo.*
import indigo.physics.*
import indigo.scenes.*
import indigo.syntax.*

import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("IndigoGame")
object Pong extends IndigoSandbox[Size, Model]:

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
  ): Outcome[Startup[Size]] =
    Outcome(Startup.Success(config.viewport.size))

  def initialModel(viewportSize: Size): Outcome[Model] =
    Outcome(Model.initial(viewportSize))

  def updateModel(
      context: FrameContext[Size],
      model: Model
  ): GlobalEvent => Outcome[Model] =
    case MouseEvent.Click(_) if model.state.onStartScreen =>
      Outcome(model.copy(state = GameState.Game))

    case FrameTick if model.state.onGameScreen =>
      val nextPaddleA =
        Model.movePaddle(model.paddleA, context.inputState.mouse.position.y)
      val nextPaddleB =
        Model.movePaddle(model.paddleB, context.inputState.mouse.position.y)

      model.world
        .update(context.delta)(
          Model.paddleColliderA.moveTo(model.paddleA.position.toVertex),
          Model.paddleColliderB.moveTo(model.paddleB.position.toVertex)
        )
        .map { updatedWorld =>
          model.copy(
            paddleA = nextPaddleA,
            paddleB = nextPaddleB,
            world = updatedWorld
          )
        }

    case GameEvent.SpeedUp =>
      val nextWorld =
        model.world.modifyByTag(Tags.Ball)(b =>
          b.withVelocity(b.velocity * Model.velocityMultiplier)
        )

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
          .modifyByTag(Tags.Ball)(
            _.moveTo(model.ballStartPosition).withVelocity(
              giveVec * Model.velocityAfterReset
            )
          )

      Outcome(model.copy(world = nextWorld))

    case _ =>
      Outcome(model)

  def present(
      context: FrameContext[Size],
      model: Model
  ): Outcome[SceneUpdateFragment] =
    model.state match
      case GameState.Start =>
        Outcome(
          SceneUpdateFragment(
            Text(
              "Infinite Pong!",
              2,
              2,
              5,
              Fonts.fontKey,
              Assets.fontMaterial
            ).alignCenter
              .moveTo(context.startUpData.width / 2, 30),
            Text(
              "Move your mouse up\nand down to move\nthe paddles.",
              2,
              2,
              5,
              Fonts.fontKey,
              Assets.fontMaterial
            ).alignCenter
              .moveTo(context.startUpData.width / 2, 90),
            Text(
              "click to start!",
              2,
              2,
              5,
              Fonts.fontKey,
              Assets.fontMaterial
                .withAlpha(
                  Signal
                    .Pulse(1.second)
                    .map(p => if p then 1.0 else 0.0)
                    .at(context.running)
                )
            ).alignCenter
              .moveTo(
                context.startUpData.width / 2,
                context.startUpData.height - 60
              )
          )
        )

      case GameState.Game =>
        Outcome(
          SceneUpdateFragment(
            Layer(
              Text(
                s"score\n${model.scoreA.toString()} - ${model.scoreB.toString()}",
                2,
                2,
                5,
                Fonts.fontKey,
                Assets.fontMaterial.withAlpha(0.5)
              ).alignCenter
                .moveTo(context.startUpData.width / 2, 30),
              Shape.Box(
                model.paddleA,
                Fill.Color(RGBA.White)
              ),
              Shape.Box(
                model.paddleB,
                Fill.Color(RGBA.White)
              )
            ),
            Layer(
              model.world.presentNot(c =>
                c.tag == Tags.LeftGoal || c.tag == Tags.RightGoal
              ) {
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
    state: GameState,
    ballStartPosition: Vertex,
    scoreA: Int,
    scoreB: Int,
    paddleA: Rectangle,
    paddleB: Rectangle,
    world: World[Tags]
)

object Model:

  val velocityAfterReset         = Vector2(100, 100)
  val velocityMultiplier: Double = 1.5

  val paddle = Rectangle(10, 50)
  val paddleColliderA =
    Collider.Box(Tags.PaddleA, BoundingBox.fromRectangle(paddle)).makeStatic
  val paddleColliderB =
    Collider.Box(Tags.PaddleB, BoundingBox.fromRectangle(paddle)).makeStatic

  def movePaddle(paddle: Rectangle, mouseY: Int): Rectangle =
    val top    = 20
    val bottom = 400 - 20 - paddle.height
    val maybeY = mouseY - paddle.halfSize.height

    val nextY =
      if maybeY <= top then top
      else if maybeY >= bottom then bottom
      else maybeY

    paddle.moveTo(paddle.x, nextY)

  def initial(viewportSize: Size): Model =
    val gap = 30
    val a   = paddle.withPosition(gap, viewportSize.height / 2)
    val b = paddle.withPosition(
      viewportSize.width - gap - paddle.width,
      viewportSize.height / 2
    )
    val start = (viewportSize / 2).toVertex

    Model(
      state = GameState.Start,
      ballStartPosition = start,
      scoreA = 0,
      scoreB = 0,
      paddleA = a,
      paddleB = b,
      world = World
        .empty[Tags]
        .withColliders(
          Collider
            .Box(
              Tags.LeftGoal,
              BoundingBox(-50, -100, 50, viewportSize.height + 200)
            )
            .makeStatic,
          Collider
            .Box(
              Tags.RightGoal,
              BoundingBox(
                viewportSize.width + 50,
                -100,
                50,
                viewportSize.height + 200
              )
            )
            .makeStatic,
          Collider
            .Box(Tags.Wall, BoundingBox(10, 10, viewportSize.width - 20, 10))
            .makeStatic,
          Collider
            .Box(
              Tags.Wall,
              BoundingBox(
                10,
                viewportSize.height - 20,
                viewportSize.width - 20,
                10
              )
            )
            .makeStatic,
          Collider
            .Circle(Tags.Ball, BoundingCircle(start, 10))
            .withVelocity(velocityAfterReset)
            .onCollision {
              case c if c.tag == Tags.PaddleA || c.tag == Tags.PaddleB =>
                Batch(GameEvent.SpeedUp)

              case c if c.tag == Tags.LeftGoal =>
                Batch(GameEvent.ScoreB, GameEvent.ResetBall)

              case c if c.tag == Tags.RightGoal =>
                Batch(GameEvent.ScoreA, GameEvent.ResetBall)

              case _ =>
                Batch()
            }
        )
    )

enum GameState:
  case Start, Game

  def onStartScreen: Boolean =
    this match
      case Start => true
      case Game  => false

  def onGameScreen: Boolean =
    this match
      case Start => false
      case Game  => true

enum GameEvent extends GlobalEvent:
  case SpeedUp, ScoreA, ScoreB, ResetBall

enum Tags:
  case Ball, PaddleA, PaddleB, Wall, LeftGoal, RightGoal
