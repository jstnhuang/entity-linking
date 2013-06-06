package edu.washington.cs.knowitall.entity.experiment

import scopt.OptionParser
import scala.collection.JavaConversions._
import scala.io.Source
import java.io.File
import edu.knowitall.browser.entity.EntityLinker
import edu.knowitall.openie.models.ReVerbExtractionGroup
import edu.knowitall.openie.models.ExtractionGroup
import edu.knowitall.openie.models.ReVerbExtraction
import edu.knowitall.browser.entity.util.HeadPhraseFinder
import edu.knowitall.openie.models.ExtractionArgument
import edu.knowitall.openie.models.FreeBaseEntity
import edu.knowitall.browser.entity.EntityLink
import edu.knowitall.openie.models.FreeBaseType
import java.io.BufferedWriter
import java.io.FileWriter
import edu.knowitall.browser.entity.EntityTyper
import edu.knowitall.browser.entity.CrosswikisCandidateFinder
import edu.knowitall.browser.entity.batch_match

/**
 * Class that will run an entity linking experiment, given the path with all the supporting files.
 */
class ReverbEntityLinkingExperiment(linkerSupportPath: File) {
  val crosswikisLinker = new EntityLinker(
    new batch_match(linkerSupportPath),
    new CrosswikisCandidateFinder(linkerSupportPath, 0.01, 10),
    new EntityTyper(linkerSupportPath)
  )
  // Not to be confused with the ReVerbExtractionGroup object.
  type ReverbExtractionGroup = ExtractionGroup[ReVerbExtraction]
  
  /**
   * Convert an entity link object into a possible Freebase entity and set of types.
   */
  def convertLinkToEntity(link: EntityLink): (Option[FreeBaseEntity], Set[FreeBaseType]) = {
    if (link != null) {
      val fbEntity = FreeBaseEntity(link.entity.name, link.entity.fbid, link.score, link.inlinks)
      val fbTypes = link.retrieveTypes.flatMap(FreeBaseType.parse).toSet
      (Some(fbEntity), fbTypes)
    } else {
      (None, Set.empty[FreeBaseType])
    }
  }
  
  /**
   * Finds entity links for the given group. The argument strings are taken from the first instance
   * in the group.
   */
  def linkGroup(reg: ReverbExtractionGroup, useNormalization: Boolean = true):
      ReverbExtractionGroup = {
    val (arg1Text, arg2Text) = if(useNormalization) {
      val arg1Tokens = reg.instances.head.extraction.arg1Tokens
      val arg2Tokens = reg.instances.head.extraction.arg2Tokens
      val candidateFinder = crosswikisLinker.candidateFinder
      (
        HeadPhraseFinder.getHeadPhrase(arg1Tokens, candidateFinder),
        HeadPhraseFinder.getHeadPhrase(arg2Tokens, candidateFinder)
      )
    } else {
      (reg.instances.head.extraction.arg1Text, reg.instances.head.extraction.arg2Text) 
    }
    
    val context = reg.instances.map(_.extraction.sentenceText).toSeq
    val arg1Link = crosswikisLinker.getBestEntity(arg1Text, context)
    val arg2Link = crosswikisLinker.getBestEntity(arg2Text, context)
    val (arg1Entity, arg1Types) = convertLinkToEntity(arg1Link)
    val (arg2Entity, arg2Types) = convertLinkToEntity(arg2Link)
    val updatedArg1 = ExtractionArgument(reg.arg1.norm, arg1Entity, arg1Types)
    val updatedArg2 = ExtractionArgument(reg.arg2.norm, arg2Entity, arg2Types)
    
    new ReverbExtractionGroup(updatedArg1, reg.rel, updatedArg2, reg.instances)
  }
  
  /**
   * Entity link the given lines, which are serialized ReVerb extraction groups.
   */
  def linkGroups(lines: Iterator[String]) = {
    lines.flatMap({ line =>
      val group = ReVerbExtractionGroup.deserializeFromString(line)
      group match {
        case Some(reg) => Some(linkGroup(reg))
        case None => {
          System.err.println("Error parsing group: %s".format(line)); None
        }
      }
    })
  }
  
  def outputGroups(writer: BufferedWriter, groups: Iterator[ReverbExtractionGroup]) = {
    groups.foreach { group =>
      writer.write(ReVerbExtractionGroup.serializeToString(group))
      writer.newLine()
    }
  }
  
  def run(inputPath: File, outputPath: File) = {
    val updatedGroups = linkGroups(Source.fromFile(inputPath).getLines())
    val writer = new BufferedWriter(new FileWriter(outputPath))
    outputGroups(writer, updatedGroups)
  }
  
  def run2(inputPath: File, outputPath: File) = {
    val writer = new BufferedWriter(new FileWriter(outputPath))
    Source.fromFile(inputPath).getLines().foreach({ line =>
      val group = ReVerbExtractionGroup.deserializeFromString(line)
      group match {
        case Some(reg) => {
          val updatedGroup = linkGroup(reg)
          writer.write(ReVerbExtractionGroup.serializeToString(updatedGroup))
          writer.newLine()
        }
        case None => {
          System.err.println("Error parsing group: %s".format(line))
        }
      }
    })
    writer.close()
  }
}

object ReverbEntityLinkingExperiment {
  def main(args: Array[String]): Unit = {
    var inputFile = "."
    var linkerSupportPath = "."
    var outputPath = "."

    val parser = new OptionParser() {
      arg("inputDir", "The file with ReVerb extraction groups to link.", inputFile = _)
      arg(
        "linkerSupportDir",
        "The path to the supporting files for the entity linker.",
        linkerSupportPath = _
      )
      arg("outputDir", "The path of the output file.", outputPath = _)
    }

    if (!parser.parse(args)) {
      return
    }

    val experiment = new ReverbEntityLinkingExperiment(new File(linkerSupportPath))
    experiment.run2(new File(inputFile), new File(outputPath))
  }
}