package cadenza.jit

import cadenza.Language
import cadenza.data.*
import cadenza.semantics.ConsEnv
import cadenza.semantics.Ctx
import cadenza.semantics.NameInfo
import cadenza.semantics.Type
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.*
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.*
import java.io.Serializable

@TypeSystemReference(DataTypes::class)
@ReportPolymorphism
abstract class Builtin(@Suppress("unused") open val type: Type, val arity: Int) : Node(), Serializable {
  @Throws(NeutralException::class)
  abstract fun run(frame: VirtualFrame, args: Array<Any?>): Any?
  @Throws(NeutralException::class)
  open fun runUnit(frame: VirtualFrame, args: Array<Any?>) { run(frame, args) }
  @Throws(NeutralException::class)
  open fun runClosure(frame: VirtualFrame, args: Array<Any?>): Closure = DataTypesGen.expectClosure(run(frame, args))
  @Throws(NeutralException::class)
  open fun runBoolean(frame: VirtualFrame, args: Array<Any?>): Boolean = DataTypesGen.expectBoolean(run(frame, args))
  @Throws(NeutralException::class)
  open fun runInteger(frame: VirtualFrame, args: Array<Any?>): Int = DataTypesGen.expectInteger(run(frame, args))
}


abstract class Builtin2(type: Type) : Builtin(type, 2) {
  @Throws(NeutralException::class)
  abstract fun execute(left: Any?, right: Any?): Any?
  @Throws(NeutralException::class)
  open fun executeBoolean(left: Any?, right: Any?): Boolean = DataTypesGen.expectBoolean(execute(left, right))
  @Throws(NeutralException::class)
  open fun executeClosure(left: Any?, right: Any?): Closure = DataTypesGen.expectClosure(execute(left, right))
  @Throws(NeutralException::class)
  open fun executeInteger(left: Any?, right: Any?): Int = DataTypesGen.expectInteger(execute(left, right))

  @Throws(NeutralException::class)
  override fun run(frame: VirtualFrame, args: Array<Any?>): Any? { return execute(args[0], args[1]) }
  @Throws(NeutralException::class)
  override fun runUnit(frame: VirtualFrame, args: Array<Any?>) { execute(args[0], args[1]) }
  @Throws(NeutralException::class)
  override fun runBoolean(frame: VirtualFrame, args: Array<Any?>): Boolean { return executeBoolean(args[0], args[1]) }
  @Throws(NeutralException::class)
  override fun runClosure(frame: VirtualFrame, args: Array<Any?>): Closure { return executeClosure(args[0], args[1]) }
  @Throws(NeutralException::class)
  override fun runInteger(frame: VirtualFrame, args: Array<Any?>): Int { return executeInteger(args[0], args[1]) }
}

abstract class Builtin1(type: Type) : Builtin(type, 1) {
  @Throws(NeutralException::class)
  abstract fun execute(x: Any?): Any?
  @Throws(NeutralException::class)
  open fun executeBoolean(x: Any?): Boolean = DataTypesGen.expectBoolean(execute(x))
  @Throws(NeutralException::class)
  open fun executeClosure(x: Any?): Closure = DataTypesGen.expectClosure(execute(x))
  @Throws(NeutralException::class)
  open fun executeInteger(x: Any?): Int = DataTypesGen.expectInteger(execute(x))

  @Throws(NeutralException::class)
  override fun run(frame: VirtualFrame, args: Array<Any?>): Any? { return execute(args[0]) }
  @Throws(NeutralException::class)
  override fun runUnit(frame: VirtualFrame, args: Array<Any?>) { execute(args[0]) }
  @Throws(NeutralException::class)
  override fun runBoolean(frame: VirtualFrame, args: Array<Any?>): Boolean { return executeBoolean(args[0]) }
  @Throws(NeutralException::class)
  override fun runClosure(frame: VirtualFrame, args: Array<Any?>): Closure { return executeClosure(args[0]) }
  @Throws(NeutralException::class)
  override fun runInteger(frame: VirtualFrame, args: Array<Any?>): Int { return executeInteger(args[0]) }
}

abstract class Le : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Bool))) {
  @Specialization
  internal fun leInt(left: Int, right: Int): Boolean = left <= right
}

abstract class Eq : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Bool))) {
  @Specialization
  internal fun eqInt(left: Int, right: Int): Boolean = left == right
}


abstract class Plus : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Nat))) {
  @Specialization(rewriteOn = [ArithmeticException::class])
  internal fun addInt(left: Int, right: Int): Int = Math.addExact(left, right)
  @Specialization
  internal fun addBigInt(left: BigInt, right: BigInt): BigInt = BigInt(left.value.add(right.value))
}

