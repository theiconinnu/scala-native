package scala.scalanative.codegen.compat.os

import scala.scalanative.codegen.AbstractCodeGen
import scala.scalanative.nir.ControlFlow.Block
import scala.scalanative.nir._
import scala.scalanative.util.ShowBuilder

private[codegen] class UnixCompat(protected val codegen: AbstractCodeGen)
    extends OsCompat {
  import codegen.{pointerType => ptrT}
  val ehWrapperTy = "@_ZTIN11scalanative16ExceptionWrapperE"
  val excRecTy = s"{ $ptrT, i32 }"
  val beginCatch = "@__cxa_begin_catch"
  val endCatch = "@__cxa_end_catch"
  val catchSig =
    if (useOpaquePointers) s"$ptrT $ehWrapperTy"
    else s"i8* bitcast ({ i8*, i8*, i8* }* $ehWrapperTy to i8*)"
  val landingpad =
    s"landingpad $excRecTy catch $catchSig"
  val typeid =
    s"call i32 @llvm.eh.typeid.for($catchSig)"

  protected val osPersonalityType: String = "@__gxx_personality_v0"

  override def genBlockAlloca(block: Block)(implicit sb: ShowBuilder): Unit =
    ()

  def genLandingPad(
      unwind: Next.Unwind
  )(implicit fresh: Fresh, pos: Position, sb: ShowBuilder): Unit = {
    import sb._
    val Next.Unwind(Val.Local(excname, _), next) = unwind

    val excpad = "_" + excname.id + ".landingpad"
    val excsucc = excpad + ".succ"
    val excfail = excpad + ".fail"

    val exc = "%_" + excname.id
    val rec, r0, r1, id, cmp = "%_" + fresh().id
    val w0, w1, w2 = "%_" + fresh().id

    def line(s: String) = { newline(); str(s) }

    line(s"$excpad:")
    indent()
    line(s"$rec = $landingpad")
    line(s"$r0 = extractvalue $excRecTy $rec, 0")
    line(s"$r1 = extractvalue $excRecTy $rec, 1")
    line(s"$id = $typeid")
    line(s"$cmp = icmp eq i32 $r1, $id")
    line(s"br i1 $cmp, label %$excsucc, label %$excfail")
    unindent()

    line(s"$excsucc:")
    indent()
    line(s"$w0 = call $ptrT $beginCatch($ptrT $r0)")
    if (useOpaquePointers) {
      line(s"$w2 = getelementptr ptr, ptr $w0, i32 1")
      line(s"$exc = load ptr, ptr $w2")
    } else {
      line(s"$w1 = bitcast i8* $w0 to i8**")
      line(s"$w2 = getelementptr i8*, i8** $w1, i32 1")
      line(s"$exc = load i8*, i8** $w2")
    }
    line(s"call void $endCatch()")
    codegen.genInst(Inst.Jump(next))
    unindent()

    line(s"$excfail:")
    indent()
    line(s"resume $excRecTy $rec")
    unindent()
  }

  def genPrelude()(implicit builder: ShowBuilder): Unit = {
    import builder._
    line(s"declare i32 @llvm.eh.typeid.for($ptrT)")
    line(s"declare i32 $osPersonalityType(...)")
    line(s"declare $ptrT $beginCatch($ptrT)")
    line(s"declare void $endCatch()")
    line(s"$ehWrapperTy = external constant { $ptrT, $ptrT, $ptrT }")
  }
}
