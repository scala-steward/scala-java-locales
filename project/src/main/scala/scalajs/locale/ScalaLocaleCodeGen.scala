package scalajs.locale

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import javax.xml.parsers.SAXParserFactory

import scala.xml.{XML, _}
import scala.collection.JavaConverters._
import scala.collection.breakOut

/**
  * Value objects build out of CLDR XML data
  */
case class LDMLNumericSystem(id: String, digits: String)

case class XMLLDMLNumberSymbols(system: LDMLNumericSystem,
    decimal: Option[String] = None,
    group: Option[String] = None,
    list: Option[String] = None,
    percent: Option[String] = None,
    plus: Option[String] = None,
    minus: Option[String] = None,
    perMille: Option[String] = None,
    infinity: Option[String] = None,
    nan: Option[String] = None)

case class XMLLDMLLocale(language: String, territory: Option[String],
                         variant: Option[String], script: Option[String])

case class XMLLDML(locale: XMLLDMLLocale, defaultNS: Option[LDMLNumericSystem],
    digitSymbols: Map[LDMLNumericSystem, XMLLDMLNumberSymbols]) {
  val scalaSafeName: String = {
    List(Some(locale.language), locale.script, locale.territory, locale.variant)
      .flatten.mkString("_")
  }
}

object CodeGenerator {
  import treehugger.forest._
  import definitions._
  import treehuggerDSL._

  val autoGeneratedCommend = "Auto-generated code from CLDR definitions, don't edit"

  val ldmlLanguageEx = "[A-Za-z]{2,3}|[A-Za-z]{5,8}".r

  def buildClassTree(packageObject: String, ldmls: List[XMLLDML], only: List[String]): Tree = {
    val langs = ldmls.map(_.scalaSafeName.split("_").toList)
    // Root must always be available
    val root = ldmls.find(_.scalaSafeName == "root").get

    BLOCK (
      IMPORT("scala.scalajs.locale.ldml.LDML") withComment autoGeneratedCommend,
      IMPORT("scala.scalajs.locale.ldml.LDMLLocale"),
      IMPORT("scala.scalajs.locale.ldml.LDMLDigitSymbols"),
      IMPORT("scala.scalajs.locale.ldml.data.numericsystems._"),
      PACKAGEOBJECTDEF(packageObject) := BLOCK(
        if (only.nonEmpty) {
          ldmls.filter(a => only.contains(a.scalaSafeName))
            .map(buildClassTree(root, langs))
        } else {
          ldmls.map(buildClassTree(root, langs))
        }
      )
    ) inPackage "scala.scalajs.locale.ldml.data"
  }

  def findParent(root: XMLLDML, langs: List[List[String]], ldml: XMLLDML): Option[String] = {
    // http://www.unicode.org/reports/tr35/#Locale_Inheritance

    // This searches based on the simple hierarchy resolution based on bundle_name
    // http://www.unicode.org/reports/tr35/#Bundle_vs_Item_Lookup
    ldml.scalaSafeName.split("_").reverse.toList match {
      case x :: Nil if x == root.scalaSafeName => None
      case x :: Nil => Some(root.scalaSafeName)
      case x :: xs if langs.contains(xs.reverse) => Some(xs.reverse.mkString("_"))
    }
  }

