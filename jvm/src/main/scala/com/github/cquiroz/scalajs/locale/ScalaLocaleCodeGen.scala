package com.github.cquiroz.scalajs.locale

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import javax.xml.parsers.SAXParserFactory

import scala.collection.JavaConverters._
import scala.scalajs.locale.ldml.{LDML, LDMLLocale}
import scala.xml._

object CodeGenerator {
  import treehugger.forest._
  import definitions._
  import treehuggerDSL._

  val autoGeneratedCommend = "Auto-generated source file, don't edit"

  def treeHugIt(ldmls: List[LDML]):Tree = {
    BLOCK (
      IMPORT("scala.scalajs.locale.ldml.LDML") withComment autoGeneratedCommend,
      IMPORT("scala.scalajs.locale.ldml.LDMLLocale"),
      PACKAGEOBJECTDEF("locales") := BLOCK(
        ldmls.map(treeHugIt)
      )
    ) inPackage "scala.scalajs.locale.ldml"
  }

  def treeHugIt(ldml: LDML):Tree = {
    val ldmlSym = getModule("LDML")
    val ldmlLocaleSym = getModule("LDMLLocale")

    val ldmlLocaleTree = Apply(ldmlLocaleSym, LIT(ldml.locale.language), ldml.locale.territory.fold(NONE)(t => SOME(LIT(t))), ldml.locale.variant.fold(NONE)(v => SOME(LIT(v))), ldml.locale.script.fold(NONE)(s => SOME(LIT(s))))

    VAL(ldml.scalaSafeName, "LDML") := Apply(ldmlSym, ldmlLocaleTree)
  }

  def isoCodes(codes: List[String], languages: List[String]): Tree = {
    BLOCK (
      PACKAGEOBJECTDEF("isocodes") := BLOCK(
        VAL("isoCountries", "List[String]") := LIST(codes.map(LIT(_))),
        VAL("isoLanguages", "List[String]") := LIST(languages.map(LIT(_)))
      ) withComment autoGeneratedCommend
    ) inPackage "scala.scalajs.locale"

  }

}

object ScalaLocaleCodeGen extends App {

  def writeGeneratedTree(file: String, tree: treehugger.forest.Tree):Unit = {
    val path = Paths.get(s"js/src/main/scala/scala/scalajs/locale/ldml/$file.scala")
    path.getParent.toFile.mkdirs()
    Files.write(path, treehugger.forest.treeToString(tree).getBytes(Charset.forName("UTF8")))
  }

  def constructLDMLDescriptor(f: File, xml: Elem): (File, LDML) = {
    val language = (xml \ "identity" \ "language" \ "@type").text
    val territory = Option((xml \ "identity" \ "territory" \ "@type").text).filter(_.nonEmpty)
    val variant = Option((xml \ "identity" \ "variant" \ "@type").text).filter(_.nonEmpty)
    val script = Option((xml \ "identity" \ "script" \ "@type").text).filter(_.nonEmpty)
    (f, LDML(LDMLLocale(language, territory, variant, script)))
  }

  val parser: SAXParser = {
    // Use a non validating parser for speed
    val f = SAXParserFactory.newInstance()
    f.setNamespaceAware(false)
    f.setValidating(false)
    f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    f.newSAXParser()
  }

  val files = Files.newDirectoryStream(Paths.get("jvm/src/main/resources/common/main")).iterator().asScala.toList

  val clazzes = for {
    f <- files.map(k => k.toFile)
  } yield constructLDMLDescriptor(f, XML.withSAXParser(parser).loadFile(f))

  val tree = CodeGenerator.treeHugIt(clazzes.map(_._2))

  writeGeneratedTree("locales", tree)

  val isoCountryCodes = clazzes.flatMap(_._2.locale.territory).distinct.filter(_.length == 2).sorted
  val isoLanguages = clazzes.map(_._2.locale.language).distinct.filter(_.length == 2).sorted

  writeGeneratedTree("isocodes", CodeGenerator.isoCodes(isoCountryCodes, isoLanguages))

}

