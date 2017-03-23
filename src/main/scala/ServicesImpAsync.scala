package querysidepoc

import akka.actor.ActorSystem
import akka.cluster.sharding.ClusterSharding
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.Try
import scala.concurrent.duration._
import cats.MonadError


object ServicesImpAsync {
  
    object Implicits {
      
      import scala.concurrent.duration._
      import akka.stream._
      import FutureEither._
      import scala.concurrent.{ExecutionContext, Future}
      

      implicit val timeout = akka.util.Timeout(10 seconds)
      implicit val system = ActorSystem("query-side-poc")
      implicit val materializer = ActorMaterializer()

      implicit val ec: ExecutionContext = system.dispatcher

      implicit def fromFutEToFut[T](v: FutureEither[T]): Future[Either[Error, T]] = convertToFuture(v)
  
      implicit val E =  MonadErrorUtil.EFutureEither
      
      implicit val servicios: Map[TipoProducto, Servicio[FutureEither]] = Map(
        Cuenta -> new ServicioCuentas(system),
        Hipoteca -> new ServicioHipotecas
      )

      implicit val servicioProductos = new ServicioProductosFutureEither
      
    }
  
  
}



class ServicioProductosFutureEither( implicit ec : ExecutionContext ) extends ServicioProductos[FutureEither] {

  def obtenerListaProductos(personaId: String) : FutureEither[ListaProductos] = {

    val promis = Promise[ ListaProductos ]()

    new Thread {
      () =>
        Thread.sleep( (Math.random() * 1000).toInt)
      promis.complete(Try( ListaProductos(List("CUENTA-1" -> Cuenta, "2" -> Hipoteca))))
    }.run()

    // mock
    FutureEither.successfulWith(promis.future)
  }

}

trait ConActores {
  self: ConPlanA[FutureEither] =>

  val actorSystem: ActorSystem

  implicit val timeout: akka.util.Timeout
  
  implicit val ex : ExecutionContext =  actorSystem.dispatcher

  val nombreRegionSharding: String

  def planA(productoId: String): FutureEither[Option[ProductoDTO]] = {
    val result = (ClusterSharding(actorSystem).shardRegion(nombreRegionSharding) ? ObtenerInfo(productoId))
      .mapTo[ProductoDTO].map { case NoEncontrada => Option.empty[ProductoDTO]
    case q:ProductoDTO => Some(q) }

    FutureEither.successfulWith(result)
  }

}

object MonadErrorUtil {
  
  import ServiciosSync.EitherError
  
  def EEitherError : MonadError[ EitherError, Error ] = new MonadError[EitherError, Error]  {
    
    // Members declared in cats.Applicative
    def pure[A](x: A): EitherError[A] = Right( x )
    
    // Members declared in cats.ApplicativeError
    def handleErrorWith[A](fa: EitherError[A])(f: Error => EitherError[A]): EitherError[A] = fa
    def raiseError[A](e: Error): EitherError[A] = Left( e )
    
    // Members declared in cats.FlatMap
    def flatMap[A, B](fa: EitherError[A])(f: A => EitherError[B]): EitherError[B] = {
      
      fa match {
        case Right( r ) => f( r )
        case Left( l ) => Left( l )
      } 
      
    }
    
    def tailRecM[A, B](a: A)(f: A => EitherError[Either[A,B]]): EitherError[B] = {
      
        f( a ) match {
          case Right(Right(r)) => Right[Error,B](r)
          case Right(Left(l)) => Left( ErrorTailRecM( l ) )
          case Left( l ) => Left( ErrorTailRecM( l ) )
        }
    }
    
  }
  
  def EFutureEither(implicit ec: scala.concurrent.ExecutionContext) : MonadError[FutureEither, Error]  = new MonadError[FutureEither, Error] {
    
    import FutureEither._
    
    // Members declared in cats.Applicative
    def pure[A](x: A): FutureEither[A] = successful( x )
    
    // Members declared in cats.ApplicativeError
    def handleErrorWith[A](fa: FutureEither[A])(f: Error => FutureEither[A]): FutureEither[A] = fa
    def raiseError[A](e: Error): FutureEither[A] = failure( e )
    
    // Members declared in cats.FlatMap
    def flatMap[A, B](fa: FutureEither[A])(f: A => FutureEither[B]): FutureEither[B] = fa flatMap( f(_) )
    def tailRecM[A, B](a: A)(f: A => FutureEither[Either[A,B]]): FutureEither[B] = {
      
        f( a ).flatMap {
          
            _ match {
              case Right( r ) => successful( r )
              case Left( l ) => failure( ErrorTailRecM(l) )
              
            }
          
        }
      
      
      }
    
  }
  
  
 }


class ServicioCuentas (val actorSystem: ActorSystem)(implicit ec: ExecutionContext) extends ServicioModernizado[FutureEither] with ConActores {
  
  implicit val E : MonadError[FutureEither, Error] = MonadErrorUtil.EFutureEither

  override val nombreRegionSharding: String = ActorCuenta.CuentaRegionName

  implicit val timeout: akka.util.Timeout = Timeout(7 seconds)

  override def planB(productoId: String): FutureEither[ProductoDTO] = {

    val promis = Promise[CuentaDTO]()

    new Thread {
      () =>
        Thread.sleep((Math.random() * 1000).toInt)
      promis.complete(Try(CuentaDTO(productoId, titulares = List("A", s"B"))))
    }.run()

    // mock
    FutureEither.successfulWith(promis.future)
  }

}


class ServicioHipotecas( implicit ex : ExecutionContext ) extends Servicio[FutureEither] {
  
  override def obtenerInfo(productoId: String) : FutureEither[ProductoDTO] =
  {
    val promis = Promise[HipotecaDTO]()

    new Thread {
      () =>
        Thread.sleep((Math.random() * 1000).toInt)
      promis.complete(Try(HipotecaDTO(productoId, total = 250000, restante = 125000, intereses = 5)))
    }.run()

    // mock
    FutureEither.successfulWith(promis.future)
  }
}

class ServicioPosicionesGlobalesFutureEither(servicioProductos: ServicioProductosFutureEither, servicios: Map[TipoProducto, Servicio[FutureEither]])
                                (implicit val ec: scala.concurrent.ExecutionContext){

  def obtenerPosicionGlobal(personaId: String) : FutureEither[List[ProductoDTO]] = {

    import FutureEither._

    for {

      listaProductos <- servicioProductos.obtenerListaProductos( personaId )

      listaDetalleProducto <- successful(listaProductos.productos.map { case (id: String, tipoProducto: TipoProducto) =>  servicios(tipoProducto).obtenerInfo(id) } )

      positionGlobal <- sequence(listaDetalleProducto)

    } yield positionGlobal

  }

}