  def buildClassTree(root: XMLLDML, langs: List[List[String]])(ldml: XMLLDML): Tree = {
    val ldmlSym = getModule("LDML")
    val ldmlNumericSym = getModule("LDMLDigitSymbols")
    val ldmlLocaleSym = getModule("LDMLLocale")

    val parent = findParent(root, langs, ldml).fold(NONE)(v => SOME(REF(v)))

    val ldmlLocaleTree = Apply(ldmlLocaleSym, LIT(ldml.locale.language),
      ldml.locale.territory.fold(NONE)(t => SOME(LIT(t))),
      ldml.locale.variant.fold(NONE)(v => SOME(LIT(v))),
      ldml.locale.script.fold(NONE)(s => SOME(LIT(s))))

    val defaultNS = ldml.defaultNS.fold(NONE)(s => SOME(REF(s.id)))

    // Locales only use the default numeric system
    val numericSymbols = ldml.defaultNS.flatMap(ldml.digitSymbols.get).map { symb =>
      val decimal = symb.decimal.fold(NONE)(s => SOME(LIT(s)))
      val group = symb.group.fold(NONE)(s => SOME(LIT(s)))
      val list = symb.list.fold(NONE)(s => SOME(LIT(s)))
      val percent = symb.percent.fold(NONE)(s => SOME(LIT(s)))
      val minus = symb.minus.fold(NONE)(s => SOME(LIT(s)))
      val perMille = symb.perMille.fold(NONE)(s => SOME(LIT(s)))
      val infinity = symb.infinity.fold(NONE)(s => SOME(LIT(s)))
      val nan = symb.nan.fold(NONE)(s => SOME(LIT(s)))
      Apply(ldmlNumericSym, decimal, group, list, percent, minus, perMille, infinity, nan)
    }.fold(NONE)(ns => SOME(ns))

    LAZYVAL(ldml.scalaSafeName, "LDML") := Apply(ldmlSym, parent, ldmlLocaleTree, defaultNS, numericSymbols)
  }

  def metadata(codes: List[String], languages: List[String], scripts: List[String]): Tree = {
    BLOCK (
      OBJECTDEF("metadata") := BLOCK(
        LAZYVAL("isoCountries", "Array[String]") :=
          ARRAY(codes.map(LIT(_))) withComment autoGeneratedCommend,
        LAZYVAL("isoLanguages", "Array[String]") :=
          ARRAY(languages.map(LIT(_))) withComment autoGeneratedCommend,
        LAZYVAL("scripts", "Array[String]") :=
          ARRAY(scripts.map(LIT(_))) withComment autoGeneratedCommend
      )
    ) inPackage "scala.scalajs.locale.ldml.data"
  }

  def numericSystems(ns: Seq[LDMLNumericSystem]): Tree = {
    val ldmlNS = getModule("LDMLNumberingSystem")

    BLOCK (
      IMPORT("scala.scalajs.locale.ldml.LDMLNumberingSystem"),
      OBJECTDEF("numericsystems") := BLOCK(
        ns.map(s =>
          LAZYVAL(s.id, "LDMLNumberingSystem") :=
            Apply(ldmlNS, LIT(s.id), LIST(s.digits.toList.map(LIT(_))))
            withComment autoGeneratedCommend
        )
      )
    ) inPackage "scala.scalajs.locale.ldml.data"
  }

}

object ScalaLocaleCodeGen {
  // Minimal set of locales required for java locale
  val defaultLocales = List("en", "fr", "de", "it", "ja", "ko", "zh",
    "zh_Hans_CN", "zh_Hant_TW", "fr_FR", "de_DE", "it_IT", "ja_JP",
    "ko_KR", "en_GB", "en_US", "en_CA", "fr_CA", "root", "zh_Hans", "zh_Hant")

  def writeGeneratedTree(base: File, file: String, tree: treehugger.forest.Tree):File = {
    val dataPath = base.toPath.resolve("scala").resolve("sacalajs")
      .resolve("ldml").resolve("data")
    val path = dataPath.resolve(s"$file.scala")

    path.getParent.toFile.mkdirs()
    println(s"Write to $path")

    Files.write(path, treehugger.forest.treeToString(tree)
      .getBytes(Charset.forName("UTF8")))
    path.toFile
  }

