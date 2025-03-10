package scala.scalanative

import scalanative.annotation.alwaysinline
import scalanative.unsafe._
import scalanative.unsigned.USize
import scalanative.runtime.Intrinsics._
import scalanative.runtime.monitor._
import scala.scalanative.meta.LinktimeInfo.isMultithreadingEnabled

package object runtime {

  /** Used as a stub right hand of intrinsified methods. */
  def intrinsic: Nothing = throwUndefined()

  /** Enter monitor of given object. */
  @alwaysinline def enterMonitor(obj: Object): Unit =
    if (isMultithreadingEnabled) {
      getMonitor(obj).enter(obj)
    }

  /** Enter monitor of given object. */

  @alwaysinline def exitMonitor(obj: Object): Unit =
    if (isMultithreadingEnabled) {
      getMonitor(obj).exit(obj)
    }

  /** Get monitor for given object. */
  @alwaysinline def getMonitor(obj: Object) = {
    if (isMultithreadingEnabled)
      new BasicMonitor(
        elemRawPtr(
          castObjectToRawPtr(obj),
          castIntToRawSize(MemoryLayout.Object.LockWordOffset)
        )
      )
    else
      throw new IllegalStateException(
        "Monitors unavilable in single threaded mode"
      )
  }

  /** Initialize runtime with given arguments and return the rest as Java-style
   *  array.
   */
  def init(argc: Int, rawargv: RawPtr): scala.Array[String] = {
    if (isMultithreadingEnabled) {
      assert(
        Thread.currentThread() != null,
        "failed to initialize main thread"
      )
    }

    val argv = fromRawPtr[CString](rawargv)
    val args = new scala.Array[String](argc - 1)

    // skip the executable name in argv(0)
    var c = 0
    while (c < argc - 1) {
      // use the default Charset (UTF_8 atm)
      args(c) = fromCString(argv(c + 1))
      c += 1
    }

    args
  }

  @alwaysinline def fromRawPtr[T](rawptr: RawPtr): Ptr[T] =
    Boxes.boxToPtr(rawptr)

  @alwaysinline def toRawPtr[T](ptr: Ptr[T]): RawPtr =
    Boxes.unboxToPtr(ptr)

  @alwaysinline def fromRawSize[T](rawSize: RawSize): Size =
    Boxes.boxToSize(rawSize)

  @alwaysinline def fromRawUSize[T](rawSize: RawSize): USize =
    Boxes.boxToUSize(rawSize)

  @alwaysinline def toRawSize(size: Size): RawSize =
    Boxes.unboxToSize(size)

  @alwaysinline def toRawSize(size: USize): RawSize =
    Boxes.unboxToUSize(size)

  @alwaysinline def sizeOfPtr = Intrinsics.sizeOf[Ptr[_]]

  /** Run the runtime's event loop. The method is called from the generated
   *  C-style after the application's main method terminates.
   */
  @noinline def loop(): Unit =
    ExecutionContext.loop()

  /** Called by the generated code in case of division by zero. */
  @noinline def throwDivisionByZero(): Nothing =
    throw new java.lang.ArithmeticException("/ by zero")

  /** Called by the generated code in case of incorrect class cast. */
  @noinline def throwClassCast(from: RawPtr, to: RawPtr): Nothing = {
    val fromName = loadObject(
      elemRawPtr(from, castIntToRawSizeUnsigned(MemoryLayout.Rtti.NameOffset))
    )
    val toName = loadObject(
      elemRawPtr(to, castIntToRawSizeUnsigned(MemoryLayout.Rtti.NameOffset))
    )
    throw new java.lang.ClassCastException(
      s"$fromName cannot be cast to $toName"
    )
  }

  /** Called by the generated code in case of operations on null. */
  @noinline def throwNullPointer(): Nothing =
    throw new NullPointerException()

  /** Called by the generated code in case of unexpected condition. */
  @noinline def throwUndefined(): Nothing =
    throw new UndefinedBehaviorError

  /** Called by the generated code in case of out of bounds on array access. */
  @noinline def throwOutOfBounds(i: Int): Nothing =
    throw new ArrayIndexOutOfBoundsException(i.toString)

  /** Called by the generated code in case of missing method on reflective call.
   */
  @noinline def throwNoSuchMethod(sig: String): Nothing =
    throw new NoSuchMethodException(sig)
}
