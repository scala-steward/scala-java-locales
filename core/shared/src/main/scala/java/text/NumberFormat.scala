package java.text

import java.math.RoundingMode
import java.util.{ Currency, Locale }
import locales.cldr.{ LDML, NumberPatterns }
import locales.LocalesDb

abstract class NumberFormat protected () extends Format {
  private[this] var parseIntegerOnly: Boolean  = false
  private[this] var roundingMode: RoundingMode = RoundingMode.HALF_EVEN

  // override def parseObject(source: String, pos: ParsePosition): AnyRef

  override def format(obj: AnyRef, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer =
    obj match {
      case n: Number if n.doubleValue() == n.longValue() =>
        format(n.longValue(), toAppendTo, pos)
      case n: Number                                     =>
        format(n.doubleValue(), toAppendTo, pos)
      case _                                             =>
        throw new IllegalArgumentException("Cannot format given Object as a Number")
    }

  final def format(number: Double): String =
    format(number, new StringBuffer, IgnoreFieldPosition).toString
  final def format(number: Long): String   =
    format(number, new StringBuffer, IgnoreFieldPosition).toString

  def format(number: Double, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer
  def format(number: Long, toAppendTo:   StringBuffer, pos: FieldPosition): StringBuffer

  // def parse(source: String, parsePosition: ParsePosition): Number
  // def parse(source: String): Number

  def isParseIntegerOnly(): Boolean             = this.parseIntegerOnly
  def setParseIntegerOnly(value: Boolean): Unit = this.parseIntegerOnly = value

  def isGroupingUsed(): Boolean
  def setGroupingUsed(newValue: Boolean): Unit

  def getMaximumIntegerDigits(): Int
  def setMaximumIntegerDigits(newValue: Int): Unit

  def getMinimumIntegerDigits(): Int
  def setMinimumIntegerDigits(newValue: Int): Unit

  def getMaximumFractionDigits(): Int
  def setMaximumFractionDigits(newValue: Int): Unit

  def getMinimumFractionDigits(): Int
  def setMinimumFractionDigits(newValue: Int): Unit

  def getCurrency(): Currency
  def setCurrency(currency: Currency): Unit

  def getRoundingMode(): RoundingMode           = roundingMode
  def setRoundingMode(mode: RoundingMode): Unit = this.roundingMode = mode
}

object NumberFormat {
  val INTEGER_FIELD: Int  = 0
  val FRACTION_FIELD: Int = 1

  private def setup(nf: DecimalFormat): NumberFormat = {
    nf.setMaximumIntegerDigits(Integer.MAX_VALUE)
    nf
  }

  private def integerSetup(nf: DecimalFormat): NumberFormat = {
    setup(nf)
    nf.setMaximumFractionDigits(0)
    nf
  }

  private def percentSetup(nf: DecimalFormat): NumberFormat = {
    setup(nf)
    nf.setMaximumFractionDigits(0)
    nf.setMultiplier(100)
    nf
  }

  private def patternsR(ldml: LDML, get: NumberPatterns => Option[String]): Option[String] =
    get(ldml.numberPatterns).orElse(ldml.parent.flatMap(patternsR(_, get)))

  final def getInstance(): NumberFormat = getNumberInstance()

  def getInstance(inLocale: Locale): NumberFormat = getNumberInstance(inLocale)

  final def getNumberInstance(): NumberFormat =
    getInstance(Locale.getDefault(Locale.Category.FORMAT))

  def getNumberInstance(inLocale: Locale): NumberFormat =
    LocalesDb
      .ldml(inLocale)
      .flatMap { ldml =>
        val ptrn = patternsR(ldml, _.decimalFormat)
        ptrn.map(new DecimalFormat(_, DecimalFormatSymbols.getInstance(inLocale))).map(setup)
      }
      .getOrElse(new DecimalFormat("", DecimalFormatSymbols.getInstance(inLocale)))

  final def getIntegerInstance(): NumberFormat =
    getIntegerInstance(Locale.getDefault(Locale.Category.FORMAT))

  def getIntegerInstance(inLocale: Locale): NumberFormat = {
    val f = LocalesDb
      .ldml(inLocale)
      .flatMap { ldml =>
        val ptrn = patternsR(ldml, _.decimalFormat)
        ptrn
          .map(p =>
            new DecimalFormat(
              p.substring(0, p.indexOf(".")),
              DecimalFormatSymbols.getInstance(inLocale)
            )
          )
          .map(integerSetup)
      }
      .getOrElse(new DecimalFormat("", DecimalFormatSymbols.getInstance(inLocale)))

    f.setParseIntegerOnly(true)

    f
  }

  final def getCurrencyInstance(): NumberFormat =
    getCurrencyInstance(Locale.getDefault(Locale.Category.FORMAT))

  final def getCurrencyInstance(inLocale: Locale): NumberFormat =
    LocalesDb
      .ldml(inLocale)
      .flatMap { ldml =>
        val ptrn = patternsR(ldml, _.currencyFormat)
        ptrn.map(new DecimalFormat(_, DecimalFormatSymbols.getInstance(inLocale))).map(setup)
      }
      .getOrElse(new DecimalFormat("", DecimalFormatSymbols.getInstance(inLocale)))

  final def getPercentInstance(): NumberFormat =
    getPercentInstance(Locale.getDefault(Locale.Category.FORMAT))

  def getPercentInstance(inLocale: Locale): NumberFormat =
    LocalesDb
      .ldml(inLocale)
      .flatMap { ldml =>
        val ptrn = patternsR(ldml, _.percentFormat)
        ptrn.map(new DecimalFormat(_, DecimalFormatSymbols.getInstance(inLocale))).map(percentSetup)
      }
      .getOrElse(new DecimalFormat("", DecimalFormatSymbols.getInstance(inLocale)))

  def getAvailableLocales(): Array[Locale] = Locale.getAvailableLocales()
}
