package pkg

import indigo.*
import indigo.scenes.*
import indigoextras.geometry.BoundingBox
import indigoextras.geometry.LineSegment
import indigoextras.geometry.Vertex

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

  val paddle    = Rectangle(10, 50)
  val ballStart = Ball(Point(270, 195), 10, Vector2(1, 1), 3)

  def initialModel(startupData: Unit): Outcome[Model] =
    Outcome(
      Model(
        walls = Batch(
          Rectangle(0, 0, 550, 10),
          Rectangle(0, 390, 550, 10)
        ),
        paddles = Batch(
          paddle.withPosition(30, 50),
          paddle.withPosition(510, 50)
        ),
        ball = ballStart
      )
    )

  def updateModel(
      context: FrameContext[Unit],
      model: Model
  ): GlobalEvent => Outcome[Model] =
    case FrameTick =>
      val nextPaddles = model.paddles.map { p =>
        Model.movePaddle(p, context.inputState.mouse.position.y)
      }

      val nextBall = Ball.moveBall(model.ball, model.walls, model.paddles)

      Outcome(model.copy(paddles = nextPaddles, ball = nextBall))

    case _ =>
      Outcome(model)

  def present(
      context: FrameContext[Unit],
      model: Model
  ): Outcome[SceneUpdateFragment] =
    Outcome(
      SceneUpdateFragment(
        (model.walls ++ model.paddles).map { b =>
          Shape.Box(b, Fill.Color(RGBA.White))
        } ++ Batch(
          Shape.Circle(
            model.ball.position,
            model.ball.radius,
            Fill.Color(RGBA.White)
          )
        )
      )
    )

final case class Model(
    walls: Batch[Rectangle],
    paddles: Batch[Rectangle],
    ball: Ball
)

object Model:

  def movePaddle(paddle: Rectangle, mouseY: Int): Rectangle =
    val top    = 10
    val bottom = 400 - 10 - paddle.height

    val maybeY =
      mouseY - paddle.halfSize.height

    val nextY =
      if maybeY <= top then top
      else if maybeY >= bottom then bottom
      else maybeY

    paddle.moveTo(paddle.x, nextY)

final case class Ball(position: Point, radius: Int, force: Vector2, speed: Int):
  def withForce(value: Vector2): Ball = this.copy(force = value)
  def withSpeed(value: Int): Ball     = this.copy(speed = value)

  def moveBy(amount: Point): Ball =
    this.copy(position = position + amount)

object Ball:

  def applyForce(b: Ball, f: Vector2): Ball =
    b.moveBy((Vector2(b.speed) * f).toPoint).withForce(f)

  def moveBall(
      ball: Ball,
      walls: Batch[Rectangle],
      paddles: Batch[Rectangle]
  ): Ball =
    val current     = ball.position
    val ballAdvance = applyForce(ball, ball.force)

    val line =
      LineSegment(
        Vertex.fromPoint(current),
        Vertex.fromPoint(
          ballAdvance.position + (Point(ball.radius) * ball.force.toPoint)
        )
      )

    val wallCollision =
      walls.exists(w => BoundingBox.fromRectangle(w).lineIntersects(line))

    val paddleCollision =
      paddles.exists(p => BoundingBox.fromRectangle(p).lineIntersects(line))

    val nextForce =
      Vector2(
        if paddleCollision then -ball.force.x else ball.force.x,
        if wallCollision then -ball.force.y else ball.force.y
      )

    if wallCollision || paddleCollision then applyForce(ball, nextForce)
    else ballAdvance
