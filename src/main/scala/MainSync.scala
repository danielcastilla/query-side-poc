package querysidepoc


import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.event.{Logging, LoggingReceive}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.persistence.PersistentActor
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source, Zip}
import akka.util.Timeout
import org.json4s.{DefaultFormats, jackson}

import scala.concurrent.duration._
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.{implicitConversions, postfixOps}
import scala.util.Try
import ServicioProductosEither.EitherError
import FutureEither.convertToFuture


object MainSync extends App {

  import scala.concurrent.duration._
  import com.hazelcast.core.Hazelcast
  import com.hazelcast.config.Config;
  import cats.MonadError
  

  implicit val timeout = akka.util.Timeout(10 seconds)
  implicit val system = ActorSystem("query-side-poc")
  implicit val materializer = ActorMaterializer()

  implicit val hzlInstance = Hazelcast.newHazelcastInstance( new Config )

  val servicioCuentas = new ServicioCuentasSync
  val servicioHipotecas = new ServicioHipotecasSync

  val actorRefSource = Source.actorRef[TitularAnadidoALaCuenta](100, OverflowStrategy.fail)

  import de.heikoseeberger.akkahttpjson4s.Json4sSupport._

  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats


  val logging = Logging(system, classOf[Main])

  
  import akka.stream.scaladsl.GraphDSL.Implicits._

  val flow =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>

      val Enriquecer = builder.add(Flow[TitularAnadidoALaCuenta].map(item => item.copy(cuentaId = "CUENTA-" + item.cuentaId)))

      val AlaCuenta = builder.add(Flow[TitularAnadidoALaCuenta].map(event => servicioCuentas.update( event ) ) )

      val Comprobar = builder.add(Flow[EitherError[String]].map(s => {
          logging.info("received " + s )
          s match {
              case Right("Success") => "success"
              case _ => "failure"
          
            }
        }
      ))

       Enriquecer ~> AlaCuenta ~> Comprobar

       FlowShape(Enriquecer.in, Comprobar.out)

    })

  val sourceActorRef = flow.runWith(actorRefSource, Sink.foreach(msg => logging.info("acknowledge")))._1

  import ServicioPosicionesGlobales.obtenerPosicionGlobal
  
  implicit val EEitherError : MonadError[EitherError,Error] = MonadErrorUtil.EEitherError
  
  implicit val serviciosSync: Map[TipoProducto, Servicio[EitherError]] = Map(
    Cuenta -> servicioCuentas,
    Hipoteca -> servicioHipotecas
  )

  implicit val servicioProductos = new ServicioProductosSync
  

  val route = post {
    path("cuenta" / Segment / "titular" / Segment) {
      (cuentaId: String, titularId: String) => {
        val event = TitularAnadidoALaCuenta(cuentaId, titularId)
        sourceActorRef ! event
        complete(OK)
      }
    }
  } ~ get {
    path("posicion" / Segment) {
      personaId: String => {
        obtenerPosicionGlobal[EitherError]( personaId ) match {
          case Left(error) => complete( InternalServerError -> error.description )
          case Right(value ) => complete( OK -> value )
        }
        
      }
    }
  }

  Http().bindAndHandle(route, "localhost", 8080)

}



/*
Account              |  New Backend ... but can fallback
Hipoteca             |  Legacy Backend (REST API call)
*/


