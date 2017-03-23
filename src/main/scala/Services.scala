package querysidepoc

import akka.actor.ActorSystem
import akka.cluster.sharding.ClusterSharding
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.Try
import scala.concurrent.duration._
import cats.MonadError


trait ServicioProductos[P[_]] {
  def obtenerListaProductos(personaId: String): P[ListaProductos]
}

trait Servicio[P[_]] {
  def obtenerInfo(productoId: String): P[ProductoDTO]
}

trait ConPlanA [P[_]] {
  def planA(productoId: String): P[Option[ProductoDTO]]
}

trait ConPlanB [P[_]] {
  def planB(productoId: String): P[ProductoDTO]
}

trait UpdatePlanA[ P[_] ] {
  def update(  titularAnadido : TitularAnadidoALaCuenta ) : P[String]  
}

trait ServicioModernizado[P[_]] extends Servicio[P] with ConPlanA[P] with ConPlanB[P] {

  implicit val E : MonadError[P, Error]

  override def obtenerInfo(productoId: String) : P[ProductoDTO] = {
    
    import cats.implicits._
    
    for {
      resultA <- planA(productoId)
      result <- resultA match {
        case Some( value ) => E pure ( value )
        case _ => planB(productoId)
      }
    } yield result
  }
}



object ServicioPosicionesGlobales {
  
  import cats.implicits._
  
  def obtenerPosicionGlobal[P[_]]( personaId: String )( implicit SP: ServicioProductos[P], S : Map[TipoProducto, Servicio[P]], E : MonadError[P,Error] )  = {
      
      for {

        listaProductos <- SP.obtenerListaProductos( personaId )

        listaDetalleProducto <- E pure (listaProductos.productos.map { case (id: String, tipoProducto: TipoProducto) =>  S(tipoProducto).obtenerInfo(id) } )

        positionGlobal <- sequence( listaDetalleProducto )

      } yield positionGlobal
    
  }
  
  
  def sequence[P[_],T](list: List[P[T]])(implicit E: MonadError[P,Error]): P[List[T]] = {

    def _sequence[T](from: List[P[T]], result: P[List[T]]): P[List[T]] = {

      from match {

        case h :: t  => {

          for {
            
            x <- h

            list <- _sequence(t, result)

          } yield x +: list

        }

        case e => result

      }
    }

    _sequence(list, E pure ( List[T]() ) )
  }
  
}
