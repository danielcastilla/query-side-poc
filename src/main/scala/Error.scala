package querysidepoc

sealed trait Error {
  val description: String
}

case class CuentaNoEncontrada(cuentaId: String) extends Error {
  override val description = "Cuenta con id " + cuentaId + " no encontrada"
}
case class PersonaNoEncontrada(personaId: String) extends Error {
  override val description = "Persona con id " + personaId + " no encontrada"

}
case class ErrorGenerico(e: Throwable) extends Error {
  override val description = "Error generico " + e.getMessage
}

case class ErrorTailRecM[A](a : A) extends Error {
  override val description = "Error tail RecM : " + a
}
