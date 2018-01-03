/*
 * Copyright 2016 The BigDL Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intel.analytics.bigdl.models.utils

import java.nio.file.{Files, Paths}

import com.intel.analytics.bigdl.dataset.{DataSet, LocalDataSet}
import com.intel.analytics.bigdl.dataset.image.{BGRImgToLocalSeqFile, LocalImgReaderWithName, LocalLabeledImagePath}
import scopt.OptionParser

object ImgSeqFileGenerator {

  case class ImgSeqFileGeneratorParams(
    folder: String = ".",
    output: String = ".",
    dataSet: String = "",
    categoryFile: String = "",
    parallel: Int = 1,
    blockSize: Int = 12800,
    train: Boolean = true,
    validate: Boolean = true,
    scaleSize: Int = 256,
    isResize: Boolean = false,
    hasName: Boolean = false)

  private val parser = new OptionParser[ImgSeqFileGeneratorParams]("Spark-DL Places365 " +
    "Sequence File Generator") {
    head("Spark-DL Places365 Sequence File Generator")
    opt[String]('f', "folder")
      .text("where you put the Places365 data")
      .action((x, c) => c.copy(folder = x))
    opt[String]('o', "output folder")
      .text("where you put the generated seq files")
      .action((x, c) => c.copy(output = x))
    opt[String]('d', "dataset")
      .text("which dataset to generate seq file for")
      .action((x, c) => c.copy(dataSet = x))
    opt[String]('c', "categoryfile")
      .text("category file list")
      .action((x, c) => c.copy(categoryFile = x))
    opt[Int]('p', "parallel")
      .text("parallel num")
      .action((x, c) => c.copy(parallel = x))
    opt[Int]('b', "blockSize")
      .text("block size")
      .action((x, c) => c.copy(blockSize = x))
    opt[Unit]('t', "trainOnly")
      .text("only generate train data")
      .action((_, c) => c.copy(validate = false))
    opt[Unit]('v', "validationOnly")
      .text("only generate validation data")
      .action((_, c) => c.copy(train = false))
    opt[Int]('s', "scaleSize")
      .text("scale size, default is uniform scale without -r option")
      .action((x, c) => c.copy(scaleSize = x))
    opt[Unit]('r', "resize")
      .text("resize to (scaleSize, scaleSize) instead of uniform scale")
      .action((x, c) => c.copy(isResize = true))
    opt[Unit]('h', "hasName")
      .text("add name to seq file")
      .action((x, c) => c.copy(hasName = true))
  }

  def main(args: Array[String]): Unit = {
    parser.parse(args, ImgSeqFileGeneratorParams()).foreach { param =>
      if (param.train) {
        // Process train data
        println("Process train data...")
        val trainFolderPath = Paths.get(param.folder, "train")
        val categoryFilePath = Paths.get(param.categoryFile)
        require(Files.isDirectory(trainFolderPath),
          s"${trainFolderPath} is not valid")
        var trainDataSet : LocalDataSet[LocalLabeledImagePath] = null
        if (param.dataSet.equalsIgnoreCase("imagenet")) {
          trainDataSet = DataSet.ImageFolder.paths(trainFolderPath)
        } else if (param.dataSet.equalsIgnoreCase("imagenet")) {
          trainDataSet = DataSet.ImageFolder.paths(trainFolderPath, categoryFilePath)
        } else {
          throw new IllegalArgumentException(s"${param.dataSet} can not be recognized")
        }
        trainDataSet.shuffle()
        val iter = trainDataSet.data(train = false)
        (0 until param.parallel).map(tid => {
          val workingThread = new Thread(new Runnable {
            override def run(): Unit = {
              val imageIter = if (param.isResize) {
                LocalImgReaderWithName(param.scaleSize, param.scaleSize, 255f)(iter)
              } else {
                LocalImgReaderWithName(param.scaleSize)(iter)
              }
              val fileIter = BGRImgToLocalSeqFile(param.blockSize, Paths.get(param.output, "train",
                s"imagenet-seq-$tid"), param.hasName)(imageIter)
              while (fileIter.hasNext) {
                println(s"Generated file ${fileIter.next()}")
              }
            }
          })
          workingThread.setDaemon(false)
          workingThread.start()
          workingThread
        }).foreach(_.join())
      }

      if (param.validate) {
        // Process validation data
        println("Process validation data...")
        val validationFolderPath = Paths.get(param.folder, "val")
        require(Files.isDirectory(validationFolderPath),
          s"${validationFolderPath} is not valid")

        val validationDataSet = DataSet.ImageFolder.paths(validationFolderPath)

        validationDataSet.shuffle()
        val iter = validationDataSet.data(train = false)
        (0 until param.parallel).map(tid => {
          val workingThread = new Thread(new Runnable {
            override def run(): Unit = {
              val imageIter = if (param.isResize) {
                LocalImgReaderWithName(param.scaleSize, param.scaleSize, 255f)(iter)
              } else {
                LocalImgReaderWithName(param.scaleSize)(iter)
              }
              val fileIter = BGRImgToLocalSeqFile(param.blockSize, Paths.get(param.output, "val",
                s"imagenet-seq-$tid"), param.hasName)(imageIter)
              while (fileIter.hasNext) {
                println(s"Generated file ${fileIter.next()}")
              }
            }
          })
          workingThread.setDaemon(false)
          workingThread.start()
          workingThread
        }).foreach(_.join())
      }
    }

    println("Done")
  }

}
