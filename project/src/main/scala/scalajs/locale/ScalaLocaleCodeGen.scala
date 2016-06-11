package scalajs.locale

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import javax.xml.parsers.SAXParserFactory

import scala.xml.{XML, _}
import scala.collection.JavaConverters._

/**
  * Value objects build out of CLDR XML data
  */
case class XMLLDMLLocale(language: String, territory: Option[String],
                         variant: Option[String], script: Option[String])

case class XMLLDML(locale: XMLLDMLLocale) {
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
      PACKAGEOBJECTDEF(packageObject) := BLOCK(
        if (only.nonEmpty) {
          ldmls.filter(a => only.contains(a.scalaSafeName)).map(buildClassTree(root, langs))
        } else {
          ldmls.map(buildClassTree(root, langs))
        }
      )
    ) inPackage "scala.scalajs.locale.ldml.data"
  }

  def findParent(root: XMLLDML, langs: List[List[String]], ldml: XMLLDML): Option[String] = {
    // http://www.unicode.org/reports/tr35/#Locale_Inheritance

    // This searches based on the simple hirerachy resolution based on bundle_name
    // http://www.unicode.org/reports/tr35/#Bundle_vs_Item_Lookup
    ldml.scalaSafeName.split("_").reverse.toList match {
      case x :: Nil if x == root.scalaSafeName => None
      case x :: Nil => Some(root.scalaSafeName)
      case x :: xs if langs.contains(xs.reverse) => Some(xs.reverse.mkString("_"))
    }
  }


  def buildClassTree(root: XMLLDML, langs: List[List[String]])(ldml: XMLLDML): Tree = {
    val ldmlSym = getModule("LDML")
    val ldmlLocaleSym = getModule("LDMLLocale")

    val parent = findParent(root, langs, ldml).fold(NONE)(v => SOME(REF(v)))

    val ldmlLocaleTree = Apply(ldmlLocaleSym, LIT(ldml.locale.language),
      ldml.locale.territory.fold(NONE)(t => SOME(LIT(t))),
      ldml.locale.variant.fold(NONE)(v => SOME(LIT(v))),
      ldml.locale.script.fold(NONE)(s => SOME(LIT(s))))

    LAZYVAL(ldml.scalaSafeName, "LDML") := Apply(ldmlSym, parent, ldmlLocaleTree)
  }

  def metadata(codes: List[String], languages: List[String], scripts: List[String]): Tree = {
    BLOCK (
      OBJECTDEF("metadata") := BLOCK(
        LAZYVAL("isoCountries", "Array[String]") := ARRAY(codes.map(LIT(_))) withComment autoGeneratedCommend,
        LAZYVAL("isoLanguages", "Array[String]") := ARRAY(languages.map(LIT(_))) withComment autoGeneratedCommend,
        LAZYVAL("scripts", "Array[String]") := ARRAY(scripts.map(LIT(_))) withComment autoGeneratedCommend
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
    val dataPath = base.toPath.resolve("scala").resolve("sacalajs").resolve("ldml").resolve("data")
    val path = dataPath.resolve(s"$file.scala")

    path.getParent.toFile.mkdirs()
    println(s"Write to $path")

    Files.write(path, treehugger.forest.treeToString(tree).getBytes(Charset.forName("UTF8")))
    path.toFile
  }

  def constructLDMLDescriptor(f: File, xml: Elem): XMLLDML = {
    val language = (xml \ "identity" \ "language" \ "@type").text
    val territory = Option((xml \ "identity" \ "territory" \ "@type").text).filter(_.nonEmpty)
    val variant = Option((xml \ "identity" \ "variant" \ "@type").text).filter(_.nonEmpty)
    val script = Option((xml \ "identity" \ "script" \ "@type").text).filter(_.nonEmpty)
    XMLLDML(XMLLDMLLocale(language, territory, variant, script))
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
    println(data.toPath.resolve("common").resolve("main"))
    val nanos = System.nanoTime()
    val files = Files.newDirectoryStream(data.toPath.resolve("common").resolve("main")).iterator().asScala.toList

    val clazzes = for {
      f <- files.map(k => k.toFile)
    } yield constructLDMLDescriptor(f, XML.withSAXParser(parser).loadFile(f))

    val tree = CodeGenerator.buildClassTree("all", clazzes, Nil)
    val stdTree = CodeGenerator.buildClassTree("minimal", clazzes, defaultLocales)

    val isoCountryCodes = clazzes.flatMap(_.locale.territory).distinct.filter(_.length == 2).sorted
    val isoLanguages = clazzes.map(_.locale.language).distinct.filter(_.length == 2).sorted
    val scripts = clazzes.flatMap(_.locale.script).distinct.sorted

    val f1 = writeGeneratedTree(base, "all", tree)
    val f2 = writeGeneratedTree(base, "minimal", stdTree)
    val f3 = writeGeneratedTree(base, "metadata", CodeGenerator.metadata(isoCountryCodes, isoLanguages, scripts))
    println("Generation took " + (System.nanoTime() - nanos) / 1000000 + " [ms]")
    Seq(f1, f2, f3)
  }
}