abstract class Mod : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Nat))) {
  @Specialization
  internal fun modInt(x: Int, y: Int): Int = x.rem(y)
}

abstract class Div : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Nat))) {
  @Specialization
  internal fun divInt(x: Int, y: Int): Int = x.div(y)
}

abstract class Mult : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Nat))) {
  @Specialization
  internal fun multInt(x: Int, y: Int): Int = x * y
}


// TODO: check result positive
abstract class Minus : Builtin2(Type.Arr(Type.Nat,Type.Arr(Type.Nat, Type.Nat))) {
  @Specialization(rewriteOn = [ArithmeticException::class])
  internal fun subInt(left: Int, right: Int): Int {
    return Math.subtractExact(left, right)
  }

  @Specialization
  internal fun subBigInt(left: BigInt, right: BigInt): BigInt {
    return BigInt(left.value.subtract(right.value))
  }
}

val natF = Type.Arr(Type.Nat, Type.Nat)
val natFF = Type.Arr(natF, natF)

//// fixNatF f x = f (fixNatF f) x
//abstract class FixNatF : Builtin2(Type.Arr(natFF, natF)) {
//  @CompilerDirectives.CompilationFinal var target: RootCallTarget? = null
//
//  @Child var dispatch: Dispatch = DispatchNodeGen.create(2, true)
//  @Specialization(guards = ["f.equals(cachedF)"], limit = "100000")
//  fun fixNatF(f: Closure, x: Any?, @Cached("f") cachedF: Closure, @Cached("mkSelfApp(cachedF)") selfApp: Closure): Any? {
//    return dispatch.executeDispatch(cachedF, arrayOf(selfApp, x))
//  }
//
//  fun mkSelfApp(f: Closure): Closure {
//    if (target == null) {
//      CompilerDirectives.transferToInterpreterAndInvalidate()
//      val language = lookupLanguageReference(Language::class.java).get()
//      target = Truffle.getRuntime().createCallTarget(BuiltinRootNode(language, this))
//    }
//    return Closure(null, arrayOf(f), 1, type, target as RootCallTarget)
//  }
//}

// fixNatF f x = f (fixNatF f) x
// fix f = let r = f r in r
abstract class FixNatF : Builtin(Type.Arr(natFF, natF), 2) {
  abstract fun execute(frame: VirtualFrame, l: Any?, r: Any?): Any?

  override fun run(frame: VirtualFrame, args: Array<Any?>) = execute(frame, args[0], args[1])

  @Specialization(guards = ["f.equals(cachedF)"], limit = "100000")
  fun fixNatF(frame: VirtualFrame, f: Closure, right: Any?,
              @CachedLanguage language: Language,
              @Cached("f") cachedF: Closure,
              @Cached("mkFix(f, language)") fix: FixNatF1
              ): Any? {
    return fix.run(frame, arrayOf(right))
  }
  fun mkFix(f: Closure, language: Language) = FixNatF1(f, language)
}

// fixNatF with a known function
// inlines possibly up to graal.TruffleMaximumRecursiveInlining (default 2)
class FixNatF1(private val f: Closure, language: Language) : Builtin(natF, 1) {
  // TODO: make this instrumentable?
  private val target: RootCallTarget = Truffle.getRuntime().createCallTarget(BuiltinRootNode(language, this))
  private val self = Closure(null, arrayOf(), 1, type, target)
  // TODO: making this a tail call breaks the specialization we get by using FixNatF1
  // TODO: this is wrong when using CallBuiltin
  @Child var dispatch: Dispatch = DispatchNodeGen.create(2, true)

  override fun run(frame: VirtualFrame, xs: Array<Any?>):  Any? {
    val x = xs[0]
    // still need to use a dispatch since calling w/ multiple args => might need to call twice
    // but actually it's impossible to branch on values of function types, so this is avoidable?
    return dispatch.executeDispatch(frame, f, arrayOf(self, x))
  }
}


class PrintId : Builtin1(natF) {
  override fun execute(x: Any?): Any? {
    println(x)
    return x
  }
}

val initialCtx: Ctx = arrayOf(
  Pair("le", LeNodeGen.create()),
  Pair("fixNatF", FixNatFNodeGen.create()),
  Pair("plus", PlusNodeGen.create()),
  Pair("minus", MinusNodeGen.create()),
  Pair("eq",EqNodeGen.create()),
  Pair("mod",ModNodeGen.create()),
  Pair("div",DivNodeGen.create()),
  Pair("mult",MultNodeGen.create()),
  Pair("printId",PrintId())
).fold(null as Ctx) { c, x ->
  ConsEnv(x.first, NameInfo(x.second.type, x.second), c)
}

