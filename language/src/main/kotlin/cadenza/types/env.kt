package cadenza.types

// traditional functional programmer environment: the singly linked list map
typealias Env<T> = ConsEnv<T>?
data class ConsEnv<T>(val name: Name, val value: T, val next: Env<T>)
inline val NilEnv: Env<*> get() = null

@Throws(TypeError::class)
fun <T> Env<T>.lookup(name: String): T {
  var current: Env<T> = this
  while (current != null) {
    if (name == current.name) return current.value
    current = current.next
  }
  throw TypeError("unknown variable")
}