  def constructLDMLDescriptor(f: File, xml: Elem, ns: Map[String, LDMLNumericSystem]): XMLLDML = {
    val language = (xml \ "identity" \ "language" \ "@type").text
    val territory = Option((xml \ "identity" \ "territory" \ "@type").text)
      .filter(_.nonEmpty)
    val variant = Option((xml \ "identity" \ "variant" \ "@type").text)
      .filter(_.nonEmpty)
    val script = Option((xml \ "identity" \ "script" \ "@type").text)
      .filter(_.nonEmpty)
    // Find out the default numeric system
    val defaultNS = Option((xml \ "numbers" \ "defaultNumberingSystem").text)
      .filter(_.nonEmpty).filter(ns.contains)

    def symbolN(n: NodeSeq): Option[String] = if (n.isEmpty) None else Some(n.text)

    val symbols = (xml \ "numbers" \\ "symbols").flatMap { s =>
      // http://www.unicode.org/reports/tr35/tr35-numbers.html#Numbering_Systems
      // By default, number symbols without a specific numberSystem attribute
      // are assumed to be used for the "latn" numbering system, which i
      // western (ASCII) digits
      val nsAttr = Option((s \ "@numberSystem").text).filter(_.nonEmpty)
      val sns = nsAttr.flatMap(ns.get).getOrElse(ns.get("latn").get)
      // TODO process aliases
      val symbols = s.collect {
        case s @ <symbols>{_*}</symbols> if (s \ "alias").isEmpty =>
          // elements may not be present and they could be the empty string
          val decimal = symbolN(s \ "decimal")
          val group = symbolN(s \ "group")
          val list = symbolN(s \ "list")
          val percentSymbol = symbolN(s \ "percentSign")
          val plusSign = symbolN(s \ "plusSign")
          val minusSign = symbolN(s \ "minusSign")
          val perMilleSign = symbolN(s \ "perMille")
          val infiniteSign = symbolN(s \ "infinity")
          val nan = symbolN(s \ "nan")
          val sym = XMLLDMLNumberSymbols(sns, decimal, group, list,
            percentSymbol, plusSign, minusSign, perMilleSign, infiniteSign, nan)
          sns -> sym
      }
      symbols.headOption
    }
    XMLLDML(XMLLDMLLocale(language, territory, variant, script),
      defaultNS.flatMap(ns.get), symbols.toMap)
  }

  def parseNumberingSystems(xml: Elem): Seq[LDMLNumericSystem] = {
    val ns = xml \ "numberingSystems" \\ "numberingSystem"

    for {
      n <- ns
      if (n \ "@type").text == "numeric"
    } yield {
      val id = (n \ "@id").text
      val digits = (n \ "@digits").text
      LDMLNumericSystem(id, digits)
    }
  }

  val parser: SAXParser = {
    // Use a non validating parser for speed
    val f = SAXParserFactory.newInstance()
    f.setNamespaceAware(false)
    f.setValidating(false)
    f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    f.newSAXParser()
  }

  def generateLocaleData(base: File, data: File): Seq[File] = {
    val nanos = System.nanoTime()
    val files = Files.newDirectoryStream(data.toPath.resolve("common")
      .resolve("main")).iterator().asScala.toList

    val numberingSystemsFile = data.toPath.resolve("common")
      .resolve("supplemental").resolve("numberingSystems.xml").toFile
    val numericSystems = parseNumberingSystems(XML.withSAXParser(parser)
      .loadFile(numberingSystemsFile))
    val numericSystemsMap: Map[String, LDMLNumericSystem] =
      numericSystems.map(n => n.id -> n)(breakOut)

    val clazzes = for {
      f <- files.map(k => k.toFile)
    } yield constructLDMLDescriptor(f, XML.withSAXParser(parser).loadFile(f), numericSystemsMap)

    val tree = CodeGenerator.buildClassTree("all", clazzes, Nil)
    val stdTree = CodeGenerator.buildClassTree("minimal", clazzes, defaultLocales)

    val isoCountryCodes = clazzes.flatMap(_.locale.territory).distinct
      .filter(_.length == 2).sorted
    val isoLanguages = clazzes.map(_.locale.language).distinct
      .filter(_.length == 2).sorted
    val scripts = clazzes.flatMap(_.locale.script).distinct.sorted

    val f1 = writeGeneratedTree(base, "all", tree)
    val f2 = writeGeneratedTree(base, "minimal", stdTree)
    val f3 = writeGeneratedTree(base, "metadata",
      CodeGenerator.metadata(isoCountryCodes, isoLanguages, scripts))
    val f4 = writeGeneratedTree(base, "numericsystems",
      CodeGenerator.numericSystems(numericSystems))
    println("Generation took " + (System.nanoTime() - nanos) / 1000000 + " [ms]")
    Seq(f1, f2, f3, f4)
  }
}