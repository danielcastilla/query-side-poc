package querysidepoc

import scala.util.{Right,Either}
import com.hazelcast.core.HazelcastInstance

object ServicioProductosEither {

    type EitherError[T] = Either[Error,T]
  
}

import ServicioProductosEither.EitherError

trait ConHazelCast {
  
  self: ConPlanA[EitherError] =>

  implicit val instance : HazelcastInstance
  
  val nameMap  : String
  
  def getMap : java.util.Map[String, ProductoDTO] = instance.getMap( nameMap )

  def planA(productoId: String): EitherError[Option[ProductoDTO]] = {
    
      val result = instance.getMap[String, ProductoDTO]( nameMap ).get( productoId )
      
      val res: Option[ProductoDTO] = if ( result != null ) {
          Option( result )
        } else {
          Option.empty
        }
        
      Right( res );
    
  }

}


class ServicioProductosSync[A] extends ServicioProductos[EitherError] {

  def obtenerListaProductos(personaId: String) : EitherError[ListaProductos] = {
    
    Thread.sleep( (Math.random() * 1000).toInt)
    
    Right( ListaProductos(List("CUENTA-1" -> Cuenta, "2" -> Hipoteca)) )

  }

}

class ServicioCuentasSync( implicit inst : HazelcastInstance ) extends ServicioModernizado[EitherError] with ConHazelCast with UpdatePlanA[ EitherError ]{
  
  import cats.MonadError
  
  implicit val instance = inst
  
  val nameMap = "cuentas"
  
  implicit val E : MonadError[EitherError, Error] = MonadErrorUtil.EEitherError
  
  override def update(  titularAnadido : TitularAnadidoALaCuenta ) : EitherError[String] = {
    
    titularAnadido match {
      
      case TitularAnadidoALaCuenta(cuentaId, titularId) => 
        
        val cuenta : CuentaDTO = if ( getMap.containsKey( cuentaId ) ) {
          
          getMap.get( cuentaId ).asInstanceOf[CuentaDTO]
          
        } else {
          
          CuentaDTO( cuentaId, List[String]() )
          
        }
        
        val cuentaActualizada =  cuenta.copy( titulares = cuenta.titulares :+ titularId ) 
        
        getMap.put( cuentaId, cuentaActualizada )
      
    }
    
    Right( "Success" )
    
  }
  
  override def planB(productoId: String): EitherError[ProductoDTO] = {
    
    Thread.sleep( (Math.random() * 1000).toInt)

    Right(CuentaDTO(productoId, titulares = List("A", s"B") ) )
  }

}

class ServicioHipotecasSync extends Servicio[EitherError] {
  
  override def obtenerInfo(productoId: String) : EitherError[ProductoDTO] =
  {
    Thread.sleep((Math.random() * 1000).toInt)
    Right(HipotecaDTO(productoId, total = 250000, restante = 125000, intereses = 5))
  }
}


